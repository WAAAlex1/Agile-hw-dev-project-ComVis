import chiseltest.ChiselScalatestTester
import mnist.BmpUtil
import org.scalatest.flatspec.AnyFlatSpec

import java.io.File

import mnist.BmpUtil

class bmp2memTest extends AnyFlatSpec with ChiselScalatestTester {

  "bmp2memTest" should "output correct Vec for given BMP" in {
    val file = new File("src/test/resources/handDrawnTwo.bmp") // 28x28 image
    val threshold = 128
    val bmpData = BmpUtil.bmp2mem(file, threshold)

    bmpData.foreach {row =>
      var outputString = ""

      for (i <- 27 to 0 by -1) {
        val tempBit = (row >> i) & 0x1

        if (tempBit == 0) {
          outputString += " " // Black pixel
        } else {
          outputString += "#" // White pixel
        }
      }
      println(outputString)
    }
  }
}
