import sbt._
import Keys._
import sbtassembly.Plugin.AssemblyKeys._
import sbt.NameFilter._

object LevelDBJNISpecBuild extends Build {
  lazy val leveldbjnispecs = Project(id = "leveldbjni-specs", base = file(".")).settings(
    version := "0.1-SNAPSHOT",
    organization := "org.fusesource.leveldbjni",
    scalaVersion := "2.9.1",
    scalacOptions ++= Seq("-deprecation", "-unchecked"),
    mainTest := "org.fusesource.leveldbjni.LevelDBJNISpec",

    test <<= (streams, fullClasspath in Test, outputStrategy in Test, mainTest) map { (s, cp, os, testName) =>
      val delim = java.io.File.pathSeparator
      val cpStr = cp map { _.data } mkString delim
      s.log.debug("Running with classpath: " + cpStr)
      val opts2 =
        Seq("-classpath", cpStr) ++
        Seq("specs2.run") ++
        Seq(testName)
      val result = Fork.java.fork(None, opts2, None, Map(), false, LoggedOutput(s.log)).exitValue()
      if (result != 0) error("Tests unsuccessful")    // currently has no effect (https://github.com/etorreborre/specs2/issues/55)
    },
    
    resolvers ++= Seq(
      "Local Maven Repository"            at "file://"+Path.userHome.absolutePath+"/.m2/repository"
    ),
    
    libraryDependencies ++= Seq(
      "org.fusesource.leveldbjni" %  "leveldbjni" % "99-master-SNAPSHOT",
      "org.fusesource.leveldbjni" %  "leveldbjni-osx"     % "99-master-SNAPSHOT", // optional(),
//      "org.fusesource.leveldbjni" %  "leveldbjni-linux64" % "99-master-SNAPSHOT" optional(),
      "org.specs2"                %% "specs2"     % "1.8",
      "org.scala-tools.testing"   %% "scalacheck" % "1.9",
      "com.weiglewilczek.slf4s"   %% "slf4s"      % "1.0.7",
      "ch.qos.logback"            %  "logback-classic" % "1.0.0"
    )
  )

  val mainTest = SettingKey[String]("main-test", "The primary test class for the project (just used for pandora)")
}
