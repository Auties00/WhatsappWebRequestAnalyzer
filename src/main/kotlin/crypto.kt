package it.auties.analyzer

import it.auties.whatsapp.binary.BinaryDecoder
import it.auties.whatsapp.crypto.AesGmc
import it.auties.whatsapp.model.request.Node
import org.openqa.selenium.devtools.v114.network.model.WebSocketFrameReceived
import org.openqa.selenium.devtools.v114.network.model.WebSocketFrameSent
import java.util.Base64
import kotlin.math.max

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
        MessageWrapper(Base64.getDecoder().decode(payload))
    }.getOrNull() ?: return

    val counter = if(request) whatsappKeys.writeIv.getAndIncrement() else whatsappKeys.readIv.getAndIncrement()
    val node = whatsappKeys.keys.copy()
        .firstNotNullOfOrNull { decodeNodeOrFallback(message, counter, it, request) }
    if (node != null) {
        return
    }

    println("Cannot decode node")
}

private fun decodeNodeOrFallback(
    message: MessageWrapper,
    counter: Long,
    key: ByteArray,
    request: Boolean
): Node? {
    val result = tryDecodeNode(message, counter, key, request)
    if (result != null) {
        return result
    }

    // Try to rescue the message, there is obviously a better way, but it works and performs well enough
    val lowerBound = max(counter - 10, 0)
    val upperBound = counter + 10
    return (lowerBound..upperBound)
        .firstNotNullOfOrNull { tryDecodeNode(message, it, key, request) }
}

private fun tryDecodeNode(message: MessageWrapper, counter: Long, key: ByteArray, request: Boolean): Node? =
    message.decoded.firstNotNullOfOrNull { decoded -> decodeNode(counter, decoded, key, request) }

private fun decodeNode(counter: Long, decoded: ByteArray, key: ByteArray, request: Boolean) = runCatching {
    val plainText = AesGmc.decrypt(counter, decoded, key)
    val decoder = BinaryDecoder()
    val node = decoder.decode(plainText)
    if (request) {
        onMessageSent(node)
    } else {
        onMessageReceived(node)
    }

    node
}.getOrNull()