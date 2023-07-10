package it.auties.analyzer

import it.auties.whatsapp.util.BytesHelper
import lombok.Value
import lombok.experimental.Accessors
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.util.*


@Value
@Accessors(fluent = true)
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

    private fun decodeLength(input: DataInputStream): Int = runCatching {
        val lengthBytes = ByteArray(3)
        input.readFully(lengthBytes)
        this.decodeLength(lengthBytes)
    }.getOrDefault(-1)

    private fun decodeLength(input: ByteArray): Int {
        val buffer = BytesHelper.newBuffer(input)
        return buffer.readByte().toInt() shl 16 or buffer.readUnsignedShort()
    }
}