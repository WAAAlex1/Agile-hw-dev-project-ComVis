package UnitTests.peripherals

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import peripherals._

class SevenSegDriverTester extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "SevenSegDecoder"

  it should "correctly extract digits and calculate percentage (HEX mode)" in {
    test(new SevenSegDecoder(maxScore = 10240)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      // Test case 1: 50% confidence (0x32 in hex)
      // maxScore = 32 × 32 × 10 = 10240
      // confidence = 5120 (50%)
      // 50 decimal = 0x32 hex
      dut.io.digitA.poke(7.U)
      dut.io.digitB.poke(7.U)
      dut.io.confidence.poke(5120.U)
      dut.clock.step(1)

      // Check layout: [7]=A, [5]=B, [1:0]=percentage in hex
      dut.io.digits(7).expect(7.U)  // Input digit
      dut.io.digits(5).expect(7.U)  // Predicted digit
      dut.io.digits(2).expect(0xF.U)  // Blank (unused in hex mode)
      dut.io.digits(1).expect(0x3.U)  // Upper nibble: 50 = 0x32 -> 3
      dut.io.digits(0).expect(0x2.U)  // Lower nibble: 50 = 0x32 -> 2
      dut.io.digits(6).expect(0xF.U)  // Blank
      dut.io.digits(4).expect(0xF.U)  // Blank
      dut.io.digits(3).expect(0xF.U)  // Blank
      println("Test 1 (HEX): 50% = 0x32 CORRECT")

      // Test case 2: 100% confidence (0x64 in hex)
      dut.io.confidence.poke(10240.U)
      dut.clock.step(1)
      dut.io.digits(1).expect(0x6.U)  // Upper nibble: 100 = 0x64 -> 6
      dut.io.digits(0).expect(0x4.U)  // Lower nibble: 100 = 0x64 -> 4
      println("Test 2 (HEX): 100% = 0x64 CORRECT")

      // Test case 3: 0% confidence (0x00 in hex)
      dut.io.confidence.poke(0.U)
      dut.clock.step(1)
      dut.io.digits(1).expect(0x0.U)  // Upper nibble: 0 = 0x00 -> 0
      dut.io.digits(0).expect(0x0.U)  // Lower nibble: 0 = 0x00 -> 0
      println("Test 3 (HEX): 0% = 0x00 CORRECT")

      // Test case 4: 75% confidence (0x4B in hex)
      dut.io.digitA.poke(3.U)
      dut.io.digitB.poke(8.U)
      dut.io.confidence.poke(7680.U)  // 75% of 10240
      dut.clock.step(1)
      dut.io.digits(7).expect(3.U)  // Input = 3
      dut.io.digits(5).expect(8.U)  // Predicted = 8
      dut.io.digits(1).expect(0x4.U)  // Upper nibble: 75 = 0x4B -> 4
      dut.io.digits(0).expect(0xB.U)  // Lower nibble: 75 = 0x4B -> B (11)
      println("Test 4 (HEX): 75% = 0x4B with different digits CORRECT")
    }
  }

  behavior of "SevenSegTDM"

  it should "cycle through digits with correct active-low logic" in {
    test(new SevenSegTDM(refreshDiv = 30)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      // Set up test digits
      val testDigits = Seq(0, 1, 2, 3, 4, 5, 6, 7)
      for (i <- 0 until 8) {
        dut.io.digits(i).poke(testDigits(i).U)
      }

      // Expected 7-segment patterns (active-low)
      val expectedPatterns = Seq(
        0xC0,  // 0
        0xF9,  // 1
        0xFF,  // 2
        0xFF,  // 3
        0xFF,  // 4
        0x92,  // 5
        0xFF,  // 6
        0xF8   // 7
      )

      // Test TDM cycling
      for (cycle <- 0 until 3) {  // Test 3 complete cycles
        for (digit <- 0 until 8) {
          // Wait for refresh tick
          for (_ <- 0 until 30) {
            dut.clock.step(1)
          }

          // Check anode: only current digit should be LOW (active)
          val expectedAnode = ~(1 << digit) & 0xFF
          println(f"Expected=0x${expectedAnode}%02X, Actual=0x${dut.io.anodes.peek().litValue.toInt}%02X")
          dut.io.anodes.expect(expectedAnode.U)

          // Check cathode pattern matches current digit
          dut.io.cathodes.expect(expectedPatterns(digit).U)

          if (cycle == 0) {
            println(f"Digit $digit: Anode=0x${expectedAnode}%02X, Cathode=0x${expectedPatterns(digit)}%02X")
          }
        }
      }
      println("TDM cycling verified over 3 complete cycles")
    }
  }

  behavior of "SevenSegDriver"

  it should "integrate decoder and TDM correctly (HEX mode)" in {
    test(new SevenSegDriver(maxScore = 10240, refreshDiv = 10))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        // Test: Input=5, Predicted=8, Confidence=75% (0x4B in hex)
        dut.io.digitA.poke(5.U)
        dut.io.digitB.poke(8.U)
        dut.io.confidence.poke(7680.U)  // 75% of 10240

        // Let it run for several complete cycles
        for (_ <- 0 until 160) {
          dut.clock.step(1)
        }

        // Check that outputs are changing (TDM is working)
        val anode1 = dut.io.anodes.peek().litValue
        dut.clock.step(20)
        val anode2 = dut.io.anodes.peek().litValue

        assert(anode1 != anode2, "Anodes should be cycling")
        println("Integrated driver verified (HEX mode)")
      }
  }
}