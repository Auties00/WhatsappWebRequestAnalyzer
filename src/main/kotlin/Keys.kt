package it.auties.analyzer

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

data class Keys(
    val keys: CopyOnWriteArrayList<ByteArray> = CopyOnWriteArrayList(),
    val readIv: AtomicLong = AtomicLong(),
    val writeIv: AtomicLong = AtomicLong(),
)