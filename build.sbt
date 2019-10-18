import com.typesafe.sbt.packager.docker.Cmd

lazy val kindProjectorVersion = "0.11.0"
lazy val attoVersion          = "0.7.1"
lazy val fs2Version           = "2.0.1"
lazy val log4catsVersion      = "1.0.1"
lazy val slf4jVersion         = "1.7.28"
lazy val http4sVersion        = "0.21.0-M5"

inThisBuild(Seq(
  scalaVersion       := "2.13.1",
  crossScalaVersions := Seq(scalaVersion.value),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % kindProjectorVersion cross CrossVersion.full)
))

lazy val mosaic_server = project.in(file("."))
  .dependsOn(core)
  .aggregate(core)

lazy val core = project
  .in(file("modules/core"))
  .settings(
    name        := "mosaic-server-core",
    description := "Mosaic image server based on Montage.",
    libraryDependencies ++= Seq(
      "org.tpolecat"      %% "atto-core"           % attoVersion,
      "co.fs2"            %% "fs2-core"            % fs2Version,
      "io.chrisdavenport" %% "log4cats-core"       % log4catsVersion,
      "io.chrisdavenport" %% "log4cats-slf4j"      % log4catsVersion,
      "org.slf4j"         %  "slf4j-simple"        % slf4jVersion,
      "org.http4s"        %% "http4s-dsl"          % http4sVersion,
      "org.http4s"        %% "http4s-blaze-server" % http4sVersion,
      "org.http4s"        %% "http4s-blaze-client" % http4sVersion
    ),
    scalacOptions += "-Yno-predef"
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    Docker / packageName := "web",
    dockerRepository := Some("registry.heroku.com"),
    dockerUsername   := Some("gemini-2mass-mosaic"),
    name in Docker   := "web",
    dockerAlias      := DockerAlias(
      dockerRepository.value,
      dockerUsername.value,
      (name in Docker).value,
      None
    ),
    dockerCommands   := """

      FROM ubuntu:bionic

      # Install the things we need
      RUN apt-get update
      RUN apt-get install --yes build-essential git libfontconfig openjdk-8-jre

      # Build Montage from Rob's fork and add it to the path
      RUN git clone -b mArchiveList-segfault https://github.com/tpolecat/Montage.git
      WORKDIR /Montage
      RUN make
      WORKDIR /
      ENV PATH="${PATH}:/Montage/bin"

      # Set up the Scala app
      WORKDIR /opt/docker
      ADD --chown=daemon:daemon opt /opt
      USER daemon
      CMD /opt/docker/bin/mosaic-server-core

    """.lines.map(_.trim).map(Cmd(_)).toSeq
  )

