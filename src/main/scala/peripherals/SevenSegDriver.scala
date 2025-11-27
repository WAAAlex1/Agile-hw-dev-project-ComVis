package peripherals

import chisel3._
import chisel3.util._

/** SevenSegDriver - Complete 7-segment display driver
  *
  * Top-level module combining decoder and TDM controller. Designed for Nexys A7 board with 8-digit common-anode
  * display.
  *
  * @param maxScore
  *   Theoretical maximum score (computed at elaboration)
  * @param refreshDiv
  *   Clock divider for TDM refresh rate
  */
class SevenSegDriver(val maxScore: Int, val refreshDiv: Int = 100000) extends Module {

  val confWidth = log2Ceil(maxScore + 1)

  val io = IO(new Bundle {
    // Inputs
    val digitA     = Input(UInt(4.W)) // Input digit (0-9)
    val digitB     = Input(UInt(4.W)) // Predicted digit (0-9)
    val confidence = Input(UInt(confWidth.W)) // Raw confidence score

    // Outputs to FPGA pins
    val anodes   = Output(UInt(8.W)) // AN7..AN0 (active-low)
    val cathodes = Output(UInt(8.W)) // DP, CG, CF, CE, CD, CC, CB, CA (active-low)
  })

  // Instantiate combinatorial decoder
  val decoder = Module(new SevenSegDecoder(maxScore))
  decoder.io.digitA     := io.digitA
  decoder.io.digitB     := io.digitB
  decoder.io.confidence := io.confidence

  // Instantiate TDM controller
  val tdm = Module(new SevenSegTDM(refreshDiv))
  tdm.io.digits := decoder.io.digits

  // Connect outputs
  io.anodes   := tdm.io.anodes
  io.cathodes := tdm.io.cathodes

  // ==================== FORMAL VERIFICATION ====================
  // Runtime assertions for top-level inputs
  assert(io.digitA <= 9.U, "digitA must be 0-9")
  assert(io.digitB <= 9.U, "digitB must be 0-9")
  assert(io.confidence <= maxScore.U, s"confidence must be <= maxScore ($maxScore)")

}

/** Helper object for creating drivers with standard configurations */
object SevenSegDriver {

  /** Create driver for standard 32x32 MNIST with 10 templates per digit maxScore = 32 × 32 × 10 = 10,240 Requires
    * 14-bit confidence input
    */
  def mnist32x32: SevenSegDriver =
    new SevenSegDriver(maxScore = 32 * 32 * 10, refreshDiv = 100000)
}
