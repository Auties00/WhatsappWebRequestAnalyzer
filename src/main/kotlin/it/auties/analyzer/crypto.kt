package it.auties.analyzer

import it.auties.whatsapp.binary.BinaryMessage
import it.auties.whatsapp4j.common.binary.BinaryArray.*
import it.auties.whatsapp4j.common.response.JsonResponse
import it.auties.whatsapp4j.common.response.Response
import it.auties.whatsapp4j.common.utils.CypherUtils
import it.auties.whatsapp4j.common.utils.Validate
import it.auties.whatsapp4j.standard.response.UserInformationResponse
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.openqa.selenium.devtools.v93.network.model.WebSocketFrameReceived
import org.openqa.selenium.devtools.v93.network.model.WebSocketFrameSent
import java.nio.ByteBuffer

private val standardDecoder = it.auties.whatsapp4j.standard.binary.BinaryDecoder()
private val mdDecoder = it.auties.whatsapp.binary.BinaryDecoder()

fun handleReceivedMessage(msg: WebSocketFrameReceived) {
    val payload = msg.response.payloadData
    if (msg.response.opcode.toInt() != 1) {
        handleBinaryMessage(payload)
        return
    }

    val response = Response.fromTaggedResponse(payload)
    handleJsonMessage(response)
    onJsonMessageReceived(response)
}

fun handleSentMessage(msg: WebSocketFrameSent) {
    val payload = msg.response.payloadData
    if (msg.response.opcode?.toInt() != 1) {
        handleBinaryMessage(payload, true)
        return
    }

    val response = JsonResponse.fromJson(payload)
    onJsonMessageSent(response)
}

private fun handleJsonMessage(response: Response<*>) {
    if (response !is JsonResponse) {
        return
    }

    when (response.description()) {
        "Conn" -> initializeKeys(response)
        "Cmd" -> switchToMultiDevice(response)
    }
}

private fun handleBinaryMessage(payload: String, request: Boolean = false) {
    if (whatsappKeys.multiDevice) {
        handleMultiDeviceBinaryMessage(payload, request)
        return
    }

    handleStandardBinaryMessage(payload, request)
}

private fun handleStandardBinaryMessage(payload: String, request: Boolean) {
    val binaryMessage = forBase64(payload)
    val tagAndMessagePair =
        binaryMessage.indexOf(',').map { binaryMessage.cut(it) to binaryMessage.slice(it) }.orThrow()

    val messageTag = tagAndMessagePair.first.toString()
    val messageContent = tagAndMessagePair.second

    val offset = if (request) 2 else 0
    val message = messageContent.slice(32 + offset)
    val decryptedMessage = CypherUtils.aesDecrypt(message, whatsappKeys.standardReadKey)
    val whatsappMessage = standardDecoder.decode(decryptedMessage)

    when {
        request -> {
            val metric = toMetric(messageContent.at(0))
            val flag = toFlag(messageContent.at(1))
            onBinaryMessageSent(messageTag, whatsappMessage, metric, flag)
        }

        else -> onBinaryMessageReceived(messageTag, whatsappMessage)
    }
}

private fun handleMultiDeviceBinaryMessage(payload: String, request: Boolean) {
    val counter = whatsappKeys.incrementIO(request)
    val node = whatsappKeys.multiDeviceKeys.firstNotNullOfOrNull {
        runCatching {
            val secretKey = KeyParameter(it.toByteArray())
            val iv = ByteBuffer.allocate(12).putLong(4, counter).array()
            val cipher = AESEngine()
            cipher.init(false, secretKey)
            val gcm = GCMBlockCipher(cipher)
            val params = AEADParameters(secretKey, 128, iv, null)
            gcm.init(false, params)

            val message = BinaryMessage(forBase64(payload).data).decoded()
            val outputLength = gcm.getOutputSize(message.size())
            val output = ByteArray(outputLength)
            val outputOffset = gcm.processBytes(message.data, 0, message.size(), output, 0)
            gcm.doFinal(output, outputOffset)
            val unpacked = mdDecoder.unpack(output)
            mdDecoder.decode(unpacked)
        }.getOrNull()
    }

    if (node == null) {
        System.err.println("Cannot decode $payload with message($request)")
        return
    }

    when {
        request -> onBinaryMessageSent(content = node)
        else -> onBinaryMessageReceived(content = node)
    }
}

private fun initializeKeys(jsonResponse: JsonResponse) {
    val res = jsonResponse.toModel(UserInformationResponse::class.java)
    val base64Secret = res.secret() ?: return
    val secret = forBase64(base64Secret)
    val pubKey = secret.cut(32)
    val sharedSecret = CypherUtils.calculateSharedSecret(
        CypherUtils.toX509Encoded(pubKey.data()),
        CypherUtils.toPKCS8Encoded(whatsappKeys.standardPrivateKey.toByteArray())
    )
    val sharedSecretExpanded = CypherUtils.hkdfExpand(sharedSecret, null, 80)
    val hmacValidation =
        CypherUtils.hmacSha256(secret.cut(32).append(secret.slice(64)), sharedSecretExpanded.slice(32, 64))
    Validate.isTrue(hmacValidation == secret.slice(32, 64), "Cannot login: Hmac validation failed!")
    val keysEncrypted = sharedSecretExpanded.slice(64).append(secret.slice(64))
    val key = sharedSecretExpanded.cut(32)
    val keysDecrypted = CypherUtils.aesDecrypt(keysEncrypted, key)
    whatsappKeys.standardReadKey = keysDecrypted.cut(32)
}
