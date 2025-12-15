package com.betgol.receipt.infrastructure.services

import com.betgol.receipt.domain.services.IdGenerator
import com.github.f4b6a3.uuid.UuidCreator
import zio.{UIO, ZIO, ZLayer}


case class UuidV7IdGenerator() extends IdGenerator {
  override def generate: UIO[String] = ZIO.succeed {
    UuidCreator.getTimeOrderedEpoch().toString
  }
}

object UuidV7IdGenerator {
  val layer: ZLayer[Any, Nothing, IdGenerator] =
    ZLayer.succeed(UuidV7IdGenerator())
}