package comvis

import chisel3._
import chisel3.util._

class MaskSlice(val imgWidth: Int) extends Module {
  val io = IO(new Bundle {
    val maskSlice  = Input(UInt(imgWidth.W))
    val imgSlice   = Input(UInt(imgWidth.W))
    val confidence = Output(UInt(log2Up(imgWidth + 1).W))
  })

  // ReduceTree version which currently doesn't work
  // val resVec = VecInit((xnorRes).asBools.map(_.asUInt))
  // io.confidence := resVec.reduceTree((x,y) => x + y)

  // This is a tree in hardware yippie
  io.confidence := PopCount(~(io.maskSlice ^ io.imgSlice))
}
