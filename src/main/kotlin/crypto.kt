package it.auties.analyzer

import it.auties.whatsapp.binary.BinaryDecoder
import it.auties.whatsapp.crypto.AesGcm
import it.auties.whatsapp.model.node.Node
import org.openqa.selenium.devtools.v124.network.model.WebSocketFrameReceived
import org.openqa.selenium.devtools.v124.network.model.WebSocketFrameSent
import java.util.*
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
    for (key in whatsappKeys.keys) {
        val result = decodeNodes(message, counter, key, request)
        if(result.isNotEmpty()){
            return
        }
    }
    println("Cannot decode node")
}

private fun decodeNodes(
    wrapper: MessageWrapper,
    counter: Long,
    key: ByteArray,
    request: Boolean
): List<Node> = wrapper.decoded
    .mapNotNull { tryDecodeNode(counter, it, key, request) }
    .toList()

private fun tryDecodeNode(
    counter: Long,
    it: ByteArray,
    key: ByteArray,
    request: Boolean
): Node? {
    val result = decodeNode(counter, it, key, request)
    if (result != null) {
        return result
    }

    // Try to rescue the message, there is obviously a better way, but it works and performs well enough
    val lowerBound = max(counter - 10, 0)
    val upperBound = counter + 10
    return (lowerBound..upperBound)
        .firstNotNullOfOrNull { hypotheticalCounter -> decodeNode(hypotheticalCounter, it, key, request) }
}

private fun decodeNode(counter: Long, decoded: ByteArray, key: ByteArray, request: Boolean) = runCatching {
    val plainText = AesGcm.decrypt(counter, decoded, key)
    val decoder = BinaryDecoder(plainText)
    val node = decoder.decode()
    if (request) {
        onMessageSent(node)
    } else {
        onMessageReceived(node)
    }

    node
}.getOrNull()