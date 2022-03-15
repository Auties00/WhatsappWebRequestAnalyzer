package it.auties.analyzer

import it.auties.bytes.Bytes.ofBase64
import it.auties.whatsapp.binary.BinaryDecoder
import it.auties.whatsapp.binary.BinaryMessage
import it.auties.whatsapp.crypto.AesGmc
import org.openqa.selenium.devtools.v97.network.model.WebSocketFrameReceived
import org.openqa.selenium.devtools.v97.network.model.WebSocketFrameSent

private val decoder = BinaryDecoder()

fun handleReceivedMessage(msg: WebSocketFrameReceived) {
    val payload = msg.response.payloadData
    if (msg.response.opcode.toInt() != 2) {
        return
    }

    handleBinaryMessage(payload, false)
}

fun handleSentMessage(msg: WebSocketFrameSent) {
    val payload = msg.response.payloadData
    if (msg.response.opcode?.toInt() != 2) {
        return
    }

    handleBinaryMessage(payload, true)
}

private fun handleBinaryMessage(payload: String, request: Boolean) {
    val message = runCatching {
        BinaryMessage(ofBase64(payload))
    }.getOrNull() ?: return

    val counter = if(request) whatsappKeys.writeIv.getAndIncrement() else whatsappKeys.readIv.getAndIncrement()
    val node = ArrayList(whatsappKeys.keys).firstNotNullOfOrNull { key ->
        message.decoded().firstNotNullOfOrNull { decoded ->
            runCatching {
                val plainText = AesGmc.with(key, counter, false)
                    .process(decoded.toByteArray())
                val node = decoder.decode(plainText)
                if(request) {
                    onMessageSent(node)
                }else {
                    onMessageReceived(node)
                }

                node
            }.getOrNull()
        }
    }

    if (node != null) {
        return
    }

    println("Cannot decode node")
}