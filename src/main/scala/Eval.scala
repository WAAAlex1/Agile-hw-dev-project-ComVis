import chisel3._
import chisel3.util._

class Eval(val N: Int, val WIDTH: Int = 10) extends Module {
  require(N >= 1, "N must be >= 1")
  val idxWidth = if (N <= 1) 1 else log2Ceil(N)

  val io = IO(new Bundle {
    val confScoresVec = Input(Vec(N, UInt(WIDTH.W)))
    val valid         = Input(Bool())
    val bestIdx       = Output(UInt(idxWidth.W))
    val bestScore     = Output(UInt(WIDTH.W))
  })

  val initScore = io.confScoresVec(0)
  val initIdx   = 0.U(idxWidth.W)

  val (bestScoreComb, bestIdxComb) =
    io.confScoresVec.zipWithIndex.drop(1).foldLeft((initScore, initIdx)) {
      case ((maxScore, maxIdx), (currScore, currIdx)) =>
        val currIdxU    = currIdx.U(idxWidth.W)
        val takeCurr    = currScore > maxScore
        val newMaxScore = Mux(takeCurr, currScore, maxScore)
        val newMaxIdx   = Mux(takeCurr, currIdxU, maxIdx)
        (newMaxScore, newMaxIdx)
    }

  val bestScoreReg = RegInit(0.U(WIDTH.W))
  val bestIdxReg   = RegInit(0.U(idxWidth.W))

  when(io.valid) {
    bestScoreReg := bestScoreComb
    bestIdxReg   := bestIdxComb
  }
    .otherwise {
      io.bestScore := 0.U
      io.bestIdx   := 0.U
    }

  io.bestScore := bestScoreReg
  io.bestIdx   := bestIdxReg
}
