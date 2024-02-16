package it.auties.analyzer

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.*


class MessageWrapper(raw: ByteArray) {
    var decoded: LinkedList<ByteArray> = LinkedList<ByteArray>()

    init {
        val stream = DataInputStream(ByteArrayInputStream(raw))
        while (stream.available() > 3) {
            val length = decodeLength(stream)
            if (length > 0) {
                val message = ByteArray(length)
                stream.readFully(message)
                decoded.add(message)
            }
        }
    }

    private fun decodeLength(input: DataInputStream): Int {
        val lengthBytes = ByteArray(3)
        input.readFully(lengthBytes)
        return (lengthBytes[0].toInt() and 0xFF shl 16) or
                (lengthBytes[1].toInt() and 0xFF shl 8) or
                (lengthBytes[2].toInt() and 0xFF)
    }
}