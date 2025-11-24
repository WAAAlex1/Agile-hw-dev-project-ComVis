package comvis

import chisel3._
import chisel3.util._

class MaskerTop(val imgWidth: Int, val TPN: Int, val symbolN: Int) extends Module {
  val io = IO(new Bundle {
    val extIn = new MaskIn()
    val din   = Flipped(new MemOut(imgWidth, TPN, symbolN))
    val out   = Flipped(new conAccIn(imgWidth, TPN, symbolN))
  })

  // 2-D Scala sequence of MaskSlices
  val masks = Seq.fill(symbolN, TPN) {
    Module(new MaskSlice(imgWidth))
  }

  // Connect the modules
  for (templLine <- 0 until symbolN)
    for (mask <- 0 until TPN) {

      // Inputs
      masks(templLine)(mask).io.imgSlice  := io.din.imgData
      masks(templLine)(mask).io.maskSlice := io.din.templateData(templLine)(mask)

      // Output connections
      io.out.sliceConf(templLine)(mask) := masks(templLine)(mask).io.confidence
    }

  val sliceCnt = RegInit(0.U(log2Up(imgWidth + 1).W))

  object State extends ChiselEnum {
    val idle, run = Value
  }
  import State._
  val stateReg = RegInit(idle)

  // Default outputs:
  io.out.done  := false.B
  io.out.valid := false.B

  switch(stateReg) {
    is(idle) {
      when(io.extIn.start) {
        stateReg := run
      }
        .elsewhen(true.B) // How to .otherwise??
        {
          stateReg := idle
        }
    }
    is(run) {
      when(sliceCnt === imgWidth.U) {
        io.out.done := true.B
        sliceCnt    := 0.U
        stateReg    := idle
      }
        .elsewhen(true.B) {
          // Increments every cycle
          io.out.valid := true.B
          sliceCnt     := sliceCnt + 1.U
          stateReg     := run
        }
    }
  }

}
