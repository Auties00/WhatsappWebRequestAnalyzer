package it.auties.analyzer

import java.util.concurrent.atomic.AtomicLong

data class Keys(
    val keys: ArrayList<ByteArray> = ArrayList(),
    val readIv: AtomicLong = AtomicLong(),
    val writeIv: AtomicLong = AtomicLong(),
)