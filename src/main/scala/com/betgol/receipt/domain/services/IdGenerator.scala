package com.betgol.receipt.domain.services

import zio.UIO


trait IdGenerator {
  def generate: UIO[String]
}