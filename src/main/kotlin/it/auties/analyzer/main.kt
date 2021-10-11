package it.auties.analyzer

import it.auties.whatsapp4j.common.binary.BinaryArray
import it.auties.whatsapp4j.common.binary.BinaryFlag
import it.auties.whatsapp4j.common.protobuf.model.misc.Node
import it.auties.whatsapp4j.common.response.JsonResponse
import it.auties.whatsapp4j.common.response.Response
import it.auties.whatsapp4j.common.utils.CypherUtils.*
import it.auties.whatsapp4j.common.utils.Validate
import it.auties.whatsapp4j.multidevice.binary.BinaryUnpack.unpack
import it.auties.whatsapp4j.multidevice.binary.MultiDeviceMessage
import it.auties.whatsapp4j.standard.binary.BinaryMetric
import it.auties.whatsapp4j.standard.response.UserInformationResponse
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.devtools.DevTools
import org.openqa.selenium.devtools.v90.debugger.Debugger
import org.openqa.selenium.devtools.v90.debugger.model.*
import org.openqa.selenium.devtools.v90.network.Network
import org.openqa.selenium.devtools.v90.network.model.WebSocketFrameReceived
import org.openqa.selenium.devtools.v90.network.model.WebSocketFrameSent
import org.openqa.selenium.devtools.v90.runtime.Runtime
import org.openqa.selenium.devtools.v90.runtime.model.PropertyDescriptor
import org.openqa.selenium.devtools.v90.runtime.model.RemoteObject
import org.openqa.selenium.devtools.v90.runtime.model.RemoteObjectId
import java.nio.ByteBuffer
import java.util.*
import java.util.Optional.empty
import java.util.Optional.of
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

val standardDecoder = it.auties.whatsapp4j.standard.binary.BinaryDecoder()
val mdDecoder = it.auties.whatsapp4j.multidevice.binary.BinaryDecoder()
var multiDevice = false
// A counter isn't the best choice here.
// Technically we should be able to recognize if the key is a write/read key using the scope of the call.
// Chrome doesn't provide a way(or at least I couldn't find it) to find the caller of a lambda function(no name obviously).
// So for now a counter will do the work.
var multiDeviceCounter = 0
lateinit var whatsappKeys: WhatsappKeys

fun main(args: Array<String>) {
    val driver = initializeSelenium()
    val tools = driver.devTools
    tools.createSession()
    tools.send(Debugger.enable(empty()))
    tools.send(Debugger.setBreakpointsActive(true))
    tools.addListener(Debugger.scriptParsed()) { onWhatsappScriptLoaded(tools, it, "keyPair:t,") }
    tools.addListener(Debugger.scriptParsed()) {
        onWhatsappScriptLoaded(
            tools,
            it,
            "return self.crypto.subtle.importKey(\"raw\",new Uint8Array(e)"
        )
    }
    tools.addListener(Debugger.paused()) { onBreakpointTriggered(tools, it) }
    tools.send(Network.enable(empty(), empty(), empty()))
    tools.addListener(Network.webSocketFrameSent()) { onMessageSent(it) }
    tools.addListener(Network.webSocketFrameReceived()) { onMessageReceived(it) }
    driver["https://web.whatsapp.com/"]
}

fun onJsonMessageSent(request: Response<*>) {
    println("Sent JSON Message $request")
}

fun onBinaryMessageSent(tag: String, request: Node, metric: BinaryMetric? = null, flag: BinaryFlag? = null) {
    println("Sent Binary Message(${metric?.name},${flag?.name}) $tag,$request")
}

fun onJsonMessageReceived(response: Response<*>) {
    println("Received JSON Message $response")
    if(response !is JsonResponse){
        return
    }

    if (response.description() == "Conn") {
        initializeKeys(response)
        return
    }

    if (response.description() == "Cmd" && response.content()["type"] == "upgrade_md_prod") {
        println("Switching to multi device beta")
        multiDevice = true
    }
}

fun onBinaryMessageReceived(tag: String, request: Node) {
    println("Received Binary Message $tag,$request")
}

fun onMessageReceived(msg: WebSocketFrameReceived) {
    val payload = msg.response.payloadData
    if (msg.response.opcode.toInt() != 1) {
        decodeBase64BinaryMessage(payload)
        return
    }

    val response = Response.fromTaggedResponse(payload)
    onJsonMessageReceived(response)
}

fun onMessageSent(msg: WebSocketFrameSent) {
    val payload = msg.response.payloadData
    if (msg.response.opcode?.toInt() != 1) {
        decodeBase64BinaryMessage(payload, true)
        return
    }

    val response = JsonResponse.fromJson(payload)
    onJsonMessageSent(response)
}

fun initializeKeys(jsonResponse: JsonResponse) {
    println("Initializing keys $jsonResponse")
    val res = jsonResponse.toModel(UserInformationResponse::class.java)
    val base64Secret = res.secret() ?: return
    val secret = BinaryArray.forBase64(base64Secret)
    val pubKey = secret.cut(32)
    val sharedSecret = calculateSharedSecret(toX509Encoded(pubKey.data()), toPKCS8Encoded(whatsappKeys.privateKey.toByteArray()))
    val sharedSecretExpanded = hkdfExpand(sharedSecret, null,80)
    val hmacValidation = hmacSha256(secret.cut(32).append(secret.slice(64)), sharedSecretExpanded.slice(32, 64))
    Validate.isTrue(hmacValidation == secret.slice(32, 64), "Cannot login: Hmac validation failed!")
    val keysEncrypted = sharedSecretExpanded.slice(64).append(secret.slice(64))
    val key = sharedSecretExpanded.cut(32)
    val keysDecrypted = aesDecrypt(keysEncrypted, key)
    whatsappKeys.encKey = keysDecrypted.cut(32)
}

fun decodeBase64BinaryMessage(payload: String, isRequest: Boolean = false) {
    if (multiDevice) {
        val decodedPayload = Base64.getDecoder().decode(payload)
        val decrypted = decryptMultiDeviceMessage(decodedPayload, isRequest) ?: return
        val unpacked = unpack(decrypted)
        val node = mdDecoder.decode(unpacked)
        if(isRequest){
            onBinaryMessageSent("multi_device", node)
            return
        }

        onBinaryMessageReceived("multi_device", node)
        return
    }

    val binaryMessage = BinaryArray.forBase64(payload)
    val tagAndMessagePair = binaryMessage.indexOf(',').map { binaryMessage.cut(it) to binaryMessage.slice(it) }.orElseThrow()

    val messageTag = tagAndMessagePair.first.toString()
    val messageContent = tagAndMessagePair.second

    val offset = if (isRequest) 2 else 0
    val message = messageContent.slice(32 + offset)
    val decryptedMessage = aesDecrypt(message, whatsappKeys.encKey)
    val whatsappMessage = standardDecoder.decode(decryptedMessage)

    when {
        isRequest -> {
            val metric = toMetric(messageContent.at(0))
            val flag = toFlag(messageContent.at(1))
            onBinaryMessageSent(messageTag, whatsappMessage, metric, flag)
        }

        else -> onBinaryMessageReceived(messageTag, whatsappMessage)
    }
}

fun decryptMultiDeviceMessage(payload: ByteArray, read: Boolean): ByteArray? {
    return runCatching {
        val counter = if(read) whatsappKeys.readCounter++ else whatsappKeys.writeCounter++
        val key = (if (read) whatsappKeys.readKey else whatsappKeys.writeKey) ?: return null
        val secretKey = KeyParameter(key.toByteArray())
        val iv: ByteArray = ByteBuffer.allocate(12).putLong(4, counter).array()
        val cipher = AESEngine()
        cipher.init(false, secretKey)
        val gcm = GCMBlockCipher(cipher)
        val params = AEADParameters(secretKey, 128, iv, null)
        gcm.init(false, params)

        val message = MultiDeviceMessage(payload).decoded()
        val outputLength = gcm.getOutputSize(message.size())
        val output = ByteArray(outputLength)
        val outputOffset = gcm.processBytes(message.data, 0, message.size(), output, 0)
        gcm.doFinal(output, outputOffset)
        output
    }.getOrElse {
        System.err.println("Error: ${it.message}")
        null
    }
}

fun toMetric(byte: Byte): BinaryMetric? {
    return BinaryMetric.values()
        .firstOrNull { it.data() == java.lang.Byte.toUnsignedInt(byte) }
}

fun toFlag(byte: Byte): BinaryFlag? {
    return BinaryFlag.values()
        .firstOrNull { it.data() == byte }
}

fun initializeSelenium(): ChromeDriver {
    System.setProperty("webdriver.chrome.driver", PathUtils.fromJar("chromedriver${determinePlatformExecutable()}"))
    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({}, 0, 1, TimeUnit.MINUTES)
    return ChromeDriver()
}

fun determinePlatformExecutable(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> ".exe"
        os.contains("nix") || os.contains("nux") || os.contains("aix") -> ""
        else -> throw UnsupportedOperationException("Whatsapp request analyzer only works on Windows and Linux")
    }
}

fun onWhatsappScriptLoaded(tools: DevTools, script: ScriptParsed, breakpointCode: String) {
    if (!script.url.contains("https://web.whatsapp.com/bootstrap_qr")) {
        return
    }

    tools.send(Debugger.getScriptSource(script.scriptId))
        .scriptSource
        .lines()
        .mapIndexed { lineNumber, line -> lineNumber to line.indexOf(breakpointCode) }
        .first { it.second != -1 }
        .let { tools.send(Debugger.setBreakpoint(Location(script.scriptId, it.first, of(it.second)), empty())) }
}

fun onBreakpointTriggered(tools: DevTools, paused: Paused) {
    val frame = paused.callFrames.first()
    val local = findKeypairObjectId(frame)
    if(frame.functionName == "t.getOrGenerate") {
        val publicKey = findKeyValue("pubKey", local, tools)
        val privateKey = findKeyValue("privKey", local, tools)
        whatsappKeys = WhatsappKeys(publicKey!!, privateKey!!)
        tools.send(Debugger.resume(empty()))
        return
    }

    val key = findKeyValue("e", local, tools)
    val currentCounter = multiDeviceCounter++
    if (currentCounter == 4 || currentCounter == 10) {
        whatsappKeys.readKey = key!!
        whatsappKeys.readCounter = 0
        tools.send(Debugger.resume(empty()))
        return
    }

    if (currentCounter == 5 || currentCounter == 11) {
        whatsappKeys.writeKey = key!!
        whatsappKeys.writeCounter = 0
        tools.send(Debugger.resume(empty()))
        return
    }

    tools.send(Debugger.resume(empty()))
}

fun findKeypairObjectId(frame: CallFrame): RemoteObjectId = frame.scopeChain
    .first { it.type == Scope.Type.LOCAL }
    .run { getObject().objectId.orElseThrow() }

fun findKeyValue(property: String, id: RemoteObjectId, tools: DevTools): List<Byte>? = tools.send(Runtime.getProperties(id, empty(), empty(), empty()))
    .result
    .firstNotNullOfOrNull { if (it.name == property) extractUnsignedIntArray(tools, it) else findKeyValueDeep(it, property, tools) }

private fun findKeyValueDeep(it: PropertyDescriptor, property: String, tools: DevTools): List<Byte>? = it.value
    .filter { obj -> obj.type == RemoteObject.Type.OBJECT }
    .map { obj -> findKeyValue(property, obj.objectId.orElseThrow(), tools) }
    .orNull()

fun extractUnsignedIntArray(tools: DevTools, prop: PropertyDescriptor): List<Byte> {
    val propValue = prop.value.orNull() ?: return emptyList()
    val propValueId = propValue.objectId.orNull() ?: return emptyList()
    val propsId = findArrayPropertyId(tools, propValueId) ?: return emptyList()
    return tools.send(Runtime.getProperties(propsId, empty(), empty(), empty()))
        .result
        .filter { it.name.toIntOrNull() != null }
        .map { it.value.orElseThrow().value.orElseThrow().toString().toInt().toByte() }
}

private fun findArrayPropertyId(tools: DevTools, propValueId: RemoteObjectId): RemoteObjectId? =
    tools.send(Runtime.getProperties(propValueId, empty(), empty(), empty()))
        .internalProperties
        .orElseThrow()
        .firstOrNull { it.name == "[[Uint8Array]]" }
        ?.run { value.flatMap { it.objectId }.orNull() }

fun <T> Optional<T>.orNull(): T? = this.orElse(null)