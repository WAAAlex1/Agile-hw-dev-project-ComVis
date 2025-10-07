package comvis

import chisel3._
import chisel3.util._

class Masker(val imgWidth: Int) extends Module {
  val io = IO(new Bundle {
    val maskIn = Input(UInt(imgWidth.W))
    val imgIn = Input(UInt(imgWidth.W))
    val confidence = Output(UInt((log2Up(imgWidth^2)).W))
    val ready = Output(Bool())
  })

  //Counter for ready. Increments every clockcycle.
  val sliceCnt = RegInit(0.U((log2Up(imgWidth)).W))
  sliceCnt := sliceCnt + 1.U

  val confidenceReg = RegInit(0.U((log2Up(imgWidth^2)).W))

  val maskSlice = new MaskSlice(imgWidth)
  maskSlice.io.maskSlice := io.maskIn
  maskSlice.io.imgSlice := io.imgIn

  //Increments every cycle
  confidenceReg := confidenceReg + maskSlice.io.confidence

  //Needed default values?
  io.ready := false.B
  io.confidence := 0.U

  //How to make sure this exponent is done in scala on generation??
  when(sliceCnt === (imgWidth^2).U){
    io.ready := true.B
    io.confidence := confidenceReg
  }
}
