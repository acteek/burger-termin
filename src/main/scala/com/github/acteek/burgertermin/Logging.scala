package com.github.acteek.burgertermin

import cats.effect.IO
import org.slf4j
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.slf4j.LoggerFactory

trait Logging {

  protected val slf4jLogger: slf4j.Logger = LoggerFactory.getLogger(getClass.getName.replace("$", ""))

  implicit val log: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLoggerFromSlf4j[IO](slf4jLogger)

}
