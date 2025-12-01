package UnitTests.bram

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths}
import bram._
import chisel3.util.log2Ceil

class InitRamTester extends AnyFlatSpec with ChiselScalatestTester {

  /** Generate test hex file with simple pattern */
  def generateTestFile(filename: String, IPN: Int, symbolN: Int, imgWidth: Int): Unit = {
    val dir = new File("genForTests")
    if (!dir.exists()) {
      dir.mkdirs()
      println(s"[Test] Created directory: genForTests")
    }

    val writer = new PrintWriter(new File(filename))
    try {
      val totalImages = IPN * symbolN
      for (imgIdx <- 0 until totalImages) {
        for (line <- 0 until imgWidth) {
          // Simple pattern: upper byte = image index, lower byte = line number
          val value = (imgIdx << 8) | line
          writer.println(f"${value}%08X")
        }
      }
      println(s"[Test] Generated test file: $filename")
      println(s"[Test] Total images: $totalImages, Lines per image: $imgWidth")
    } finally {
      writer.close()
    }
  }

  behavior of "InitRam"

  it should "transfer image and pulse startOut correctly with SyncReadMem timing" in {
    // Small test configuration
    val IPN = 2        // 2 images per digit
    val symbolN = 3    // 3 digits (0-2)
    val imgWidth = 32  // 32x32 images
    val TPN = 10       // 10 templates per digit
    val imageBramIdx = TPN * symbolN
    val lineAddrWidth = log2Ceil(imgWidth)  // = 5 bits

    // Generate test file
    val testFile = "genForTests/initram_test.mem"
    generateTestFile(testFile, IPN, symbolN, imgWidth)

    test(new InitRam(IPN, TPN, symbolN, imgWidth, Some(testFile)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        println("\n=== Testing InitRam with SyncReadMem ===")

        // Test 1: Initial state
        println("Test 1: Check initial state")
        dut.io.busy.expect(false.B)
        dut.io.startOut.expect(false.B)
        dut.io.writeOut.wrEn.expect(false.B)

        // Test 2: Transfer digit=1, imgSelect=0 (absolute image index = 1*2 + 0 = 2)
        println("Test 2: Transfer image (digit=1, img=0)")
        val testDigit = 1
        val testImg = 0
        val expectedAbsoluteIdx = testDigit * IPN + testImg  // Should be 2

        dut.io.digitSelect.poke(testDigit.U)
        dut.io.imgSelect.poke(testImg.U)
        dut.io.start.poke(true.B)
        dut.clock.step(1)
        dut.io.start.poke(false.B)

        // After start, enter stageRead state (busy=true, but no write yet)
        println("  Cycle 1: stageRead state (issuing first read)")
        dut.io.busy.expect(true.B)
        dut.io.writeOut.wrEn.expect(false.B)  // No write in stageRead
        dut.clock.step(1)

        // Now in transferring state - writes begin
        println("  Cycles 2-33: transferring state (writing lines)")
        dut.io.busy.expect(true.B)

        val capturedData = scala.collection.mutable.ArrayBuffer[Int]()

        for (line <- 0 until imgWidth) {
          val encodedAddr = (imageBramIdx << lineAddrWidth) | line
          val expectedValue = (expectedAbsoluteIdx << 8) | line

          // Expect write enable and correct address
          dut.io.writeOut.wrEn.expect(true.B)
          dut.io.writeOut.wrAddr.expect(encodedAddr.U)
          dut.io.writeOut.wrData.expect(expectedValue.U)
          capturedData += expectedValue

          if (line % 8 == 0 || line == imgWidth - 1) {
            println(f"    Line $line: addr=0x${encodedAddr}%X, data=0x${expectedValue}%X")
          }

          dut.clock.step(1)
        }

        println(s"  Transferred ${capturedData.length} lines")
        println(s"  First line: 0x${capturedData(0).toHexString}")
        println(s"  Last line: 0x${capturedData.last.toHexString}")

        // Test 3: Check startOut pulse
        println("Test 3: Verify startOut pulse (done state)")
        dut.io.busy.expect(false.B)
        dut.io.startOut.expect(true.B)  // Should pulse high for 1 cycle
        dut.io.writeOut.wrEn.expect(false.B)

        dut.clock.step(1)

        // startOut should go low after 1 cycle
        dut.io.startOut.expect(false.B)
        dut.io.busy.expect(false.B)

        println("\n=== Test 4: Transfer different image ===")
        // Test 4: Transfer different image (digit=2, imgSelect=1)
        val testDigit2 = 2
        val testImg2 = 1
        val expectedAbsoluteIdx2 = testDigit2 * IPN + testImg2  // Should be 5

        dut.io.digitSelect.poke(testDigit2.U)
        dut.io.imgSelect.poke(testImg2.U)
        dut.io.start.poke(true.B)
        dut.clock.step(1)
        dut.io.start.poke(false.B)

        // Skip stageRead cycle
        println("  Skipping stageRead cycle...")
        dut.io.busy.expect(true.B)
        dut.io.writeOut.wrEn.expect(false.B)
        dut.clock.step(1)

        // Verify first line write
        val expectedFirst = (expectedAbsoluteIdx2 << 8) | 0
        println(f"  First line write: 0x${expectedFirst}%X")
        dut.io.writeOut.wrEn.expect(true.B)
        dut.io.writeOut.wrData.expect(expectedFirst.U)

        // Skip to last line (imgWidth - 2 more steps, since we're at line 0)
        dut.clock.step(imgWidth - 1)

        // Verify last line write
        val expectedLast = (expectedAbsoluteIdx2 << 8) | (imgWidth - 1)
        println(f"  Last line write: 0x${expectedLast}%X")
        dut.io.writeOut.wrEn.expect(true.B)
        dut.io.writeOut.wrData.expect(expectedLast.U)

        dut.clock.step(1)

        // Check done state
        dut.io.startOut.expect(true.B)
        dut.io.busy.expect(false.B)
        println(s"  Transfer complete!")

        println("\n=== All InitRam tests passed ===\n")
      }
  }

  it should "handle back-to-back transfers correctly" taggedAs(org.scalatest.Tag("slow")) in {
    val IPN = 2
    val symbolN = 2
    val imgWidth = 8  // Smaller for faster test
    val TPN = 2

    val testFile = "genForTests/initram_backtoback.mem"
    generateTestFile(testFile, IPN, symbolN, imgWidth)

    test(new InitRam(IPN, TPN, symbolN, imgWidth, Some(testFile)))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

        println("\n=== Testing back-to-back transfers ===")

        // First transfer
        dut.io.digitSelect.poke(0.U)
        dut.io.imgSelect.poke(0.U)
        dut.io.start.poke(true.B)
        dut.clock.step(1)
        dut.io.start.poke(false.B)

        // Wait for completion: 1 (stageRead) + imgWidth (writes) + 1 (done)
        dut.clock.step(imgWidth + 1)
        dut.io.startOut.expect(true.B)
        dut.clock.step(1)

        // Immediately start second transfer
        dut.io.digitSelect.poke(1.U)
        dut.io.imgSelect.poke(1.U)
        dut.io.start.poke(true.B)
        dut.clock.step(1)
        dut.io.start.poke(false.B)

        // Wait for completion
        dut.clock.step(imgWidth + 1)
        dut.io.startOut.expect(true.B)

        println("  Back-to-back transfers completed successfully!")
      }
  }
}