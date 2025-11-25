package peripherals

import chisel3._
import chisel3.util._

/** SevenSegDecoder - Combinatorial digit extraction and percentage calculation
 *
 * Takes raw confidence score and converts to displayable BCD digits.
 * Layout: [7]=digitA, [5]=digitB, [2][1][0]=percentage
 *
 * @param maxScore Theoretical maximum score (imgWidth x templates) - computed at elaboration
 */
class SevenSegDecoder(val maxScore: Int) extends Module {
  require(maxScore > 0, "Max score must be positive")

  val confWidth = log2Ceil(maxScore + 1)

  val io = IO(new Bundle {
    // Inputs
    val digitA    = Input(UInt(4.W))          // Input digit (0-9)
    val digitB    = Input(UInt(4.W))          // Predicted digit (0-9)
    val confidence = Input(UInt(confWidth.W)) // Raw confidence score
    // Outputs
    val digits = Output(Vec(8, UInt(4.W)))    // Hex digits for all 8 positions
  })

  // ==================== PERCENTAGE CALCULATION ====================
  // percentage = (confidence x 100) / maxScore
  // Note: This uses division

  val confidence100 = io.confidence * 100.U
  val percentage = Wire(UInt(7.W))  // 0-100, so 7 bits sufficient

  // Division by constant
  percentage := confidence100 / maxScore.U

  // Assert output validity
  assert(percentage <= 100.U, "percentage must be <= 100")

  // Clamp to 100 (extra security measure)
  val percentageClamped = Mux(percentage > 100.U, 100.U, percentage)

  // ==================== DIGIT EXTRACTION ====================
  val tensHex = percentageClamped(6, 4)  // Upper nibble
  val onesHex = percentageClamped(3, 0)  // Lower nibble

  // ==================== POSITION MAPPING ====================
  // Display layout: [7]=A, [6]=blank, [5]=B, [4]=blank, [3]=blank, [2]=blank, [1]=tens, [0]=ones

  io.digits(7) := io.digitA
  io.digits(6) := 0xF.U  // Blank
  io.digits(5) := io.digitB
  io.digits(4) := 0xF.U  // Blank
  io.digits(3) := 0xF.U  // Blank
  io.digits(2) := 0xF.U  // Blank
  io.digits(1) := tensHex
  io.digits(0) := onesHex
}