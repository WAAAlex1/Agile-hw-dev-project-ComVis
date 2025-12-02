package bram

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline

import comvis._

/** InitRom - ROM containing test images with transfer controller
  *
  * Uses SyncReadMem for BRAM inference on FPGA. Handles 1-cycle read latency with pipelined state machine.
  *
  * @param IPN
  *   Images per number
  * @param TPN
  *   Templates per number
  * @param symbolN
  *   Number of symbols (digits 0-9)
  * @param imgWidth
  *   Image width (and height, since square)
  * @param initFile
  *   Single mem (hex formatted) file with all images concatenated
  */
class InitRam(
  val IPN: Int,
  val TPN: Int,
  val symbolN: Int,
  val imgWidth: Int,
  val initFile: Option[String] = None,
  val debug: Boolean = false
) extends Module {

  val totalImages  = IPN * symbolN
  val totalLines   = totalImages * imgWidth // e.g., 100 * 32 = 3200 lines total
  val totalBrams   = TPN * symbolN + 1 // 100 template BRAMs + 1 image BRAM = 101 total
  val imageBramIdx = TPN * symbolN // Image BRAM is at index 100 (after all templates)

  val lineAddrWidth    = log2Ceil(imgWidth)
  val bramSelWidth     = log2Ceil(totalBrams)
  val totalWrAddrWidth = bramSelWidth + lineAddrWidth // Full encoded address width

  val io = IO(new Bundle {
    // Control inputs
    val start       = Input(Bool())
    val digitSelect = Input(UInt(4.W)) // Select digit 0-9
    val imgSelect   = Input(UInt(4.W)) // Select which template (0-9) for that digit

    // Interface to MultiTemplateBram (write only)
    val writeOut = Flipped(new MemWrite(totalWrAddrWidth, imgWidth, totalBrams))

    // Status outputs
    val startOut = Output(Bool())
    val busy     = Output(Bool())
  })

  if (debug) println(s"[InitRom] Image BRAM index: ${imageBramIdx}")
  if (debug) println(s"[InitRom] BRAM select bits: ${bramSelWidth}")
  if (debug) println(s"[InitRom] Line address bits: ${lineAddrWidth}")
  if (debug) println(s"[InitRom] Total write address bits: ${totalWrAddrWidth}")

  // ==================== BRAM MEMORY (Synchronous Read) ====================

  // Use SyncReadMem to infer BRAM
  val romMem = SyncReadMem(totalLines, UInt(imgWidth.W))

  // Initialize from single file using Chisel's built-in function
  initFile match {
    case Some(file) =>
      loadMemoryFromFileInline(romMem, file)
      if (debug) println(s"[InitRom] Initialized ${totalImages} images from $file")
      if (debug) println(s"[InitRom] Total lines in file: $totalLines (${totalImages} x ${imgWidth})")
    case None =>
      if (debug) println(s"[InitRom] WARNING: No init file - ROM will be uninitialized")
  }

  // ==================== STATE MACHINE ====================

  object State extends ChiselEnum {
    val idle, stageRead, transferring, lastTransfer, done = Value
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

  // Synchronous read from ROM (data available NEXT cycle)
  val romData = romMem.read(romAddr, stateReg === stageRead || stateReg === transferring)

  // Pipeline register for write address (to align with read data)
  val writeAddrReg = RegInit(0.U(totalWrAddrWidth.W))

  // Encode write address: {image_BRAM_index, line_address}
  val encodedWriteAddr = Cat(imageBramIdx.U(bramSelWidth.W), lineCounter)

  // Default outputs
  io.writeOut.wrEn   := false.B
  io.writeOut.wrAddr := 0.U
  io.writeOut.wrData := 0.U
  io.startOut        := false.B
  io.busy            := (stateReg === stageRead) || (stateReg === transferring) || (stateReg === lastTransfer)

  switch(stateReg) {
    is(idle) {
      when(io.start) {
        selectedDigit := io.digitSelect
        selectedImg   := io.imgSelect
        lineCounter   := 0.U
        stateReg      := stageRead
      }
    }

    is(stageRead) {
      // Issue first read, data will be available next cycle
      writeAddrReg := encodedWriteAddr
      lineCounter  := lineCounter + 1.U
      stateReg     := transferring
    }

    is(transferring) {
      // Write data from previous read cycle
      io.writeOut.wrEn   := true.B
      io.writeOut.wrAddr := writeAddrReg
      io.writeOut.wrData := romData

      // Prepare next address
      writeAddrReg := encodedWriteAddr

      // Check if transfer complete
      when(lineCounter === (imgWidth - 1).U) {
        // Last write will happen this cycle
        stateReg := lastTransfer
      }.otherwise {
        lineCounter := lineCounter + 1.U
        // Continue reading next line
      }
    }
    is(lastTransfer) {
      io.writeOut.wrEn   := true.B
      io.writeOut.wrAddr := writeAddrReg
      io.writeOut.wrData := romData
      stateReg           := done
    }

    is(done) {
      // Pulse to signal rest of circuit
      io.startOut := true.B
      stateReg    := idle
    }
  }
}
