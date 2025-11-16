package comvis

import chisel3._
import chisel3.util._

class MaskerTop(val imgWidth: Int, val TPN: Int, val symbolN: Int) {
  val io = IO(new Bundle {
    val extIn = new MaskIn()
    val din = Flipped(new MemOut(imgWidth,TPN,symbolN))
    val out = Flipped(new conAccIn(imgWidth, TPN, symbolN))
  })

  val masks = Vec(symbolN, Vec(TPN, Module(new Masker(imgWidth))))
  //Use For loop to connect masks to all interfaces
  for(numLine <- masks){
    for(tempLine <- numLine){
      //Not right for loops I think
    }
  }

  object State extends ChiselEnum {
    val idle, run = Value
  }
  import State._

  val sliceCnt = RegInit(0.U(log2Up(imgWidth + 1).W))
  val stateReg = RegInit(idle)

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

        sliceCnt      := 0.U
        stateReg      := idle
      }
        .elsewhen(true.B) {
          // Increments every cycle
          io.out.valid    := true.B
          sliceCnt      := sliceCnt + 1.U

        }

      stateReg := run
    }
  }

}
