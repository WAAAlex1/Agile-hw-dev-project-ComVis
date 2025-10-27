import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

/* TODO:
 * - Add random selection of images
 * - consider converting directly to binary using thresholds
 */

class MnistHandler(val Path: String) {
  var images: Array[Array[Array[Byte]]] = Array.ofDim[Byte](60000, 28, 28)
  var labels: Array[Byte]               = Array.ofDim[Byte](60000)

  def readMnist(): Unit = {
    // val imagePath = "/home/andreas/Documents/agile-hw/MNIST_ORG/train-images.idx3-ubyte"
    val imagePath   = this.Path + "train-images.idx3-ubyte"
    var ubyteImages = new File(imagePath)
    val imageStream = new java.io.FileInputStream(ubyteImages)

    val dataBuffer = new Array[Byte](1)

    try {
      imageStream.skip(16) // skip header
      for (i <- 0 until 60000)
        for (j <- 0 until (28 * 28)) {
          imageStream.read(dataBuffer)
          val pixelVal = dataBuffer(0) & 0xff
          this.images(i)(j / 28)(j % 28) = pixelVal.toByte
        }
    } catch {
      case e: IOException => e.printStackTrace()
    } finally {
      println(imageStream.available() / (28 * 28))
      imageStream.close()
    }

    // val image = new BufferedImage(28, 28, BufferedImage.TYPE_BYTE_GRAY)

    // for (i <- 0 until 28) {
    //   for (j <- 0 until 28) {
    //     image1.setRGB(j, i, images(0)(i)(j) << 16 | images(0)(i)(j) << 8 | images(0)(i)(j))
    //   }
    // }

    // ImageIO.write(image1, "png", new File("/home/andreas/Documents/agile-hw/MNIST_ORG/image1.bmp"))
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

  def countLabelOccurrences(): Map[Byte, Int] =
    this.labels.groupBy(identity).view.mapValues(_.length).toMap

  def Sort: Unit = {
    val combined = this.labels.zip(this.images)
    val sorted   = combined.sortBy(_._1)
    this.labels = sorted.map(_._1)
    this.images = sorted.map(_._2)
  }

  def saveToBmp(image: Array[Array[Byte]], name: String): Unit = {
    val image1 = new BufferedImage(28, 28, BufferedImage.TYPE_BYTE_GRAY)

    for (i <- 0 until 28)
      for (j <- 0 until 28)
        image1.setRGB(j, i, images(0)(i)(j) << 16 | images(0)(i)(j) << 8 | images(0)(i)(j))

    ImageIO.write(image1, "bmp", new File("/home/andreas/Documents/agile-hw/MNIST_ORG/" + name + ".bmp"))
  }
}
