package comvis

import chisel3._
import chisel3.util._

class MaskSlice(val imgWidth: Int) extends Module {
  val io = IO(new Bundle{
    val maskSlice = Input(UInt(imgWidth.W))
    val imgSlice = Input(UInt(imgWidth.W))
    val confidence = Output(UInt((log2Up(imgWidth)).W))
  })

  val xnorRes = ~(io.maskSlice ^ io.imgSlice)

  //ReduceTree version which currently doesn't work
  //val resVec = VecInit((xnorRes).asBools.map(_.asUInt))
  //io.confidence := resVec.reduceTree((x,y) => x + y)

  //Is this tree or chain in hardware??
  io.confidence := PopCount(xnorRes)
}