import chisel3._
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object bmp2mem{
  def bmp2mem(file: File, threshold: Int): Vec[UInt] = {
    // file: Input bmp file (other file formats may also work)
    // threshold: Cutoff value for black or white pixels (0 to 0xFFFFFF)
    // Outputs: A Chisel Vec with the same size as the input image

    val img: BufferedImage = ImageIO.read(file)
    val width = img.getWidth()
    val height = img.getHeight()

    val tempArray = new Array[Int](height)

    for (y <- 0 until height){
      var row = 0
      for (x <- 0 until width){
        val pixel = img.getRGB(x, y) & 0xFFFFFF // Kill alpha channel if present
        val bit = if (pixel >= threshold) 1 else 0
        row = (row << 1) | bit
      }
      tempArray(y) = row
    }

    // Map Scala Array to Chisel Vec and return
    val outputVec: Vec[UInt] = VecInit(tempArray.map(x => x.U(width.W)))
    outputVec
  }
}
