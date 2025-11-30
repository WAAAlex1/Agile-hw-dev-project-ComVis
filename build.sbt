//scalacOptions ++= Seq(
//  "-deprecation",
//  "-feature",
//  "-unchecked",
// // "-Xfatal-warnings",
//  "-language:reflectiveCalls",
//)

//scalaVersion := "2.13.14"
//val chiselVersion = "6.0.0"
//addCompilerPlugin("edu.berkeley.cs" %% "chisel3-plugin" % chiselVersion cross CrossVersion.full)
//libraryDependencies += "edu.berkeley.cs" %% "chisel3" % chiselVersion
//libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "0.6.2"

// For property based testing
//libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.15" % Test
//libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.17.0" % Test

// UART:
//libraryDependencies += "com.fazecast" % "jSerialComm" % "[2.0.0,3.0.0)"




scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  // "-Xfatal-warnings",
  "-language:reflectiveCalls",
  "-Ymacro-annotations"
)

scalaVersion := "2.13.12"
val chiselVersion = "6.0.0"

addCompilerPlugin("org.chipsalliance" %% "chisel-plugin" % chiselVersion cross CrossVersion.full)
libraryDependencies += "org.chipsalliance" %% "chisel" % chiselVersion
libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "6.0.0"

// For property based testing
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.15" % Test
libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.17.0" % Test

// UART:
libraryDependencies += "com.fazecast" % "jSerialComm" % "[2.0.0,3.0.0)"