import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

/* TODO:
 * - Add random selection of images
 * - consider converting directly to binary using thresholds
 */

class MnistHandler(val Path: String, val Width: Int) {
  var images: Array[Array[Array[Byte]]] = Array.ofDim[Byte](60000, this.Width, this.Width)
  var labels: Array[Byte]               = Array.ofDim[Byte](60000)

  def readMnist(): Unit = {
    // val imagePath = "/home/andreas/Documents/agile-hw/MNIST_ORG/train-images.idx3-ubyte"
    val imagePath   = this.Path + "train-images.idx3-ubyte"
    var ubyteImages = new File(imagePath)
    val imageStream = new java.io.FileInputStream(ubyteImages)

    val dataBuffer = new Array[Byte](1)

    try {
      imageStream.skip(16) // skip header
      for (i <- 0 until 60000) {
        for (j <- 0 until (this.Width * this.Width)) {
          var pixelVal: Int = 0
          // Check if within padding y axis
          if (j / this.Width > (this.Width - 28)/2 && j / this.Width < 28 + (this.Width - 28)/2) {
            // Check if within padding x axis
            if (j % this.Width > (this.Width - 28)/2 && j % this.Width < 28 + (this.Width - 28)/2) {
            imageStream.read(dataBuffer)
            pixelVal = dataBuffer(0) & 0xff
            } 
          }
          this.images(i)(j / this.Width)(j % this.Width) = pixelVal.toByte
        }
      }
    } catch {
      case e: IOException => e.printStackTrace()
    } finally {
      println(imageStream.available() / (28 * 28))
      imageStream.close()
    }

    // Resize images if width is different from 28 (MNIST size)
    // if (Width != 28) {
    //   for (i <- 0 until 60000) {
    //     for (x <- 0 until Width) {
    //       for (y <- 0 until Width) {
    //         val srcX = x * 28 / Width
    //         val srcY = y * 28 / Width
    //         images(i)(x)(y) = mnistImages(i)(srcX)(srcY)
    //       }
    //     }
    //   }
    // } else {
    //   images = mnistImages
    // }
  }

  def readLabels(): Unit = {
    val labelPath   = this.Path + "train-labels.idx1-ubyte"
    var ubyteLabels = new File(labelPath)
    val labelStream = new java.io.FileInputStream(ubyteLabels)

    val dataBuffer = new Array[Byte](1)

    try {
      labelStream.skip(8) // skip header
      for (i <- 0 until 60000) {
        labelStream.read(dataBuffer)
        this.labels(i) = dataBuffer(0)
      }
    } catch {
      case e: IOException => e.printStackTrace()
    } finally
      labelStream.close()
  }

  def countLabelOccurrences(): Map[Byte, Int] = {
    this.labels.groupBy(identity).view.mapValues(_.length).toMap
  }

  def Sort: Unit = {
    val combined = this.labels.zip(this.images)
    val sorted   = combined.sortBy(_._1)
    this.labels = sorted.map(_._1)
    this.images = sorted.map(_._2)
  }

  def saveToBmp(image: Array[Array[Byte]], name: String): Unit = {
    val bImage = new BufferedImage(28, 28, BufferedImage.TYPE_BYTE_GRAY)

    for (i <- 0 until 28) {
      for (j <- 0 until 28) {
        bImage.setRGB(j, i, image(i)(j) << 16 | image(i)(j) << 8 | image(i)(j))
      }
    }
    ImageIO.write(bImage, "bmp", new File(name + ".bmp"))
  }
}
