package bram

import chisel3._

/** Configuration objects for BRAM memory layouts
  */
object BramConfig {

  // Standard MNIST 24x24 configuration
  object MnistPaddingRemoved {
    val imageHeight       = 24 // removed padding to 24 pixels from 28
    val imageWidth        = 24 // removed padding to 24 pixels from 28
    val templatesPerDigit = 10
    val numDigits         = 10
    val totalTemplates    = templatesPerDigit * numDigits // 100
  }

  // Optimized for BRAM inference (rounded to power of 2)
  object MnistStandard {
    val imageHeight       = 28 // standard 28 pixels
    val imageWidth        = 28 // standard 28 pixels
    val templatesPerDigit = 10
    val numDigits         = 10
    val totalTemplates    = templatesPerDigit * numDigits // 100
  }

  // Smaller test configuration
  object MnistPaddingAdded {
    val imageHeight       = 32 // added padding to 32 pixels from 28
    val imageWidth        = 32 // added padding to 32 pixels from 28
    val templatesPerDigit = 10
    val numDigits         = 10
    val totalTemplates    = templatesPerDigit * numDigits // 100
  }
}

/** Pre-configured MultiTemplateBram variants
  */
object ConfiguredBrams {

  // Standard MNIST: 100 templates, 24x24
  class t24x100(initFiles: Option[Seq[String]] = None)
      extends MultiTemplateBram(
        numTemplates = BramConfig.MnistPaddingRemoved.totalTemplates,
        numLines = BramConfig.MnistPaddingRemoved.imageHeight,
        lineWidth = BramConfig.MnistPaddingRemoved.imageWidth,
        initFiles = initFiles
      )

  // Small test config: 100 templates, 28x28
  class t28x100(initFiles: Option[Seq[String]] = None)
      extends MultiTemplateBram(
        numTemplates = BramConfig.MnistStandard.totalTemplates,
        numLines = BramConfig.MnistStandard.imageHeight,
        lineWidth = BramConfig.MnistStandard.imageWidth,
        initFiles = initFiles
      )

  // Optimized MNIST: 100 templates, 32x32 (better BRAM utilization)
  class t32x100(initFiles: Option[Seq[String]] = None)
      extends MultiTemplateBram(
        numTemplates = BramConfig.MnistPaddingAdded.totalTemplates,
        numLines = BramConfig.MnistPaddingAdded.imageHeight,
        lineWidth = BramConfig.MnistPaddingAdded.imageWidth,
        initFiles = initFiles
      )

}
