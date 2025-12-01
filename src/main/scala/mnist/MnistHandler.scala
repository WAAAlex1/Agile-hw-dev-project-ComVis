package mnist

import java.io.FileInputStream
import java.awt.image.BufferedImage
import java.io.{ File, IOException }
import javax.imageio.ImageIO

/* TODO:
 * - Add random selection of images
 * - consider converting directly to binary using thresholds i.e. avoid bmp files as intermediates
 */

class MnistHandler(val Path: String, val Width: Int, val testInputs: Boolean = false) {
  val nImages = if (testInputs) 10000 else 60000 // Number of images in the dataset

  var images: Array[Array[Array[Byte]]] = Array.ofDim[Byte](nImages, this.Width, this.Width)
  var labels: Array[Byte]               = Array.ofDim[Byte](nImages)

  require(Width >= 20, "Width is now smaller than the MNIST numbers without padding")
  // TODO: Add check for loading test vs training images
  def readMnist(): Unit = {

    var imagePath: String = ""

    if (this.testInputs) {
      imagePath = this.Path + "t10k-images.idx3-ubyte"
    } else {
      imagePath = this.Path + "train-images.idx3-ubyte"
    }

    var ubyteImages = new File(imagePath)
    val imageStream = new java.io.FileInputStream(ubyteImages)

    val dataBuffer  = new Array[Byte](1)
    val mnistImages = Array.ofDim[Byte](nImages, 28, 28)

    try {
      imageStream.skip(16) // skip header
      for (i <- 0 until nImages)
        for (j <- 0 until (28 * 28)) { // 28x28 pixels since that is the MNIST standard
          imageStream.read(dataBuffer)
          val pixelVal = dataBuffer(0) & 0xff
          mnistImages(i)(j / 28)(j % 28) = pixelVal.toByte
        }
    } catch {
      case e: IOException => e.printStackTrace()
    } finally
      // println(imageStream.available() / (28 * 28))
      imageStream.close()

    // Add or remove padding to match desired Width if the Width is different from 28
    if (this.Width != 28) {
      val padding = (this.Width - 28) / 2
      for (i <- 0 until nImages)
        for (y <- 0 until this.Width)
          for (x <- 0 until this.Width)
            if (y >= padding && y < padding + 28 && x >= padding && x < padding + 28) {
              this.images(i)(y)(x) = mnistImages(i)(y - padding)(x - padding)
            } else {
              this.images(i)(y)(x) = 0
            }
    } else {
      this.images = mnistImages
    }
  }

  // TODO: Add check for loading test vs training images
  def readLabels(): Unit = {

    var labelPath: String = ""

    if (this.testInputs) {
      labelPath = this.Path + "t10k-labels.idx1-ubyte"
    } else {
      labelPath = this.Path + "train-labels.idx1-ubyte"
    }
    var ubyteLabels = new File(labelPath)
    val labelStream = new java.io.FileInputStream(ubyteLabels)

    val dataBuffer = new Array[Byte](1)

    try {
      labelStream.skip(8) // skip header
      for (i <- 0 until nImages) {
        labelStream.read(dataBuffer)
        this.labels(i) = dataBuffer(0)
      }
    } catch {
      case e: IOException => e.printStackTrace()
    } finally
      labelStream.close()
  }

  def countLabelOccurrences(): Map[Byte, Int] = this.labels.groupBy(identity).view.mapValues(_.length).toMap

  def Sort(): Unit = {
    val combined = this.labels.zip(this.images)
    val sorted   = combined.sortBy(_._1)
    this.labels = sorted.map(_._1)
    this.images = sorted.map(_._2)
  }

  def saveToBmp(image: Array[Array[Byte]], name: String): Unit = {
    val bImage = new BufferedImage(this.Width, this.Width, BufferedImage.TYPE_BYTE_GRAY)

    for (i <- 0 until this.Width)
      for (j <- 0 until this.Width)
        bImage.setRGB(j, i, image(i)(j) << 16 | image(i)(j) << 8 | image(i)(j)) // Combine to grayscale RGB
    ImageIO.write(bImage, "bmp", new File(name + ".bmp"))
  }

  def saveXNumbers(digit: Byte, number: Int = 10): Unit = {
    val firstIndex = this.labels.indexOf(digit)
    // println(s"Saving images for number: $number starting from index: $firstIndex")
    for (i <- firstIndex until firstIndex + number)
      this.saveToBmp(this.images(i), s"mnist_${digit}_${i - firstIndex}")
  }
}
