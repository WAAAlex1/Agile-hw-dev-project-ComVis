package comvis

import chisel3._
import chisel3.util._

class MaskerTop(val imgWidth: Int, val TPN: Int, val symbolN: Int) extends Module {
  val io = IO(new Bundle {
    val extIn  = new MaskIn()
    val memOut = Flipped(new MemIn(log2Up(imgWidth)))
    val din    = Flipped(new MemOut(imgWidth, TPN, symbolN))
    val out    = Flipped(new conAccIn(imgWidth, TPN, symbolN))
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
    val idle, stageMem, run, done = Value
  }
  import State._
  val stateReg = RegInit(idle)

  // Default outputs:
  io.out.done         := false.B
  io.out.valid        := 0.U
  io.memOut.rdEn      := 0.U
  io.memOut.rdAddrIdx := sliceCnt

  switch(stateReg) {
    is(idle) {
      when(io.extIn.start) {
        stateReg := stageMem
      }
        .otherwise {
          stateReg := idle
        }
    }
    is(stageMem) {
      io.memOut.rdEn := 1.U
      stateReg       := run
    }
    is(run) {
      when(sliceCnt === imgWidth.U) {
        sliceCnt     := 0.U
        io.out.valid := true.B
        stateReg     := done
      }
        .otherwise {
          // Increments every cycle
          io.out.valid   := true.B
          io.memOut.rdEn := 1.U
          sliceCnt       := sliceCnt + 1.U
          stateReg       := run
        }
    }
    is(done) {
      io.out.done := true.B
      stateReg    := idle
    }
  }

}
