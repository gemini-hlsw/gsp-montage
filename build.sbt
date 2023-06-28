import com.typesafe.sbt.packager.docker.Cmd

lazy val attoVersion          = "0.9.5"
lazy val fs2Version           = "3.7.0"
lazy val log4catsVersion      = "2.6.0"
lazy val slf4jVersion         = "2.0.7"
lazy val http4sVersion        = "1.0.0-M38"
lazy val redis4catsVersion    = "1.4.3"

inThisBuild(Seq(
  scalaVersion       := "2.13.11",
  crossScalaVersions := Seq(scalaVersion.value),
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
      "org.typelevel"     %% "log4cats-core"       % log4catsVersion,
      "org.typelevel"     %% "log4cats-slf4j"      % log4catsVersion,
      "org.slf4j"         %  "slf4j-simple"        % slf4jVersion,
      "org.http4s"        %% "http4s-dsl"          % http4sVersion,
      "org.http4s"        %% "http4s-blaze-server" % http4sVersion,
      "org.http4s"        %% "http4s-blaze-client" % http4sVersion,
      "dev.profunktor"    %% "redis4cats-effects"  % redis4catsVersion,
      "dev.profunktor"    %% "redis4cats-log4cats" % redis4catsVersion
    ),
    scalacOptions += "-Yno-predef"
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    // Docker / packageName := "web",
    dockerRepository := Some("registry.heroku.com"),
    dockerUsername   := Some("gemini-2mass-mosaic"),
    Docker / name    := "web",
    dockerAlias      := DockerAlias(
      dockerRepository.value,
      dockerUsername.value,
      (Docker / name).value,
      None
    ),
    dockerCommands   := """

      FROM ubuntu:bionic

      # Install the things we need
      RUN apt-get update
      RUN apt-get install --yes build-essential git libfontconfig openjdk-8-jre

      # Build Montage from master … feelin' lucky.
      RUN git clone https://github.com/Caltech-IPAC/Montage.git
      WORKDIR /Montage
      RUN make
      WORKDIR /
      ENV PATH="${PATH}:/Montage/bin"

      # Set up the Scala app
      WORKDIR /opt/docker
      ADD --chown=daemon:daemon opt /opt
      USER daemon
      CMD /opt/docker/bin/mosaic-server-core -J-Xmx256m

    """.linesIterator.map(_.trim).map(Cmd(_)).toSeq
  )

