# Computer Vision for number categorization

## Introduction

This project has been made as part of the [Agile Hardware Design [02203]](https://lifelonglearning.dtu.dk/compute/enkeltfag/agil-hardwareudvikling/) course  offered at The Technical University of Denmark and taught by [Martin Schoeberl](https://www.imm.dtu.dk/~masca/). Computer Vision was chosen as an interesting area of work where hardware generation using Chisel could prove useful. 

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

If all tests passed, the module should be ready to generate everything.

### Targeted hardware

This project is designed to run on various FPGA hardware platforms. There is, however, only a constraint file for the NexusA7 provided in the [Xilinx directory](https://github.com/WAAAlex1/Agile-hw-dev-project-ComVis/tree/master/Xilinx).

