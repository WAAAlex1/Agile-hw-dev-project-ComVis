
package SoftwareTests

import mnist.MnistHandler

class comvis_gm(val Path: String, val Width: Int, val nTemplates: Int, val nInputs: Int, val nProcessingElements: Int, val Threshold: Int) {
  // Path: Path to mnist dataset
  // Width: Width and height of mnist images
  // nTemplates: Number of templates to load, currently means the numbers it can handle (upto 9)
  // nInputs: Number of inputs to load
  // nProcessingElements: Number of processing elements in the comvis model
  // Threshold: Cutoff value for black or white pixels (0 to 255)
  // This model is not scalable beyond 10 templates and 10 processing elements i.e. a single template per processing element (currently)

  // Ram modules
  val templateImages: Array[Array[Int]] = Array.ofDim[Int](nTemplates, this.Width)
  val templateLabels: Array[Byte] = Array.ofDim[Byte](nTemplates) // For potential future use (i.e. figuring out template influence on results)
  val inputImages: Array[Array[Int]] = Array.ofDim[Int](nInputs, this.Width)
  val inputLabels: Array[Byte] = Array.ofDim[Byte](nInputs) // Ground truth labels for inputs
  var Results: Array[Byte] = Array.ofDim[Byte](nInputs) // Output results after evaluation

  // Registers
  var confRegisters: Array[Int] = Array.ofDim[Int](10)
  var inputAddr: Int = 0
  var templateAddr: Int = 0
  var done: Boolean = false

  /* 
    Load templates and inputs from mnist dataset
    Keep each template to a unique number, i.e. template 0 = number 0, template 1 = number 1, etc.
   */
  def loadTemplates(): Unit = {
    val mH: MnistHandler = new MnistHandler(this.Path, this.Width)

    mH.readMnist()
    mH.readLabels()
    mH.Sort()

    for (i <- 0 until nTemplates) {
      val firstIndex = mH.labels.indexOf(i.toByte)
      for (y <- 0 until this.Width) {
        var lineValue: Int = 0
        for (x <- 0 until this.Width) {
          var pixel = mH.images(firstIndex)(y)(x) & 0xff
          var bit  = if (pixel > this.Threshold) 1 else 0
          lineValue = (lineValue << 1) | bit
        }
        this.templateImages(i)(y) = lineValue
      }
      this.templateLabels(i) = mH.labels(firstIndex)
    }
  }

  def loadInput(): Unit = {
    val mH: MnistHandler = new MnistHandler(this.Path, this.Width)

    mH.readMnist(true)
    mH.readLabels(true)

    for (i <- 0 until nInputs) {
      for (y <- 0 until this.Width) {
        var lineValue: Int = 0
        for (x <- 0 until this.Width) {
          var pixel = mH.images(i)(y)(x) & 0xff
          var bit  = if (pixel > this.Threshold) 1 else 0
          lineValue = (lineValue << 1) | bit
        }
        this.inputImages(i)(y) = lineValue
      }
      this.inputLabels(i) = mH.labels(i)
    }
  }

  // Can only handle a single template per processing element currently
  def masker(templateIdx: Int): Unit = {
    val templateLine = this.templateImages(templateIdx)(this.templateAddr)
    val inputLine    = this.inputImages(this.inputAddr)(this.templateAddr)

    // Perform masking operation
    val xnorRes = ~(templateLine ^ inputLine)

    this.confRegisters(templateIdx) += Integer.bitCount(xnorRes & 0xFFFFFFFF)
  }

  def evaluator(): Unit = {
    var idxMax: Int = 0
    var confMax: Int = this.confRegisters(0)

    for (i <- 0 until confRegisters.length) {
      if (this.confRegisters(i) > confMax) {
        confMax = this.confRegisters(i)
        idxMax  = i
      }
    }

    this.Results(this.inputAddr) = this.templateLabels(idxMax)
  }


  def step(): Unit = {
    for (i <- 0 until nProcessingElements) {
      // Simulate processing element operation
      this.masker(i)
    }
    // If we have processed all rows of the current template, evaluate the result and increment to next input
    // Otherwise, increment to next row of the current template
    if (this.templateAddr >= this.Width) {
      this.evaluator()
      this.templateAddr = 0
      this.inputAddr += 1
      // Signal when all inputs have been processed
      if (this.inputAddr >= this.nInputs) {
        this.done = true
      }
    } else {
      this.templateAddr += 1
    }
  }

}
