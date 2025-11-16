package comvis

import chisel3._
import chisel3.util._

class Masker(val imgWidth: Int) extends Module {
  val io = IO(new Bundle {
    val maskIn     = Input(UInt(imgWidth.W))
    val imgIn      = Input(UInt(imgWidth.W))
    val start      = Input(Bool())
    val sliceCnt   = Input(UInt(log2Up(imgWidth + 1).W))
    val confidence = Output(UInt(((log2Up(imgWidth) * 2) + 1).W))
    val ready      = Output(Bool())
  })

  object State extends ChiselEnum {
    val idle, run = Value
  }
  import State._

  val maskSlice = Module(new MaskSlice(imgWidth))


  val confidenceReg = RegInit(0.U(imgWidth.W))
  val stateReg      = RegInit(idle)

  // Defaults:
  io.ready               := false.B
  io.confidence          := 0.U
  maskSlice.io.maskSlice := io.maskIn
  maskSlice.io.imgSlice  := io.imgIn

  switch(stateReg) {
    is(idle) {
      when(io.start) {
        stateReg := run
      }
        .elsewhen(true.B) // How to .otherwise??
        {
          stateReg := idle
        }
    }
    is(run) {
      when(io.sliceCnt === imgWidth.U) {
        io.ready      := true.B
        io.confidence := confidenceReg
        stateReg      := idle
      }
        .elsewhen(true.B) {
          // Increments every cycle
          confidenceReg := confidenceReg + maskSlice.io.confidence
        }

      stateReg := run
    }
  }

}
