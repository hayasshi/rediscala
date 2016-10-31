import sbt.Keys._
import com.typesafe.sbt.SbtGhPages._
import com.typesafe.sbt.SbtGhPages.GhPagesKeys._
import com.typesafe.sbt.SbtGit.{GitKeys => git}
import com.typesafe.sbt.SbtSite._
import com.typesafe.sbt.SbtSite.SiteKeys.siteMappings
import sbt.LocalProject
import sbt.Tests.{InProcess, Group}

val resolversList = Seq(
    "Typesafe repository snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
    "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/"
)

val rediscalaDependencies = {
  val akkaVersion = "2.4.10"

  import sbt._

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion

  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion

  val specs2 = "org.specs2" %% "specs2" % "2.3.13"

  val stm = "org.scala-stm" %% "scala-stm" % "0.7"

  val scalacheck = "org.scalacheck" %% "scalacheck" % "1.12.5"

  //val scalameter = "com.github.axel22" %% "scalameter" % "0.4"

  Seq(
    akkaActor,
    stm,
    akkaTestkit % "test",
    //scalameter % "test",
    specs2 % "test",
    scalacheck % "test"
  )
}

val baseSourceUrl = "https://github.com/etaty/rediscala/tree/"


lazy val standardSettings = Defaults.coreDefaultSettings ++
  Seq(
    name := "rediscala",
    organization := "com.github.etaty",
    scalaVersion := "2.11.8",
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
    homepage := Some(url("https://github.com/non/rediscala")),
    scmInfo := Some(ScmInfo(url("https://github.com/etaty/rediscala"), "scm:git:git@github.com:etaty/rediscala.git")),
    apiURL := Some(url("http://etaty.github.io/rediscala/latest/api/")),
    pomExtra := (
      <developers>
        <developer>
          <id>etaty</id>
          <name>Valerian Barbot</name>
          <url>http://github.com/etaty/</url>
        </developer>
      </developers>
      ),
    resolvers ++= resolversList,

    publishMavenStyle := true,
    git.gitRemoteRepo := "git@github.com:etaty/rediscala.git",

    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-Xlint",
      "-deprecation",
      "-Xfatal-warnings",
      "-feature",
      "-language:postfixOps",
      "-unchecked"
    ),
    scalacOptions in (Compile, doc) ++= Seq("-sourcepath", (baseDirectory in LocalProject("rediscala")).value.getAbsolutePath),
    autoAPIMappings := true,
    apiURL := Some(url("http://etaty.github.io/rediscala/")),
    scalacOptions in (Compile, doc) ++= {
      val ver = (version in LocalProject("rediscala")).value
      val branch = if(ver.trim.endsWith("SNAPSHOT")) "master" else ver
      Seq[String](
        "-doc-source-url", baseSourceUrl + branch +"€{FILE_PATH}.scala"
      )
    }
) ++ site.settings ++ ghpages.settings ++ Seq(
    siteMappings ++= {
      (mappings in packageDoc in Compile).value.map{
        case (f, d) => (f, (version in LocalProject("rediscala")).value + "/api/" + d)
      }
    },
    cleanSite := {
      val dir = updatedRepository.value
      val gitRun = git.gitRunner.value
      val s = streams.value
      val v = (version in LocalProject("rediscala")).value
      val toClean = IO.listFiles(dir).filter{ f =>
        val p = f.getName
        p.startsWith("latest") || p.startsWith(v)
      }.map(_.getAbsolutePath).toList
      if(toClean.nonEmpty)
        gitRun(("rm" :: "-r" :: "-f" :: "--ignore-unmatch" :: toClean) :_*)(dir, s.log)
      ()
    },
    synchLocal := {
      val clean = cleanSite.value
      val repo = updatedRepository.value
      val s = streams.value
      // TODO - an sbt.Synch with cache of previous mappings to make this more efficient. */
      val betterMappings = privateMappings.value map { case (file, target) => (file, repo / target) }
      // First, remove 'stale' files.
      //cleanSite.
      // Now copy files.
      IO.copy(betterMappings)
      if (ghpagesNoJekyll.value) IO.touch(repo / ".nojekyll")
      repo
    }
) ++ site.includeScaladoc("latest/api")

lazy val BenchTest = config("bench") extend Test

lazy val benchTestSettings = inConfig(BenchTest)(Defaults.testSettings ++ Seq(
  sourceDirectory in BenchTest := baseDirectory.value / "src/benchmark",
  //testOptions in BenchTest += Tests.Argument("-preJDK7"),
  testFrameworks in BenchTest := Seq(new TestFramework("org.scalameter.ScalaMeterFramework")),

  //https://github.com/sbt/sbt/issues/539 => bug fixed in sbt 0.13.x
  testGrouping in BenchTest := partitionTests((definedTests in BenchTest).value)
))

lazy val root = Project(id = "rediscala",
  base = file("."),
  settings = standardSettings ++ Seq(
    libraryDependencies ++= rediscalaDependencies
  )
).configs(BenchTest)
  //.settings(benchTestSettings: _* )

lazy val benchmark = {
  import pl.project13.scala.sbt.JmhPlugin

  Project(
    id = "benchmark",
    base = file("benchmark")
  ).settings(Seq(
    scalaVersion := "2.11.8",
    libraryDependencies += "net.debasishg" %% "redisclient" % "3.0"
  ))
    .enablePlugins(JmhPlugin)
    .dependsOn(root)
}

def partitionTests(tests: Seq[TestDefinition]) = {
  Seq(new Group("inProcess", tests, InProcess))
}
