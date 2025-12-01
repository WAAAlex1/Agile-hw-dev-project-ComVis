package bram

object bramUtil {
  import scala.io.Source
  import java.io.{ File, PrintWriter }

  def hexToCoe(hexFile: String, coeFile: String, depth: Int, radix: String = "16"): Unit = {
    val coeDir = new File(coeFile).getParentFile
    if (coeDir != null && !coeDir.exists()) {
      coeDir.mkdirs()
      println(s"[COE] Created directory: ${coeDir.getPath}")
    }

    val lines  = Source.fromFile(hexFile).getLines().toList.take(depth)
    val writer = new PrintWriter(new File(coeFile))

    try {
      writer.println(s"memory_initialization_radix=$radix;")
      writer.println("memory_initialization_vector=")
      val dataLines = lines.map(_.trim)
      writer.println(dataLines.mkString(",\n") + ";")

      println(s"[COE] Generated: $coeFile (${dataLines.length} entries)")
    } finally
      writer.close()
  }

  def generateAllTemplateCoe(
    numDigits: Int,
    TPN: Int,
    imgWidth: Int,
    hexBasePath: String,
    coeOutputDir: String
  ): Unit = {
    val numTemplates = numDigits * TPN;
    println(s"[COE] Generating COE files for $numTemplates templates...")

    for (i <- 0 until numDigits)
      for (j <- 0 until TPN) {
        val hexFile = s"${hexBasePath}_${i}_${j}.hex"
        val coeFile = s"${coeOutputDir}/template_${i}_${j}.coe"

        if (new File(hexFile).exists()) {
          hexToCoe(hexFile, coeFile, imgWidth)
        } else {
          println(s"[COE] WARNING: Hex file not found: $hexFile")
        }
      }

    println(s"[COE] Generated $numTemplates COE files in $coeOutputDir")
  }
}

/** Configuration objects for BRAM memory layouts
  */
object BramConfig {

  import chisel3._

  // MNIST 24x24 configuration (padding removed)
  object MnistPaddingRemoved {
    val imgWidth = 24 // removed padding to 24 pixels from 28
    val TPN      = 10 // Templates Per Number
    val symbolN  = 10 // Number of symbols (digits 0-9)
  }

  // Standard MNIST 28x28 configuration
  object MnistStandard {
    val imgWidth = 28 // standard 28 pixels
    val TPN      = 10 // Templates Per Number
    val symbolN  = 10 // Number of symbols (digits 0-9)
  }

  // MNIST 32x32 configuration (padding added for better BRAM utilization)
  object MnistPaddingAdded {
    val imgWidth = 32 // added padding to 32 pixels from 28
    val TPN      = 10 // Templates Per Number
    val symbolN  = 10 // Number of symbols (digits 0-9)
  }

  object MemInitMode extends Enumeration {
    type MemInitMode = Value
    val Simulation, Synthesis = Value
  }

}

/** Pre-configured MultiTemplateBram variants
  */
object ConfiguredBrams {

  import chisel3._

  // MNIST 24x24: 10 templates per digit, 10 digits
  class t24x100(initFiles: Option[Seq[String]] = None)
      extends MultiTemplateBram(
        TPN = BramConfig.MnistPaddingRemoved.TPN,
        symbolN = BramConfig.MnistPaddingRemoved.symbolN,
        imgWidth = BramConfig.MnistPaddingRemoved.imgWidth,
        initFiles = initFiles
      )

  // MNIST 28x28: 10 templates per digit, 10 digits
  class t28x100(initFiles: Option[Seq[String]] = None)
      extends MultiTemplateBram(
        TPN = BramConfig.MnistStandard.TPN,
        symbolN = BramConfig.MnistStandard.symbolN,
        imgWidth = BramConfig.MnistStandard.imgWidth,
        initFiles = initFiles
      )

  // MNIST 32x32: 10 templates per digit, 10 digits (better BRAM utilization)
  class t32x100(initFiles: Option[Seq[String]] = None)
      extends MultiTemplateBram(
        TPN = BramConfig.MnistPaddingAdded.TPN,
        symbolN = BramConfig.MnistPaddingAdded.symbolN,
        imgWidth = BramConfig.MnistPaddingAdded.imgWidth,
        initFiles = initFiles
      )

}
