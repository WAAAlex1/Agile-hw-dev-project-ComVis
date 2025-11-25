import mnist.MnistHandler

class comvis_gm(val Path: String, val Width: Int, val nTemplates: Int, val nInputs: Int, val nProcessingElements: Int, val Threshold: Int) {

  // Ram modules
  val templateImages: Array[Array[Int]] = Array.ofDim[Int](nTemplates, this.Width)
  val templateLabels: Array[Byte] = Array.ofDim[Byte](nTemplates)
  val inputImages: Array[Array[Int]] = Array.ofDim[Int](nInputs, this.Width)
  var inputResults: Array[Byte] = Array.ofDim[Byte](nInputs)

  // Registers
  var confRegisters: Array[Int] = Array.ofDim[Int](10)
  var inputAddr: Int = 0
  var templateAddr: Int = 0
  

  def loadTemplates(): Unit = {
    val mnistHandler: MnistHandler = new MnistHandler(this.Path, this.Width)

    mnistHandler.readMnist()
    mnistHandler.readLabels()

    for (i <- 0 until nTemplates) {
      for (y <- 0 until this.Width) {
        var lineValue: Int = 0
        for (x <- 0 until this.Width) {
          lineValue = (lineValue << 1) | (if (mnistHandler.images(i)(y)(x) > this.Threshold) 1 else 0)
        }
        this.templateImages(i)(y) = lineValue
      }
      this.templateLabels(i) = mnistHandler.labels(i)

    }
  }

  def loadInput(): Unit = {
    val mnistHandler: MnistHandler = new MnistHandler(this.Path, this.Width)

    mnistHandler.readMnist(true)

    for (i <- 0 until nInputs) {
      for (y <- 0 until this.Width) {
        var lineValue: Int = 0
        for (x <- 0 until this.Width) {
          lineValue = (lineValue << 1) | (if (mnistHandler.images(i)(y)(x) > this.Threshold) 1 else 0)
        }
        this.inputImages(i)(y) = lineValue
      }
    }
  }

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

    for (i <- 1 until 9) {
      if (this.confRegisters(i) > confMax) {
        confMax = this.confRegisters(i)
        idxMax  = i
      }
    }

    this.inputResults(this.inputAddr) = this.templateLabels(idxMax)
  }

  def step(): Unit = {
    for (i <- 0 until nProcessingElements) {
      // Simulate processing element operation
      this.masker(i)
    }
    if (this.templateAddr >= this.Width) {
      this.evaluator()
      this.templateAddr = 0
      this.inputAddr += 1
    } else {
      this.templateAddr += 1
    }
  }

}
