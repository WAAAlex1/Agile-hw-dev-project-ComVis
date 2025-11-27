package peripherals

import chisel3._
import chisel3.util._

/** SevenSegTDM - Time Division Multiplexing controller for 7-segment displays
  *
  * Implements TDM for Nexys A7 board with active-low anode and cathode signals. Refresh rate: Clock frequency /
  * refreshDiv / 8 digits
  *
  * @param refreshDiv
  *   Clock divider for TDM refresh rate
  */
class SevenSegTDM(val refreshDiv: Int = 100000) extends Module {
  require(refreshDiv > 0, "Refresh divider must be positive")

  val io = IO(new Bundle {
    // Input: digits from decoder
    val digits = Input(Vec(8, UInt(4.W)))

    // Outputs to FPGA pins (active-low)
    val anodes   = Output(UInt(8.W)) // AN7..AN0 (active-low)
    val cathodes = Output(UInt(8.W)) // DP, CG, CF, CE, CD, CC, CB, CA (active-low)
  })

  // ==================== TDM COUNTER ====================
  // Divider counter for refresh rate control
  val divCounter   = RegInit(0.U(log2Ceil(refreshDiv + 1).W))
  val digitCounter = RegInit(0.U(3.W)) // 0-7 for 8 digits

  // Refresh rate control?
  val tick = divCounter === refreshDiv.U
  divCounter := Mux(tick, 0.U, divCounter + 1.U)

  // Advance to next digit on tick
  when(tick) {
    digitCounter := Mux(digitCounter === 7.U, 0.U, digitCounter + 1.U)
  }

  // ==================== ANODE CONTROL (Active-Low) ====================
  // Only ONE anode should be LOW at a time - All others HIGH (disabled)
  val anodeVec = Wire(Vec(8, Bool()))
  for (i <- 0 until 8)
    anodeVec(i) := (digitCounter =/= i.U) // HIGH (off) unless selected
  io.anodes     := anodeVec.asUInt

  // ==================== 7-SEGMENT DECODING (Active-Low) ====================
  // Get current digit to display
  val currentDigit = io.digits(digitCounter)

  // 7-segment patterns for 0-9, A-F (hex), and blank
  // Format: DP, CG, CF, CE, CD, CC, CB, CA
  // Active-LOW: 0 = segment ON, 1 = segment OFF

  val segmentPatterns = VecInit(
    Seq(
      // Inverted patterns (0 = ON, 1 = OFF)
      "b11000000".U(8.W), // 0
      "b11111001".U(8.W), // 1
      "b10100100".U(8.W), // 2
      "b10110000".U(8.W), // 3
      "b10011001".U(8.W), // 4
      "b10010010".U(8.W), // 5
      "b10000010".U(8.W), // 6
      "b11111000".U(8.W), // 7
      "b10000000".U(8.W), // 8
      "b10010000".U(8.W), // 9
      "b10001000".U(8.W), // A (10)
      "b10000011".U(8.W), // B (11)
      "b11000110".U(8.W), // C (12)
      "b10100001".U(8.W), // D (13)
      "b10000110".U(8.W), // E (14)
      "b10001110".U(8.W) // F (15)
    )
  )

  val currentPattern = segmentPatterns(currentDigit)

  // Some digits should always just be blank (2, 3, 4, 6)
  switch(digitCounter) {
    is(2.U)(currentPattern := "b11111111".U(8.W))
    is(3.U)(currentPattern := "b11111111".U(8.W))
    is(4.U)(currentPattern := "b11111111".U(8.W))
    is(6.U)(currentPattern := "b11111111".U(8.W))
  }

  io.cathodes := currentPattern
}
