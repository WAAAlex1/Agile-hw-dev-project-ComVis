import chisel3._
import chisel3.util._

class Eval(val N: Int, val WIDTH: Int = 10) extends Module {
  require(N >= 1, "N must be >= 1")
  val idxWidth = if (N <= 1) 1 else log2Ceil(N)

  val io = IO(new Bundle {
    val c_scores   = Input(Vec(N, UInt(WIDTH.W)))
    val best_idx   = Output(UInt(idxWidth.W))
    val best_score = Output(UInt(WIDTH.W))
  })

  val initScore = io.c_scores(0)
  val initIdx   = 0.U(idxWidth.W)

  val (bestScore, bestIdx) = io.c_scores.zipWithIndex.drop(1).foldLeft((initScore, initIdx)) {
    case ((maxScore, maxIdx), (currScore, currIdx)) =>
      val currIdxU = currIdx.U(idxWidth.W)
      val takeCurr = currScore > maxScore   
      val newMaxScore = Mux(takeCurr, currScore, maxScore)
      val newMaxIdx   = Mux(takeCurr, currIdxU, maxIdx)
      (newMaxScore, newMaxIdx)
  }

  io.best_score := bestScore
  io.best_idx   := bestIdx
}
