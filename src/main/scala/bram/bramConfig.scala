package bram

import chisel3._

/** Configuration objects for BRAM memory layouts
  */
object BramConfig {

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
