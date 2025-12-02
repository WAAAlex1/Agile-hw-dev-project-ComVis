# Computer Vision for number categorization

## Introduction

This project has been made as part of the [Agile Hardware Design [02203]](https://lifelonglearning.dtu.dk/compute/enkeltfag/agil-hardwareudvikling/) course  offered at The Technical University of Denmark (DTU) and taught by [Martin Schoeberl](https://www.imm.dtu.dk/~masca/). 
Computer Vision was chosen as an interesting area of work where hardware generation using Chisel could prove useful in an agile working environment.

### Purpose of the project

The project purposefully avoids the use of machine learning algorithms and instead opts for a simple image comparison algorithm.
The hardware accelerator is highly parallel and modularized so it can easily be modified to implement more complex algorithms.
At its core, our project is a generic parallelized modular data processing accelerator that could potentially be implemented for a plethora of use-cases outside image processing.
The project does make use of the [MNIST](https://en.wikipedia.org/wiki/MNIST_database) database of handwritten digits for training image processing systems, even though our model doesn't require training.
Instead, the ROM-initialized templates are compared against the input image to calculate a confidence score for each possible digit. This could easily be expanded to include additional symbols like letters at the cost of more memory.

Contributions to this project repository have been made by:

* s223998 - Alexander Aakersø - GIT: [WAAAlex1](https://github.com/WAAAlex1)
* s224038 - Georg Dyvad - GIT: [GeorgBD](https://github.com/GeorgBD)
* s224032 - Frederik Larsen - GIT: [FrederikOeLar](https://github.com/FrederikOeLar)
* s224039 - Sofus Hammelsø - GIT: [sofusham](https://github.com/sofusham)
* s203855 - Andreas Jensen - GIT: [TH3DUCKK](https://github.com/TH3DUCKK)

## Setup

The tools and setup required for this project are described in the course repository at: [Setup](https://github.com/schoeberl/agile-hw/blob/main/Setup.md)

### Testing installation

Before the installation can be tested, some files need to be generated first. To do this, run:

```
sbt "testOnly SoftwareTests.bmp2HexTest"
```

This creates hex files based on the MNIST database.

Now it is possible to run all tests (warning - this will take a while [approx 30-40 minutes].
```
sbt test
```
We recommend just running the UnitTests as well as selected fast integrationTests. This can be done by running:
```
sbt "testOnly UnitTests.*"
sbt "testOnly IntegrationTests.* -- -n fast"
```
Everything should pass if the project has been set up correctly. These steps are mainly a pre-causion as all tests will automatically generate their necessary input files.

GIT-CI has been set up and utilized for this project. For this we reference our [worflows](https://github.com/WAAAlex1/Agile-hw-dev-project-ComVis/tree/master/.github/workflows). The main branch is protected to an additional degree, only allowing for merges through pull requests.


## Targeted hardware

This project is designed to run on various FPGA hardware platforms. It has been run and demoed on a Nexys A7-100T FPGA.

## Generating the hardware module

If all tests passed, the module should be ready to generate everything by running:
```
sbt run
```
After which you will be prompted to choose a top-level.

We have created two top-level wrappers for synthesis to an FPGA-board to choose from:
1. **TopWrapper**, a standard top-level wrapper with input-signals to choose between different preloaded input-images.
2. **TopWrapperUART**, a serial communication based top-level wrapper in which the input image must be sent through UART.

Both top-level wrappers include output signals for displaying the identified digit and its confidence score on a seven segment display.

To specifically generate either of these we also recommend calling either of:
```
sbt "runMain topLevel.TopWrapper"
sbt "runMain topLevel.TopWrapperUART"
```
This will generate a TopWrapper.sv / TopWrapperUART.sv file located within /generated/. To run this design on an FPGA either of these options are viable:

1. **Manually create a new Vivado project. Add files within fpga/topWrapper or fpga/topWrapperUART**

We recommend version 2023.1, however others are also likely to work.

If running for TopWrapper you will need to import all memory initialization files manually. Make sure to mark these as memory initialization files. Also ensure that the topWrapper.sv file defines the following:

```
Within topWrapper.sv:

------- This shold be commented out  ----------

// Include rmemory initializers in init blocks unless synthesis is set
//`ifndef SYNTHESIS
//  `ifndef ENABLE_INITIAL_MEM_
//    `define ENABLE_INITIAL_MEM_
//  `endif // not def ENABLE_INITIAL_MEM_
//`endif // not def SYNTHESIS
------------------------------------------------

`define ENABLE_INITIAL_MEM_           <------ This should be uncommented
```

If running for TopWrapperUART you will need to ensure that your constraints file contains the following - this avoids Vivado optimizing bram modules away:
```
set_property KEEP_HIERARCHY TRUE [get_cells -hierarchical -filter {NAME =~ "*templateBram_*"}]    <---- uncommented
```

2. Simply utilize bitstream files located within /fpga/topWrapper/bitstream OR /fpga/topWrapperUART/bitstream to program FPGA using Vivado.

3. Initialize the vivado project from the zipped folder within /fpga/topWrapper/projectZipped


## Dependencies

The project's only dependencies are found in the [build](https://github.com/WAAAlex1/Agile-hw-dev-project-ComVis/blob/master/build.sbt) file.
These include general scala and chisel library dependencies as well as a serial port communication library [jSerialComm](https://fazecast.github.io/jSerialComm/) that we use for sending the image over UART to the FPGA.
We recommend following the [Setup](https://github.com/schoeberl/agile-hw/blob/main/Setup.md) and studying [build.sbt](https://github.com/WAAAlex1/Agile-hw-dev-project-ComVis/blob/master/build.sbt)

## Function and Results

Due to hardware generation, this project is highly configureable. The current data (MNIST) conforms itself well to 32x32 based memory blocks which are inferred in bram.

- Using the UART Top module (topWrapperUART) these BRAMS are left un-initialized and it is the responsiblity of the UART driver to provide both templates and data.
- Using the normal Top module (topWrapper) these BRAMS are initialized using memory files. A series of 100 images (10 for each symbol) are stored in ROM and can be cycled through for comparison with templates by buttons, switches and other board-io.

Results are reported using an LED signalling the completion of a comparison. The result is directly displayed on the 7segment.

```
     [8] 8 [8] 8 8 8 8 [8 8]
      ^     ^            ^
  Expected Digit         |
            |            |
     Predicted Digit     |
                         |
            Confidence score (percent as HEX)

```

Inputs are given using switches and a button signalling start. For the precise location of these we refer to the constraint files.

```
     -------------Switches -------------
     [][][][] [][][][] [][][][] [][][][]
     ^        ^ ^ ^ ^           ^ ^ ^ ^
   reset      | | | |           | | | |
              | | | |        select digit (0-9)
          select image (0-9)
```

The following table showcases percentage results for all 10 images for each of the 10 digits.

**Digit num:**  | **Digit 0** | **Digit 1** | **Digit 2**     | **Digit 3**       | **Digit 4**      | **Digit 5**      | **Digit 6** | **Digit 7**    | **Digit 8**     | **Digit 9**
-------- |---------|---------|-------------|---------------|--------------|--------------|---------|------------|-------------| ----------
img 0: Conf % [prediction digit] | 87% [0] | 95% [1] | 88% [2]     | 91% [3]       | 90% [4]      | 89% [1] !!!  | 93% [6] | 92% [7]    | 89% [1] !!! | 90% [1] !!!
img 1: Conf % [prediction digit] | 92% [0] | 96% [1] | 88% [7] !!! | 86% [1] !!!   | 86% [9] !!!  | 88% [6] !!!  | 93% [6] | 89% [7]    | 88% [8]     | 92% [9]
img 2: Conf % [prediction digit] | 91% [0] | 95% [1] | 87% [2]     | 89% [3]       | 86% [4]      | 89% [1] !!!  | 92% [6] | 92% [7]    | 90% [1] !!! | 92% [9]
img 3: Conf % [prediction digit] | 89% [0] | 96% [1] | 88% [2]     | 86% [1] !!!   | 90% [4]      | 87% [5]      | 86% [6] | 90% [7]    | 86% [8]     | 86% [9]
img 4: Conf % [prediction digit] | 91% [0] | 96% [1] | 87% [3] !!! | 91% [3]       | 89% [1] !!!  | 87% [0] !!!  | 89% [6] | 89% [9]!!! | 89% [1] !!! | 86% [9]
img 5: Conf % [prediction digit] | 88% [0] | 96% [1] | 89% [2]     | 86% [3]       | 91% [4]      | 88% [1] !!!  | 93% [6] | 91% [7]    | 89% [1] !!! | 91% [9]
img 6: Conf % [prediction digit] | 91% [0] | 96% [1] | 87% [2]     | 86 [1] !!!    | 87% [4]      | 89% [1]      | 91% [6] | 93% [7]    | 88% [8]     | 92% [9]
img 7: Conf % [prediction digit] | 90% [0] | 96% [1] | 87% [1] !!! | 90% [3]       | 87% [4]      | 89% [8] !!!  | 93% [6] | 93% [7]    | 91% [8]     | 92% [9]
img 8: Conf % [prediction digit] | 87% [0] | 96% [1] | 84% [3] !!! | 88% [3]       | 88% [4]      | 87% [5]      | 91% [6] | 93% [7]    | 90% [8]     | 91% [9]
img 9: Conf % [prediction digit] | 90% [0] | 96% [1] | 90% [2]     | 91% [3]       | 91% [4]      | 87% [9] !!!  | 88% [6] | 91% [7]    | 91% [8]     | 89% [9]
**Total**                        | **10/10**|**10/10**| **6/10**    | **7/10**      | **8/10**     | **3/10**     | **10/10**   | **9/10**       | **6/10**        | **9/10**

We find that for this method, the results are overall good, however large discrepancies exist between the digits. Confidence for individual images within each digit more or less the same.
Structurally this makes a alot of sense. Some digits simply have more in common with others. After all our actual algorithm is fairly crude - we are just doing XNOR masking - so
overlapping images can cause issues.


## Acknowledgements







