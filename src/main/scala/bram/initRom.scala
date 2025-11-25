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

  val totalImages    = IPN * symbolN
  val totalLines     = totalImages * imgWidth // e.g., 100 * 32 = 3200 lines total
  val addrWidth      = log2Ceil(totalLines)
  val imgSelectWidth = log2Ceil(totalImages)
  val lineAddrWidth  = log2Ceil(imgWidth)

  val io = IO(new Bundle {
    // Control inputs
    val start     = Input(Bool())
    val imgSelect = Input(UInt(imgSelectWidth.W))

    // Interface to image BRAM (write only)
    val writeOut = new MemWrite(addrWidth, imgWidth)

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

  val stateReg    = RegInit(idle)
  val lineCounter = RegInit(0.U(lineAddrWidth.W))
  val selectedImg = RegInit(0.U(imgSelectWidth.W))

  // Calculate ROM address: baseAddr = imageIdx × imgWidth, then add line offset
  val baseAddr = selectedImg * imgWidth.U
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
        selectedImg := io.imgSelect
        lineCounter := 0.U
        stateReg    := transferring
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
