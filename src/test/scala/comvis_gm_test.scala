import org.scalatest.flatspec.AnyFlatSpec

class comvis_gm_test extends AnyFlatSpec {
  // Currently only a basic test to check if loading works
  "comvis_gm_test" should "run a single input" in {
    val path = "src/main/mnist/"
    val width = 32
    val nTemplates = 10
    val nInputs = 1
    val nProcessingElements = 10
    val threshold = 128

    val gm = new comvis_gm(path, width, nTemplates, nInputs, nProcessingElements, threshold)
    gm.loadTemplates()
    gm.loadInput()

    println(s"Template Label: ${gm.templateLabels(0)}")
    for (i <- 0 until width) {
      println(f"Template Row ${i}%2d: " + gm.templateImages(0)(i).toBinaryString.reverse.padTo(width, '0').reverse.mkString)
    }

    println(s"Input Label: ${gm.inputLabels(0)}")
    //println(s"Predicted Label: ${gm.Results(0)}")

  }
}
