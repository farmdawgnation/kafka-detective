package me.frmr.kafka.testtool

case class MessageBatch(
  delayMs: Int,
  messages: Seq[(Array[Byte], Array[Byte])]
)
