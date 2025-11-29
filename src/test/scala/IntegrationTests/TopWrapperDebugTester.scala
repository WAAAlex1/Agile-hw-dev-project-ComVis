package IntegrationTests

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import java.io.{File, PrintWriter}

import topLevel._

/**
 * Debug test to inspect TopWrapper internal behavior
 */
class TopWrapperDebugTester extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "TopWrapper debug inspection"

  it should "show detailed internal state during single image processing" in {
    println("\n" + "="*80)
    println("DEBUG TEST: Single Image Processing")
    println("="*80)

    // Very simple config for debugging
    val imgWidth = 8  // Small for easier inspection
    val TPN = 2
    val symbolN = 2
    val IPN = 1

    // Generate simple test data
    val dir = new File("genForTests")
    if (!dir.exists()) dir.mkdirs()

    // Create templates with very simple patterns
    println("\nGenerating templates:")
    for (digit <- 0 until symbolN) {
      for (templateIdx <- 0 until TPN) {
        val absoluteIdx = digit * TPN + templateIdx
        val filename = s"genForTests/debug_template_${absoluteIdx}.hex"
        val writer = new PrintWriter(new File(filename))

        try {
          for (line <- 0 until imgWidth) {
            // Template pattern: digit determines upper bits
            val value = if (digit == 0) 0xFF else 0x00  // All 1s for digit 0, all 0s for digit 1
            writer.println(f"${value}%02X")
          }
          println(s"  Template[$digit][$templateIdx] (abs=$absoluteIdx): all 0x${if (digit == 0) "FF" else "00"}")
        } finally {
          writer.close()
        }
      }
    }

    // Create test image matching digit 0 (all 0xFF)
    val imageFile = "genForTests/debug_image.hex"
    val imageWriter = new PrintWriter(new File(imageFile))
    try {
      for (line <- 0 until imgWidth) {
        imageWriter.println("FF")  // Should match digit 0 templates perfectly
      }
      println(s"\nTest image: all 0xFF (should match digit 0)")
    } finally {
      imageWriter.close()
    }

    test(new TopWrapper(
      imgWidth = imgWidth,
      TPN = TPN,
      IPN = IPN,
      symbolN = symbolN,
      templatePath = "genForTests/debug_template",
      imagePath = Some(imageFile),
      debug = false,
      useDebouncer = false
    )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

      println("\n" + "="*80)
      println("Starting test sequence")
      println("="*80)

      dut.io.start.poke(false.B)
      dut.io.digitSel.poke(0.U)     // select 0 as input image
      dut.io.imgSel.poke(0.U)       // select 0 as input image
      dut.clock.step(5)

      println("\nTriggering image load...")
      dut.io.start.poke(true.B)
      dut.clock.step(1)
      dut.io.start.poke(false.B)

      // Monitor ROM transfer
      println("\nMonitoring ROM transfer:")
      var cycle = 0
      while (dut.io.debug.romBusy.peek().litToBoolean && cycle < 50) {
        if (cycle % 4 == 0 || cycle < 10) {
          println(f"  Cycle $cycle%2d: ROM busy=${dut.io.debug.romBusy.peek().litToBoolean}")
        }
        dut.clock.step(1)
        cycle += 1
      }
      println(s"  ROM transfer complete at cycle $cycle")

      // Check if ROM triggered startOut
      if (dut.io.debug.romStartOut.peek().litToBoolean) {
        println("  ROM startOut pulse detected")
      }

      // Wait for processing to complete
      println("\nWaiting for processing...")
      var procCycle = 0
      val maxCycles = 100
      while (!dut.io.done.peek().litToBoolean && procCycle < maxCycles) {
        if (procCycle % 10 == 0 || procCycle < 10) {
          println(f"  Cycle $procCycle%2d: done=${dut.io.done.peek().litToBoolean}")
        }
        dut.clock.step(1)
        procCycle += 1
      }

      if (dut.io.done.peek().litToBoolean) {
        println(s"    Processing complete at cycle $procCycle")
      } else {
        println(s"    ERROR: Processing timed out after $procCycle cycles")
      }

      // Read final results
      val predictedDigit = dut.io.debug.bestIdx.peek().litValue.toInt
      val confidence = dut.io.debug.bestConf.peek().litValue.toInt
      val maxScore = imgWidth * imgWidth * TPN

      println("\n" + "="*80)
      println("RESULTS")
      println("="*80)
      println(s"Expected digit: 0 (image is all 0xFF)")
      println(s"Predicted digit: $predictedDigit ${if (predictedDigit == 0) "SUCCESS" else "FAILURE"}")
      println(s"Confidence: $confidence / $maxScore (${confidence * 100 / maxScore}%)")

      println(s"\nExpected confidence breakdown:")
      println(s"  Digit 0 templates (all 0xFF): should match perfectly = ${imgWidth * imgWidth * TPN}")
      println(s"  Digit 1 templates (all 0x00): should match 0 bits = 0")

      println("\nDumping VCD for detailed inspection...")
      println("Check test_run_dir/*/TopWrapper.vcd for waveforms")
    }
  }

  it should "check if accumulator is resetting between images" in {
    println("\n" + "="*80)
    println("DEBUG TEST: Accumulator Reset Check")
    println("="*80)

    val imgWidth = 8
    val TPN = 2
    val symbolN = 2
    val IPN = 2  // Test 2 images to see if accumulator resets

    test(new TopWrapper(
      imgWidth = imgWidth,
      TPN = TPN,
      IPN = IPN,
      symbolN = symbolN,
      templatePath = "genForTests/debug_template",
      imagePath = Some("genForTests/debug_image.hex"),
      useDebouncer = false
    )) { dut =>

      println("Processing same image twice to check accumulator reset...\n")

      for (runNum <- 0 until 2) {
        println(s"Run ${runNum + 1}:")

        dut.io.digitSel.poke(0.U)
        dut.io.imgSel.poke(0.U)
        dut.io.start.poke(true.B)
        dut.clock.step(1)
        dut.io.start.poke(false.B)

        var cycles = 0
        while (!dut.io.done.peek().litToBoolean && cycles < 100) {
          dut.clock.step(1)
          cycles += 1
        }

        val conf = dut.io.debug.bestConf.peek().litValue.toInt
        val pred = dut.io.debug.bestIdx.peek().litValue.toInt

        println(s"  Predicted: $pred, Confidence: $conf")
        dut.clock.step(10)  // Wait between runs
      }

      println("\nIf both runs have same confidence, accumulator is resetting correctly")
      println("If confidence doubles on second run, accumulator not resetting!")
    }
  }
}