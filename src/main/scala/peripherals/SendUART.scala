package peripherals

/*
 * SendUART for sending multiple template hex files to FPGA.
 *
 * - Reads template files from ./templates
 * - Files named: template_{TPNidx}_{symbolNidx}.hex
 * - Each file contains imgWidth lines of hex (each line = one row)
 * - Sends for each row:
 *     1) 32-bit address (4 bytes at once)
 *     2) 32-bit data    (4 bytes at once)
 *
 * Address format MUST match chiseltest:
 *   addr = (templateAddr << kWidth) | k
 *   where:
 *     templateAddr = symbolNidx * TPN + TPNidx
 *     k             = row index within template
 *     kWidth        = ceil(log2(imgWidth))
 *
 * After templates are sent:
 *   - sendImage(...) is called
 *   - then bootloaderSleep(...)
 */

import com.fazecast.jSerialComm.SerialPort

import java.nio.ByteBuffer
import java.nio.file.{ Files, Paths }
import scala.util.matching.Regex

object SendUART {

  // -----------------------------
  // Entry point
  // -----------------------------
  def main(args: Array[String]): Unit = {
    if (args.length != 4) {
      println("Usage: SendUART <symbolN> <TPN> <imgWidth> <selectedDigit>")
      System.exit(1)
    }

    val symbolN       = args(0).toInt
    val TPN           = args(1).toInt
    val imgWidth      = args(2).toInt
    val selectedDigit = args(3).toInt

    val kWidth     = log2Ceil(imgWidth) // = log2Up(imgWidth-1)
    val indexWidth = log2Ceil(symbolN * TPN) // = log2Up(symbolN*TPN-1)

    println(s"symbolN=$symbolN  TPN=$TPN  imgWidth=$imgWidth")
    println(s"kWidth=$kWidth  indexWidth=$indexWidth")

    // -----------------------------
    // Find serial port
    // -----------------------------
    val ports = SerialPort.getCommPorts
    if (ports.isEmpty) {
      println("No COM ports found")
      System.exit(1)
    }

    var foundPortName = "COM4"
    for (port <- ports) {
      foundPortName = port.getSystemPortName
      println("Found Port: " + foundPortName)
    }

    val serialPort = SerialPort.getCommPort(foundPortName)
    serialPort.setBaudRate(115200)
    serialPort.setNumDataBits(8)
    serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT)
    serialPort.setParity(SerialPort.NO_PARITY)

    if (!serialPort.openPort) {
      println("Failed to open port.")
      return
    } else {
      println("Port opened successfully.")
    }

    // -----------------------------
    // Send all templates (separate function)
    // -----------------------------
    loadAndSendTemplates(serialPort, symbolN, TPN, imgWidth, kWidth)

    // -----------------------------
    // Optionally send an image AFTER templates
    // (kept as requested; currently sends nothing)
    // -----------------------------
    sendImage(serialPort, symbolN, TPN, imgWidth, kWidth, indexWidth, selectedDigit)

    // -----------------------------
    // Finalize
    // -----------------------------
    bootloaderSleep(serialPort)

    println("All templates sent.")
    serialPort.closePort()
    println("Port closed.")
  }

  // -----------------------------
  // TEMPLATE LOADING + SENDING
  // -----------------------------
  def loadAndSendTemplates(
    serialPort: SerialPort,
    symbolN: Int,
    TPN: Int,
    imgWidth: Int,
    kWidth: Int
  ): Unit = {

    val templateDir = Paths.get("templates/templates_1")
    if (!Files.exists(templateDir) || !Files.isDirectory(templateDir)) {
      println("templates folder not found")
      System.exit(1)
    }

    // regex extracts TPNidx, symbolNidx from filenames like template_3_7.hex
    val templateRegex: Regex = "template_(\\d+)_(\\d+)\\.mem".r

    // gather files + parsed indices
    val filesWithIdx: Seq[(java.nio.file.Path, Int, Int)] = Files
      .list(templateDir)
      .toArray
      .map(_.asInstanceOf[java.nio.file.Path])
      .flatMap { p =>
        p.getFileName.toString match {
          case templateRegex(tpnIdxStr, symbolIdxStr) =>
            Some((p, tpnIdxStr.toInt, symbolIdxStr.toInt))
          case _ => None
        }
      }
      .toSeq

    if (filesWithIdx.isEmpty) {
      println("No template files found.")
      return
    }

    // Sort deterministically: by symbolIdx then tpnIdx (change if you prefer other ordering)
    val sorted = filesWithIdx.sortBy { case (_, tpnIdx, symbolIdx) => (symbolIdx, tpnIdx) }

    println(s"Found ${sorted.length} template files")

    // compute indexWidth once
    val indexWidth = log2Ceil(symbolN * TPN)

    // templateAddr is now a simple sequential counter
    var templateAddr = 0

    for ((path, tpnIdx, symbolIdx) <- sorted) {

      println(
        s"Sending template: ${path.getFileName} -> assigned templateAddr=$templateAddr (tpnIdx=$tpnIdx, symbolIdx=$symbolIdx)"
      )

      val lines = Files
        .readAllLines(path)
        .toArray
        .map(_.toString.trim)
        .filter(_.nonEmpty)

      if (lines.length != imgWidth) {
        println(s"WARNING: ${path.getFileName} has ${lines.length} rows, expected $imgWidth")
      }

      for (k <- lines.indices) {
        val dataWord = BigInt(lines(k), 16) & 0xffffffffL

        // pack addr: [templateAddr << kWidth] | k
        val addr: BigInt = {
          val kMasked   = BigInt(k) & ((BigInt(1) << kWidth) - 1)
          val idxMasked = BigInt(templateAddr) & ((BigInt(1) << indexWidth) - 1)
          (idxMasked << kWidth) | kMasked
        }

        // --- Send 32-bit address (4 bytes at once) (reverse for little endian)
        val addrBytes = ByteBuffer.allocate(4).putInt(addr.toInt).array()
        serialPort.writeBytes(addrBytes.reverse, 4)

        // --- Send 32-bit data (4 bytes at once)
        val dataBytes = ByteBuffer.allocate(4).putInt(dataWord.toInt).array()
        serialPort.writeBytes(dataBytes, 4)

        println(f"ADDR=0x${addr}%08X  DATA=0x${dataWord}%08X")
      }

      // increment sequential templateAddr
      templateAddr += 1
    }
  }

  // -----------------------------
  // Image sender (unchanged, kept as requested)
  // -----------------------------
  def sendImage(
    serialPort: SerialPort,
    symbolN: Int,
    TPN: Int,
    imgWidth: Int,
    kWidth: Int,
    indexWidth: Int,
    selectedDigit: Int
  ): Unit = {

    val imgPath = Paths.get(s"templates/templates_0/template_${selectedDigit}_${selectedDigit}.mem")
    if (!Files.exists(imgPath)) {
      println("inputImage.mem not found, skipping image send")
      return
    }

    val startAddr = symbolN * TPN

    println(s"Sending input image starting at address $startAddr")

    val lines = Files
      .readAllLines(imgPath)
      .toArray
      .map(_.toString.trim)
      .filter(_.nonEmpty)

    if (lines.length != imgWidth) {
      println(s"WARNING: inputImage.mem has ${lines.length} rows, expected $imgWidth")
    }

    for (k <- lines.indices) {
      val dataWord = BigInt(lines(k), 16) & 0xffffffffL

      val addr: BigInt = {
        val kMasked   = BigInt(k) & ((BigInt(1) << kWidth) - 1)
        val idxMasked = BigInt(startAddr) & ((BigInt(1) << indexWidth) - 1)
        (idxMasked << kWidth) | kMasked
      }

      // --- Send 32-bit address (4 bytes at once) (reverse for little endian)
      val addrBytes = ByteBuffer.allocate(4).putInt(addr.toInt).array()
      serialPort.writeBytes(addrBytes.reverse, 4)

      // --- Send 32-bit data (4 bytes at once)
      val dataBytes = ByteBuffer.allocate(4).putInt(dataWord.toInt).array()
      serialPort.writeBytes(dataBytes, 4)

      println(f"IMG ADDR=0x${addr}%08X  DATA=0x${dataWord}%08X")
    }
  }

  // -----------------------------
  // Utilities
  // -----------------------------

  // Exact Chisel-compatible log2Ceil
  def log2Ceil(x: Int): Int = {
    require(x > 0)
    if (x == 1) 0 else 32 - Integer.numberOfLeadingZeros(x - 1)
  }

  def bootloaderSleep(serialPort: SerialPort): Unit = {
    println("Putting Bootloader to sleep and setting LED high")

    val blSleepProtocol = Array[Byte](
      0x00.toByte,
      0x00.toByte,
      0x01.toByte,
      0xf0.toByte,
      0xff.toByte,
      0x00.toByte,
      0x00.toByte,
      0x00.toByte,
      0x00.toByte,
      0x00.toByte,
      0x00.toByte,
      0xf1.toByte,
      0x01.toByte,
      0x00.toByte,
      0x00.toByte,
      0x00.toByte
    )

    serialPort.writeBytes(blSleepProtocol, 16)
    println(blSleepProtocol.map(b => f"0x$b%02X").mkString("BootSleep: (", " ", ")"))
  }
}
