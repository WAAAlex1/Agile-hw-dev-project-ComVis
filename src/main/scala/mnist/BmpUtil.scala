package mnist

import java.awt.image.BufferedImage
import java.io.{ File, PrintWriter }
import javax.imageio.ImageIO

object BmpUtil {
  def bmp2mem(file: File, threshold: Int): Seq[Int] = {
    // file: Input bmp file (other file formats may also work)
    // threshold: Cutoff value for black or white pixels (0 to 255)
    // Outputs: A scala seq of Ints with the same length as the image height

    val img: BufferedImage = ImageIO.read(file)
    val width              = img.getWidth()
    val height             = img.getHeight()

    val tempArray = new Array[Int](height)

    for (y <- 0 until height) {
      var row = 0
      for (x <- 0 until width) {
        var pixel = img.getRGB(x, y) & 0xffffff // Kill alpha channel if present
        pixel =
          ((pixel & 0xff0000) >> 16) + ((pixel & 0x00ff00) >> 8) + (pixel & 0x0000ff) // Combine RGB values into single channel
        val bit = if (pixel >= threshold * 3) 1 else 0 // Threshold multiplied by 3 to account for 3 colour channels
        row = (row << 1) | bit
      }
      tempArray(y) = row
    }
    tempArray.toSeq // Convert output to sequence
  }

  // TODO: Should this be in the mnist folder?
  def bmp2hexfile(input: File, threshold: Int, name: String) = {
    // input: Input bmp file (other file formats may also work)
    // threshold: Cutoff value for black or white pixels (0 to 255)
    // name: Output file name (without extension)
    // Outputs: A hex file with each line representing a row of the image
    val path       = "templates/"
    val intSeq     = bmp2mem(input, threshold)
    val outputFile = new File(path + name + ".hex")
    try {

      val writer = new java.io.PrintWriter(outputFile)

      try
        intSeq.foreach(row => writer.println(f"$row%08x"))
      finally
        writer.close()

    } catch {
      case e: SecurityException               => e.printStackTrace()
      case fnf: java.io.FileNotFoundException => fnf.printStackTrace()
    }

  }

  // TODO: Rework this to avoid using bmp files as intermediates
  // TODO: Should this be in the mnist folder?
  def saveTemplates(width: Int, threshold: Int): Unit = {
    // Utility function to handle creating hex files for all mnist templates
    // width: Width and height of mnist images
    // threshold: Cutoff value for black or white pixels (0 to 255)
    val path = "src/main/mnist/"
    val mH   = new MnistHandler(path, width)
    mH.readMnist()
    mH.readLabels()
    mH.Sort()

    for (i <- 0 to 9) {
      mH.save10xNumber(i.toByte)
      for (j <- 0 to 9) {
        val fileName  = s"mnist_${i}_${j}.bmp"
        val inputFile = new File(fileName)
        bmp2hexfile(inputFile, threshold, f"template_${i * 10 + j}")
        if (!inputFile.delete()) {
          println(s"Could not delete temporary file: $fileName")
        }
      }
    }
  }

  // TODO: Should this be in the mnist folder?
  // NOTE: There should be 10 images of each number
  // NOTE: Inputs should be arranged 0 to 9 with 10 copies of each number
  def saveInputsToHex(width: Int, threshold: Int): Unit = {
    // Utility function to handle creating hex files for the test inputs
    // width: Width and height of mnist images
    // threshold: Cutoff value for black or white pixels (0 to 255)
    val path = "./src/main/mnist/"
    val mH   = new MnistHandler(path, width)
    mH.readMnist(testImages = true)
    mH.readLabels(testLabels = true)
    mH.Sort()

    // each row consist of 32 bits i.e. an Int
    val imageArray = new Array[Int]((10 * 10) * width)
    var number     = 0

    // Convert each row into binary and store in imageArray
    for (i <- 1 until (10 * 10)) {
      if (i % 10 == 0) number += 1
      val idx = mH.labels.indexOf(number.toByte) + (i % 10) // Get next occurrence of the number

      for (y <- 0 until width) {
        var row = 0
        for (x <- 0 until width) {
          var pixel = mH.images(idx)(y)(x) & 0xff
          val bit   = if (pixel >= threshold) 1 else 0
          row = (row << 1) | bit
        }
        // We write each row sequentially using i as image index
        imageArray(i * y) = row
      }
    }

    val outputFile = new File("mnist_input.hex")
    val writerHex  = new java.io.PrintWriter("templates/" + outputFile)

    try
      imageArray.foreach(row => writerHex.println(f"$row%08x"))
    finally
      writerHex.close()
  }
}
