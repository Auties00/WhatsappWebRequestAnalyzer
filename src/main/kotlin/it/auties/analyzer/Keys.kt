package it.auties.analyzer

import it.auties.bytes.Bytes
import java.util.concurrent.atomic.AtomicLong

data class Keys(
    val keys: ArrayList<Bytes> = ArrayList(),
    val readIv: AtomicLong = AtomicLong(),
    val writeIv: AtomicLong = AtomicLong(),
)