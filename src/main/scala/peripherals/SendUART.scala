package peripherals

/*
 * New SendUART will take any binary file and send it with correct addresses.
 *
 * By @GeorgBD
 */

import com.fazecast.jSerialComm.SerialPort

import java.nio.ByteBuffer
import java.nio.file.{ Files, Paths }
/*
import java.util.HexFormat
import scala.math.BigInt
import scala.util.Try
 */

object SendUART {
  def main(args: Array[String]): Unit = {
    // Make sure we got a file:
    if (args.length != 1) {
      println("Usage: ReadFileBytes <file-path>")
      System.exit(1)
    }

    // Identify available serial ports
    val ports = SerialPort.getCommPorts
    if (ports.isEmpty) System.out.println("No COM ports found ;(")

    var foundPortName = "COM4" // Standard COM port found on Georg's PC
    for (port <- ports) {
      foundPortName = port.getSystemPortName // If other port found dynamically allocate
      System.out.println("Found Port: " + foundPortName)
    }

    val serialPort = SerialPort.getCommPort(foundPortName)
    serialPort.setBaudRate(115200) // Set baud rate (match with receiver)

    serialPort.setNumDataBits(8)
    serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT)
    serialPort.setParity(SerialPort.NO_PARITY)

    if (!serialPort.openPort) {
      System.out.println("Failed to open port.")
      return
    } else {
      System.out.println("Port opened successfully.")
    }

    // Get the bytes of the file to send:
    val imgPath  = Paths.get(args(0))
    val imgBytes = Files.readAllBytes(imgPath)

    // Send the image
    sendImage(imgBytes, serialPort, 0x0)

    // Set the bootloader to sleep and set LEDs to high
    bootloaderSleep(serialPort)

    System.out.println("Data sent.")
    serialPort.closePort // Make sure to close the SerialPort
    System.out.println("Port closed.")
  }

  def sendImage(bytes: Array[Byte], serialPort: SerialPort, startAddr: Int): Unit = {
    var address = startAddr

    for (i <- 0 until Math.ceil(bytes.length / 4.0).toInt) { // Reading 4 bytes at a time
      val start       = i * 4
      val end         = Math.min(start + 4, bytes.length)
      val chunk       = bytes.slice(start, end)
      val paddedChunk = if (chunk.length < 4) chunk ++ Array.fill[Byte](4 - chunk.length)(0.toByte) else chunk

      val addressBytesPadded = ByteBuffer.allocate(4).putInt(address).array()

      // Write address (4 bytes)
      serialPort.writeBytes(addressBytesPadded, 4)
      println(addressBytesPadded.map(b => f"0x$b%02X").mkString("Address: (", " ", ")"))

      // Write data (4 bytes)
      serialPort.writeBytes(paddedChunk, 4)
      println(paddedChunk.map(b => f"0x$b%02X").mkString("Data: (", " ", ")"))

      address += 1
    }
  }

  def bootloaderSleep(serialPort: SerialPort): Unit = {
    System.out.println("Putting Bootloader to sleep and unstalling pipeline")
    // This byte array should turn on the LED on the FPGA board and sleep the bootloader
    val blSleepProtocol = Array[Byte](
      0xf0.toByte,
      0x01.toByte,
      0x00.toByte,
      0x00.toByte,
      0x00.toByte,
      0x00.toByte,
      0x00.toByte,
      0xff.toByte,
      0xf1.toByte,
      0x00.toByte,
      0x00.toByte,
      0x00.toByte,
      0x00.toByte,
      0x00.toByte,
      0x00.toByte,
      0x01.toByte
    )
    serialPort.writeBytes(blSleepProtocol, 8)
    println(blSleepProtocol.map(b => f"0x$b%02X").mkString("BootSleep: (", " ", ")"))
  }

}
