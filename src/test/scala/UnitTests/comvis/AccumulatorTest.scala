package UnitTests.comvis

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chisel3.util._
import comvis.Accumulator
import scala.util.Random

class AccumulatorTest extends AnyFlatSpec with ChiselScalatestTester {

  val imgWidth = 32
  val TPN = 10
  val symbolN = 10

  behavior of "Accumulator basic functionality"

  it should "accumulate values correctly during valid phase" in {
    test(new Accumulator(imgWidth, TPN, symbolN))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        println("\n" + "="*80)
        println("TEST: Basic Accumulation")
        println("="*80)

        val rand = new Random(42)  // Fixed seed for reproducibility
        val expectedSums = Array.fill(symbolN)(0L)

        // Accumulate over multiple cycles
        val numCycles = imgWidth

        println(s"\nAccumulating over $numCycles cycles...")
        for (cycle <- 0 until numCycles) {
          for (i <- 0 until symbolN) {
            for (j <- 0 until TPN) {
              val reqWidth = log2Up(imgWidth)
              val randVal: Long = rand.nextLong() & ((1L << reqWidth) - 1)
              dut.io.din.sliceConf(i)(j).poke(randVal.U(reqWidth.W))
              expectedSums(i) += randVal
            }
          }
          dut.io.din.valid.poke(true.B)
          dut.io.din.done.poke(false.B)
          dut.clock.step()

          if (cycle % 8 == 0 || cycle < 3) {
            println(f"  Cycle $cycle%2d: valid=true, accumulating...")
          }
        }

        println(s"\nAccumulation phase complete")
        println("Expected sums:")
        expectedSums.zipWithIndex.foreach { case (sum, idx) =>
          println(f"  Symbol[$idx]: $sum")
        }

        // Check intermediate state (still accumulating, not done)
        println("\nChecking outputs during accumulation (should be building up):")
        for (i <- 0 until symbolN) {
          val actualSum = dut.io.out.confScore(i).peek().litValue
          val expected = expectedSums(i)
          println(f"  Symbol[$i]: actual=$actualSum, expected=$expected")
        }

        dut.clock.step()
      }
  }

  behavior of "Accumulator done signal handling"

  it should "output accumulated values when done signal is asserted" in {
    test(new Accumulator(imgWidth, TPN, symbolN))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        println("\n" + "="*80)
        println("TEST: Done Signal Output")
        println("="*80)

        val rand = new Random(42)
        val expectedSums = Array.fill(symbolN)(0L)

        // Accumulate
        println("\nAccumulation phase:")
        for (cycle <- 0 until imgWidth) {
          for (i <- 0 until symbolN) {
            for (j <- 0 until TPN) {
              val reqWidth = log2Up(imgWidth)
              val randVal: Long = rand.nextLong() & ((1L << reqWidth) - 1)
              dut.io.din.sliceConf(i)(j).poke(randVal.U(reqWidth.W))
              expectedSums(i) += randVal
            }
          }
          dut.io.din.valid.poke(true.B)
          if(cycle === imgWidth-1) {
            println("\nAsserting done signal...")
            dut.io.din.done.poke(true.B)
          }
          else
            dut.io.din.done.poke(false.B)
          dut.clock.step()
        }

        println("  Accumulation complete")
        // Values should be available when done is asserted
        println("\nChecking outputs when done=true:")
        dut.io.out.valid.expect(true.B, "Output valid should be true cycle after done")

        var allCorrect = true
        for (i <- 0 until symbolN) {
          val actualSum = dut.io.out.confScore(i).peek().litValue
          val expected = expectedSums(i)
          val status = if (actualSum == expected) "SUCCESS" else "FAILURE"

          println(f"  Symbol[$i]: actual=$actualSum, expected=$expected $status")

          if (actualSum != expected) {
            allCorrect = false
            if (actualSum == 0) {
              println(f"    ERROR: Got 0 - accumulator may have reset prematurely!")
            }
          }
        }

        if (allCorrect) {
          println("\n SUCCESS: All outputs correct when done=true")
        } else {
          println("\n FAILURE: Outputs incorrect when done=true")
          println("  This suggests accumulator resets before outputs are read!")
        }

        assert(allCorrect, "Accumulator outputs should match expected sums when done=true")
      }
  }

  behavior of "Accumulator reset behavior"

  it should "reset after done signal is deasserted" in {
    test(new Accumulator(imgWidth, TPN, symbolN))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        println("\n" + "="*80)
        println("TEST: Reset After Done")
        println("="*80)

        // First accumulation
        println("\nFirst accumulation cycle:")
        for (cycle <- 0 until 5) {
          for (i <- 0 until symbolN) {
            for (j <- 0 until TPN) {
              dut.io.din.sliceConf(i)(j).poke(10.U)  // Simple constant value
            }
          }
          dut.io.din.valid.poke(true.B)
          dut.io.din.done.poke(false.B)
          dut.clock.step()
        }

        // Assert done
        println("  Asserting done...")
        dut.io.din.done.poke(true.B)
        dut.io.din.valid.poke(true.B)
        dut.clock.step()

        dut.io.out.valid.expect(true.B, "Output valid should be true cycle after done")
        val firstResult = dut.io.out.confScore(0).peek().litValue
        println(f"  First result: $firstResult")

        // Deassert done and wait
        println("\n  Deasserting done...")
        dut.io.din.done.poke(false.B)
        dut.io.din.valid.poke(false.B)
        dut.clock.step(1)

        val afterReset = dut.io.out.confScore(0).peek().litValue
        println(f"  After reset: $afterReset")

        if (afterReset == 0) {
          println("  SUCCESS: Accumulator reset to 0")
        } else {
          println(f"  FAILURE: Accumulator did not reset (still $afterReset)")
        }

        // Second accumulation - should start from 0
        println("\nSecond accumulation cycle:")
        for (cycle <- 0 until 5) {
          for (i <- 0 until symbolN) {
            for (j <- 0 until TPN) {
              dut.io.din.sliceConf(i)(j).poke(10.U)
            }
          }
          dut.io.din.valid.poke(true.B)
          dut.io.din.done.poke(false.B)
          dut.clock.step()
        }

        dut.io.din.done.poke(true.B)
        dut.io.din.valid.poke(true.B)
        dut.clock.step()

        val secondResult = dut.io.out.confScore(0).peek().litValue
        println(f"  Second result: $secondResult")

        dut.io.din.done.poke(false.B)
        dut.io.din.valid.poke(false.B)
        dut.clock.step()

        if (firstResult == secondResult) {
          println("\n     SUCCESS:  Both cycles produced same result - reset working correctly")
        } else {
          println(f"\n    FAILURE:  Results differ: first=$firstResult, second=$secondResult")
          println("  Accumulator may not be resetting properly between runs")
        }

        assert(firstResult == secondResult, "Results should be identical after proper reset")
      }
  }

  behavior of "Accumulator timing and handshake"

  it should "maintain output valid for at least one cycle after done" in {
    test(new Accumulator(imgWidth, TPN, symbolN))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        println("\n" + "="*80)
        println("TEST: Output Valid Timing")
        println("="*80)

        // Accumulate
        for (cycle <- 0 until 5) {
          for (i <- 0 until symbolN) {
            for (j <- 0 until TPN) {
              dut.io.din.sliceConf(i)(j).poke(10.U)
            }
          }
          dut.io.din.valid.poke(true.B)
          dut.io.din.done.poke(false.B)
          dut.clock.step()
        }

        // Assert done
        println("\nAsserting done signal...")
        dut.io.din.done.poke(true.B)
        dut.io.din.valid.poke(true.B)
        dut.clock.step()

        // Check timing over multiple cycles
        println("\nMonitoring valid signal timing:")
        for (cycle <- 0 until 5) {
          val valid = dut.io.out.valid.peek().litToBoolean
          val conf = dut.io.out.confScore(0).peek().litValue
          println(f"  Cycle $cycle: valid=$valid, conf=$conf")

          if (cycle == 0) {
            assert(valid, "Output should be valid on first cycle after done")
          }
          dut.io.din.valid.poke(false.B)
          dut.io.din.done.poke(false.B)
        }
      }
  }

  behavior of "Accumulator edge cases"

  it should "handle back-to-back accumulations correctly" in {
    test(new Accumulator(imgWidth, TPN, symbolN))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        println("\n" + "="*80)
        println("TEST: Back-to-Back Accumulations")
        println("="*80)

        for (runNum <- 0 until 3) {
          println(s"\nRun ${runNum + 1}:")

          // Accumulate
          for (cycle <- 0 until 9) {
            for (i <- 0 until symbolN) {
              for (j <- 0 until TPN) {
                dut.io.din.sliceConf(i)(j).poke(5.U)
              }
            }
            dut.io.din.valid.poke(true.B)
            dut.io.din.done.poke(false.B)
            dut.clock.step()
          }

          // Done
          dut.io.din.done.poke(true.B)
          dut.io.din.valid.poke(true.B)
          dut.clock.step()

          val result = dut.io.out.confScore(0).peek().litValue
          val expected = 10 * 5 * TPN  // 10 cycles * 5 per input * TPN inputs
          println(f"  Result: $result, Expected: $expected")

          assert(result == expected, s"Run ${runNum + 1} produced incorrect result")

          // Brief idle before next run
          dut.io.din.done.poke(false.B)
          dut.io.din.valid.poke(false.B)
          dut.clock.step(4)
        }

        println("\n   SUCCESS:  All runs produced correct results")
      }
  }

  behavior of "Accumulator with zero inputs"

  it should "correctly handle all-zero inputs" in {
    test(new Accumulator(imgWidth, TPN, symbolN))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        println("\n" + "="*80)
        println("TEST: All-Zero Inputs")
        println("="*80)

        println("\nAccumulating all zeros...")
        for (cycle <- 0 until imgWidth) {
          for (i <- 0 until symbolN) {
            for (j <- 0 until TPN) {
              dut.io.din.sliceConf(i)(j).poke(0.U)
            }
          }
          dut.io.din.valid.poke(true.B)
          dut.io.din.done.poke(false.B)
          dut.clock.step()
        }

        dut.io.din.done.poke(true.B)
        dut.io.din.valid.poke(true.B)
        dut.clock.step()

        dut.io.out.valid.expect(1.U, "Valid should be true clockcycle after done")
        println("\nChecking outputs:")
        var allZero = true
        for (i <- 0 until symbolN) {
          val result = dut.io.out.confScore(i).peek().litValue
          println(f"  Symbol[$i]: $result")
          if (result != 0) allZero = false
        }

        if (allZero) {
          println("   SUCCESS:  All outputs correctly zero")
        } else {
          println("   FAILURE:  Some outputs non-zero with zero inputs")
        }

        assert(allZero, "All-zero inputs should produce all-zero outputs")
      }
  }
}