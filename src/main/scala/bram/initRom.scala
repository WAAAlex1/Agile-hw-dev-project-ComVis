package bram

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline

import comvis._

/** InitRom - ROM containing test images with transfer controller
  *
  * Stores multiple test images in a single memory and transfers selected image to image BRAM line-by-line when start
  * signal is asserted.
  *
  * @param IPN
  *   Images per number
  * @param symbolN
  *   Number of symbols (digits 0-9)
  * @param imgWidth
  *   Image width (and height, since square)
  * @param initFile
  *   Single hex file with all images concatenated
  */
class InitRom(
  val IPN: Int,
  val symbolN: Int,
  val imgWidth: Int,
  val initFile: Option[String] = None
) extends Module {

  val totalImages   = IPN * symbolN
  val totalLines    = totalImages * imgWidth // e.g., 100 * 32 = 3200 lines total
  val addrWidth     = log2Ceil(totalLines)
  val lineAddrWidth = log2Ceil(imgWidth)

  val io = IO(new Bundle {
    // Control inputs
    val start       = Input(Bool())
    val digitSelect = Input(UInt(4.W)) // Select digit 0-9
    val imgSelect   = Input(UInt(4.W)) // Select which template (0-9) for that digit

    // Interface to image BRAM (write only)
    val writeOut = Flipped(new MemWrite(addrWidth, imgWidth)) //

    // Status outputs
    val startOut = Output(Bool())
    val busy     = Output(Bool())
  })

  // ==================== ROM MEMORY ====================

  // Use Mem for asynchronous read
  val romMem = Mem(totalLines, UInt(imgWidth.W))

  // Initialize from single file using Chisel's built-in function
  initFile match {
    case Some(file) =>
      loadMemoryFromFileInline(romMem, file)
      println(s"[InitRom] Initialized ${totalImages} images from $file")
      println(s"[InitRom] Total lines in file: $totalLines (${totalImages} x ${imgWidth})")
    case None =>
      println(s"[InitRom] WARNING: No init file - ROM will be uninitialized")
  }

  // ==================== STATE MACHINE ====================

  object State extends ChiselEnum {
    val idle, transferring, done = Value
  }
  import State._

  val stateReg      = RegInit(idle)
  val lineCounter   = RegInit(0.U(lineAddrWidth.W))
  val selectedDigit = RegInit(0.U(4.W))
  val selectedImg   = RegInit(0.U(4.W))

  // Calculate the absolute image index: digit x IPN + imgIdx
  val absoluteImgIdx = selectedDigit * IPN.U + selectedImg

  // Calculate ROM address: baseAddr = absoluteImgIdx x imgWidth, then add line offset
  val baseAddr = absoluteImgIdx * imgWidth.U
  val romAddr  = baseAddr + lineCounter

  // Asynchronous read from ROM
  val romData = romMem.read(romAddr)

  // Default outputs
  io.writeOut.wrEn   := false.B
  io.writeOut.wrAddr := 0.U
  io.writeOut.wrData := 0.U
  io.startOut        := false.B
  io.busy            := (stateReg =/= idle)

  switch(stateReg) {
    is(idle) {
      when(io.start) {
        selectedDigit := io.digitSelect
        selectedImg   := io.imgSelect
        lineCounter   := 0.U
        stateReg      := transferring
      }
    }

    is(transferring) {
      // Write current line to BRAM
      io.writeOut.wrEn   := true.B
      io.writeOut.wrAddr := lineCounter
      io.writeOut.wrData := romData

      // Check if transfer complete
      when(lineCounter === (imgWidth - 1).U) {
        stateReg := done
      }.otherwise {
        lineCounter := lineCounter + 1.U
      }
    }

    is(done) {
      // Pulse to signal rest of circuit
      io.startOut := true.B
      stateReg    := idle
    }
  }

}
