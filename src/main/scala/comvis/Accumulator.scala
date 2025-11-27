package comvis

import chisel3._
import chisel3.util._

class Accumulator(val imgWidth: Int, val TPN: Int, val symbolN: Int) extends Module {
  val io = IO(new Bundle() {
    val din = new conAccIn(imgWidth, TPN, symbolN)
    val out = Flipped(new evalIn(imgWidth, TPN, symbolN))
  })

  // symbolN amount of registers each "log2Up(imgWidth * imgWidth * TPN)" in width
  val accumWidth   = log2Up(imgWidth * imgWidth * TPN)
  val accumulation = RegInit(VecInit(Seq.fill(symbolN)(0.U(accumWidth.W))))

  when(io.din.valid) {
    for (i <- 0 until symbolN) {
      val tempSum = io.din.sliceConf(i).reduceTree(_ +& _) // "+&" keeps the width correct

      accumulation(i) := accumulation(i) + tempSum
    }
  }.otherwise { // Reset output value after value has been passed on
    accumulation := VecInit(Seq.fill(symbolN)(0.U(accumWidth.W)))
  }

  // Passing output signals
  io.out.confScore := accumulation
  io.out.valid     := io.din.done
}
