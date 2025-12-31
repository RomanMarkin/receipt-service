package com.betgol.receipt.integration

import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.test.*


trait BasicIntegrationSpec extends ZIOSpecDefault {
  // Setup config provider for reading test application.conf
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    testEnvironment ++ Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())
}