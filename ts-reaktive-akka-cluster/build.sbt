
description := "Provides utility classes that tie Akka clustering into the rest of the libs in ts-reaktive"

// Because of https://github.com/cuppa-framework/cuppa/pull/113
parallelExecution in Test := false

libraryDependencies ++= {
  Seq(
    "junit" % "junit" % "4.11" % "test",
    "org.assertj" % "assertj-core" % "3.2.0" % "test",
    "org.mockito" % "mockito-core" % "1.10.19" % "test",
    "com.novocode" % "junit-interface" % "0.11" % "test",
    "org.forgerock.cuppa" % "cuppa" % "1.1.0" % "test",
    "org.forgerock.cuppa" % "cuppa-junit" % "1.1.0" % "test"
  )
}

fork in Test := true