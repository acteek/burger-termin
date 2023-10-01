package com.github.acteek.burgertermin

import cats.effect.IO
import io.circe.{Encoder, Json}
import io.circe.generic.JsonCodec
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderException
import pureconfig.generic.auto._

import scala.concurrent.duration.FiniteDuration

@JsonCodec(encodeOnly = true)
final case class Config(
      redis: Config.Redis
    , telegram: Config.Telegram
    , updatePeriod: FiniteDuration
)

object Config {

  implicit val encoderFiniteDuration: Encoder[FiniteDuration] =
    Encoder.instance[FiniteDuration](d => Json.fromString(d.toString))

  @JsonCodec(encodeOnly = true)
  final case class Redis(
        host: String
      , port: Int
      , pass: String
  )

  @JsonCodec(encodeOnly = true)
  final case class Telegram(token: String)

  def load: IO[Config] = IO.fromEither(
    ConfigSource.default.load[Config].left.map(err => ConfigReaderException[Config](err))
  )
}
