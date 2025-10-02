import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

/* IDEA:
* Make this a helper function / class / object
* that can read MNIST data and convert to directly to the binary format for memory initialization
* for the synthesis to verilog
* Take threshold and path as input. Allow for randomly selecting images
*/

/* TODO:
* - read labels
* - sort images by label
* - Save 10 of each number as bmp
* - consider converting directly to binary using thresholds
*/


def readMnist(imagePath): Unit =
  //val imagePath = "/home/andreas/Documents/agile-hw/MNIST_ORG/train-images.idx3-ubyte"
  val ubyteImages = new File(imagePath)
  val imageStream = new java.io.FileInputStream(ubyteImages)

  val dataBuffer = new Array[Byte](1)
  val images = Array.ofDim[Byte](60000, 28, 28)

  try {
    imageStream.skip(16) // skip header
    for (i <- 0 until 60000) {
      for (j <- 0 until (28 * 28)) {
        imageStream.read(dataBuffer)
        val pixelVal = (dataBuffer(0) & 0xFF)
        images(i)(j / 28)(j % 28) = pixelVal.toByte
      }
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