import Dependencies._
import sbt._

organization := "cn.playscala"
version := "1.2.0"
name := "play-community"
scalaVersion := Versions.scala

//root 也改下？
lazy val `play-community` = Project(id = "root", base = file(".")).enablePlugins(PlayScala).settings(depend)

resolvers += Resolver.sonatypeRepo("snapshots")

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)