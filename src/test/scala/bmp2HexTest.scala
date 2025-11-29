import chiseltest.ChiselScalatestTester
import org.scalatest.flatspec.AnyFlatSpec

import java.io.File

import mnist.BmpUtil

class bmp2HexTest extends AnyFlatSpec with ChiselScalatestTester {

  "bmp2hexfile" should "create correct hex file from BMP" in {
    val file      = new File("src/main/mnist/mnist_0_0.bmp")
    val threshold = 128
    val outputHex = "test_output"

    BmpUtil.bmp2hexfile(file, threshold, outputHex)
    println("Hello world")
    // Read the generated hex file and print its contents
    val generatedFile = scala.io.Source.fromFile("templates/" + outputHex + ".hex")
    var count = 0
    println("Generated Hex File Contents:")
    for (line <- generatedFile.getLines()) {
      count += 1
      println(line)
    }
    generatedFile.close()
    println("Total lines in hex file: " + count)
  }
  "saveTemplates" should "create hex files for all mnist templates" in {
    val width     = 32
    val threshold = 128

    BmpUtil.saveTemplates(width, threshold)
    println("Hex files for all mnist templates have been created.")
  }
  "saveInputsToHex" should "create hex files for all mnist test inputs" in {
    val width     = 32
    val threshold = 128

    BmpUtil.saveInputsToHex(width, threshold)
    println("Hex files for all mnist test inputs have been created.")
  }
}