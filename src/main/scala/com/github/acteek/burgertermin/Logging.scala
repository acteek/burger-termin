package com.github.acteek.burgertermin

import cats.effect.IO
trait Logging {

  def debug(msg: String): IO[Unit]
  def info(msg: String): IO[Unit]
  def warn(msg: String): IO[Unit]
  def error(msg: String): IO[Unit]

}
