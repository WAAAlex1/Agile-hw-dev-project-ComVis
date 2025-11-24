import chisel3._
import chisel3.util._
import comvis.evalIn

class Eval(val imgWidth: Int, val TPN: Int, val symbolN: Int) extends Module {
  val scoreWidth = log2Up((imgWidth * imgWidth) * TPN)

  val io = IO(new Bundle {
    val in        = new evalIn(imgWidth, TPN, symbolN)
    val bestIdx   = Output(UInt(log2Ceil(symbolN).W))
    val bestScore = Output(UInt(scoreWidth.W))
  })

  val idxWidth = log2Ceil(symbolN)

  // Combinational max
  val initScore = io.in.confScore(0)
  val initIdx   = 0.U(idxWidth.W)

  val (bestScoreComb, bestIdxComb) =
    io.in.confScore.zipWithIndex.drop(1).foldLeft((initScore, initIdx)) {
      case ((maxScore, maxIdx), (currScore, currIdx)) =>
        val currIdxU    = currIdx.U(idxWidth.W)
        val takeCurr    = currScore > maxScore
        val newMaxScore = Mux(takeCurr, currScore, maxScore)
        val newMaxIdx   = Mux(takeCurr, currIdxU, maxIdx)
        (newMaxScore, newMaxIdx)
    }

  // Registers for output
  val bestScoreReg = RegInit(0.U(scoreWidth.W))
  val bestIdxReg   = RegInit(0.U(idxWidth.W))

  when(io.in.valid) {
    bestScoreReg := bestScoreComb
    bestIdxReg   := bestIdxComb
  }

  io.bestScore := bestScoreReg
  io.bestIdx   := bestIdxReg
}
