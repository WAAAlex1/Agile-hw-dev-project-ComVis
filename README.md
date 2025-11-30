# Computer Vision for number categorization

## Introduction

This project has been made as part of the [Agile Hardware Design [02203]](https://lifelonglearning.dtu.dk/compute/enkeltfag/agil-hardwareudvikling/) course  offered at The Technical University of Denmark (DTU) and taught by [Martin Schoeberl](https://www.imm.dtu.dk/~masca/). 
Computer Vision was chosen as an interesting area of work where hardware generation using Chisel could prove useful. 

### Purpose of the project

The project purposefully avoids the use of machine learning algorithms and instead opts for a simple image comparison algorithm. 
The hardware accelerator is highly parallel and modularized so it can easily be modified to implement more complex algorithms. 
At its core, our project is a generic parallelized modular data processing accelerator that could potentially be implemented for a plethora of use-cases outside image processing.
The project does make use of the [MNIST](https://en.wikipedia.org/wiki/MNIST_database) database of handwritten digits for training image processing systems, even though our model doesn't require training. 
Instead, the ROM-initialized templates are compared against the input image to calculate a confidence score for each possible digit. This could easily be expanded to include additional symbols like letters at the cost of more memory.

### Diagram
We here include a general diagram of the structure of the hardware with the standard top-level wrapper for synthesis:
![TopWrapper diagram](Documentation/agile_hardware_top_diagram.svg)

### Contributions
Contributions to this project repository have been made by:

* s223998 - Alexander Aakersø - GIT: [WAAAlex1](https://github.com/WAAAlex1)
* s224038 - Georg Dyvad - GIT: [GeorgBD](https://github.com/GeorgBD)
* s224032 - Frederik Larsen - GIT: [FrederikOeLar](https://github.com/FrederikOeLar)
* s224039 - Sofus Hammelsø - GIT: [sofusham](https://github.com/sofusham)
* s203855 - Andreas Jensen - GIT: [TH3DUCKK](https://github.com/TH3DUCKK)

## Setup

The tools and setup required for this project are described in the course repository at: [Setup](https://github.com/schoeberl/agile-hw/blob/main/Setup.md)

### Testing installation

Before the installation can be tested, some files need to be pre-compiled first. To do this, run:
```
example terminal code
```

That should create .bmp files. These can be converted further by running:
```
example terminal code
```

Now it is possible to run all tests:
```
sbt test
```

Everything should pass if the project has been set up correctly.

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


### Targeted hardware

This project is designed to run on various FPGA hardware platforms. 
There is, however, only a constraint file for the NexusA7 provided in the [Xilinx directory](https://github.com/WAAAlex1/Agile-hw-dev-project-ComVis/tree/master/Xilinx).

### Dependencies

The project's only dependencies are found in the [build](build.sbt) file.
These include general scala and chisel library dependencies as well as a serial port communication library [jSerialComm](https://fazecast.github.io/jSerialComm/) that we use for sending the image over UART to the FPGA.
