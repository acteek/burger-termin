import com.typesafe.sbt.packager.docker._

val Http4sVersion          = "0.23.19"
val CirceVersion           = "0.14.5"
val MunitVersion           = "0.7.29"
val LogbackVersion         = "1.2.11"
val MunitCatsEffectVersion = "1.0.7"

lazy val runJavaOptions = Seq(
    "-Xss512k"
  , "-XX:+UseG1GC"
  , "-XX:+ExitOnOutOfMemoryError"
  , "-XX:InitialRAMPercentage=70"
  , "-XX:MaxRAMPercentage=70"
)

lazy val root = (project in file("."))
  .settings(
      organization     := "com.github.acteek"
    , name             := "burger-termin"
    , scalaVersion     := "2.13.12"
    , run / fork       := true
    , dockerApiVersion := Some(DockerApiVersion(1, 40))
    , dockerBaseImage  := "openjdk:21-slim"
    , dockerUsername   := Some("acteek")
    , dockerRepository := Some("registry.digitalocean.com")
    , dockerExposedPorts ++= Seq(1099)
    , dockerUpdateLatest      := true
    , dockerEnvVars           := Map("JAVA_OPTS" -> runJavaOptions.mkString(" "))
    , dockerEntrypoint        := Seq(s"/opt/docker/bin/${packageName.value}")
    , Docker / daemonUser     := "boris"
    , Docker / daemonUserUid  := Some("1000")
    , Docker / daemonGroup    := "boris"
    , Docker / daemonGroupGid := Some("1000")
    , dockerChmodType         := DockerChmodType.UserGroupWriteExecute
    , scalacOptions ++= Seq(
        "-deprecation" // Emit warning and location for usages of deprecated APIs.
      , "-encoding"
      , "utf-8"         // Specify character encoding used by source files.
      , "-explaintypes" // Explain type errors in more detail.
      , "-feature"      // Emit warning and location for usages of features that should be imported explicitly.
      , "-unchecked"    // Enable additional warnings where generated code depends on assumptions.
      , "-Xcheckinit"
      , "-Wvalue-discard" // Warn when non-Unit expression results are unused.
      , "-language:implicitConversions"
      , "-language:higherKinds"
      , "-Ymacro-annotations"
      , "-language:existentials"
    )
    , libraryDependencies ++= Seq(
        "org.http4s"                    %% "http4s-ember-server"           % Http4sVersion
      , "org.http4s"                    %% "http4s-ember-client"           % Http4sVersion
      , "org.http4s"                    %% "http4s-circe"                  % Http4sVersion
      , "org.http4s"                    %% "http4s-dsl"                    % Http4sVersion
      , "io.circe"                      %% "circe-generic"                 % CirceVersion
      , "org.scalameta"                 %% "munit"                         % MunitVersion           % Test
      , "org.typelevel"                 %% "munit-cats-effect-3"           % MunitCatsEffectVersion % Test
      , "net.ruippeixotog"              %% "scala-scraper"                 % "3.1.0"
      , "com.bot4s"                     %% "telegram-core"                 % "5.7.0"
      , "co.fs2"                        %% "fs2-core"                      % "3.9.2"
      , "org.typelevel"                 %% "cats-effect"                   % "3.5.2"
      , "com.softwaremill.sttp.client3" %% "core"                          % "3.8.13"
      , "com.softwaremill.sttp.client3" %% "fs2"                           % "3.8.13"
      , "com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2" % "3.8.13"
      , "dev.profunktor"                %% "redis4cats-effects"            % "1.5.0"
      , "dev.profunktor"                %% "redis4cats-log4cats"           % "1.5.0"
      , "org.typelevel"                 %% "log4cats-core"                 % "2.6.0"
      , "org.typelevel"                 %% "log4cats-slf4j"                % "2.6.0"
      , "com.github.pureconfig"         %% "pureconfig"                    % "0.17.1"
    )
    , addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.13.2" cross CrossVersion.full)
    , addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
    , testFrameworks += new TestFramework("munit.Framework")
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
