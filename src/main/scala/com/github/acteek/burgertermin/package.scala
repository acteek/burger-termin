package com.github.acteek

import cats.effect.IO

package object burgertermin {

  val baseUrl = "https://service.berlin.de"

  val burgerms = List(
    122210, 122217, 327316, 122219, 327312, 122227, 327314, 122231, 327346, 122243, 327348, 122254, 122252, 329742,
    122260, 329745, 122262, 329748, 122271, 327278, 122273, 327274, 122277, 327276, 330436, 122280, 327294, 122282,
    327290, 122284, 327292, 122291, 327270, 122285, 327266, 122286, 327264, 122296, 327268, 150230, 329760, 122301,
    327282, 122297, 327286, 122294, 327284, 122312, 329763, 122314, 329775, 122304, 327330, 122311, 327334, 122309,
    327332, 317869, 122281, 327352, 122279, 329772, 122283, 122276, 327324, 122274, 327326, 122267, 329766, 122246,
    327318, 122251, 327320, 122257, 327322, 122208, 327298, 122226, 327300
  )

  implicit val log: Logging = new Logging {
    def debug(msg: String): IO[Unit] = IO.println(s"DEBUG | $msg")
    def info(msg: String): IO[Unit] = IO.println(s"INFO | $msg")
    def warn(msg: String): IO[Unit] = IO.println(s"WARN | $msg")
    def error(msg: String): IO[Unit] = IO.println(s"ERROR | $msg")
  }
}
