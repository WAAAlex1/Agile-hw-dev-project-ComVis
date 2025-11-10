import java.awt.image.BufferedImage
import java.io.File
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
}
