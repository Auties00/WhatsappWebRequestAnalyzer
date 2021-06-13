package it.auties.whatsapp4j

import it.auties.whatsapp4j.binary.BinaryArray
import it.auties.whatsapp4j.binary.BinaryDecoder
import it.auties.whatsapp4j.binary.BinaryFlag
import it.auties.whatsapp4j.binary.BinaryMetric
import it.auties.whatsapp4j.model.WhatsappNode
import it.auties.whatsapp4j.model.WhatsappResponse
import it.auties.whatsapp4j.response.impl.UserInformationResponse
import it.auties.whatsapp4j.response.model.JsonResponse
import it.auties.whatsapp4j.utils.CypherUtils
import it.auties.whatsapp4j.utils.Validate
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
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

val decoder = BinaryDecoder()
lateinit var whatsappKeys: WhatsappKeys

fun main(args: Array<String>) {
    val driver = initializeSelenium()
    val tools = driver.devTools
    tools.createSession()
    tools.send(Debugger.enable(Optional.empty()))
    tools.send(Debugger.setBreakpointsActive(true))
    tools.addListener(Debugger.scriptParsed()) { onWhatsappScriptLoaded(tools, it) }
    tools.addListener(Debugger.paused()) { onBreakpointTriggered(tools, it) }
    tools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()))
    tools.addListener(Network.webSocketFrameSent()) { onMessageSent(it) }
    tools.addListener(Network.webSocketFrameReceived()) { onMessageReceived(it) }
    driver["https://web.whatsapp.com/"]
}

fun onJsonMessageSent(request: WhatsappResponse){
    println("Sent JSON Message $request")
}

fun onBinaryMessageSent(tag: String, request: WhatsappNode, metric: BinaryMetric?, flag: BinaryFlag?) {
    println("Sent Binary Message(${metric?.name},${flag?.name}) $tag,$request")
}

fun onJsonMessageReceived(request: WhatsappResponse){
    println("Received JSON Message $request")
}

fun onBinaryMessageReceived(tag: String, request: WhatsappNode){
    println("Received Binary Message $tag,$request")
}

fun onMessageReceived(msg: WebSocketFrameReceived) {
    val payload = msg.response.payloadData
    if (msg.response.opcode.toInt() == 1) {
        val response = WhatsappResponse.fromJson(payload)
        onJsonMessageReceived(response)
        if (response.description() == "Conn") initializeKeys(response.data() as JsonResponse)
        return
    }

    decodeBase64EncodedBinaryMessage(payload)
}

fun onMessageSent(msg: WebSocketFrameSent) {
    val payload = msg.response.payloadData
    if (msg.response.opcode?.toInt() == 1) {
        val response = WhatsappResponse.fromJson(payload)
        onJsonMessageSent(response)
        return
    }

    decodeBase64EncodedBinaryMessage(payload, true)
}

fun initializeKeys(jsonResponse: JsonResponse) {
    val res = jsonResponse.toModel(UserInformationResponse::class.java)
    val base64Secret = res.secret() ?: return
    val secret = BinaryArray.forBase64(base64Secret)
    val pubKey = secret.cut(32)
    val sharedSecret = CypherUtils.calculateSharedSecret(pubKey.data(), whatsappKeys.privateKey.toByteArray())
    val sharedSecretExpanded = CypherUtils.hkdfExpand(sharedSecret, 80)
    val hmacValidation = CypherUtils.hmacSha256(secret.cut(32).merged(secret.slice(64)), sharedSecretExpanded.slice(32, 64))
    Validate.isTrue(hmacValidation == secret.slice(32, 64), "Cannot login: Hmac validation failed!")
    val keysEncrypted = sharedSecretExpanded.slice(64).merged(secret.slice(64))
    val key = sharedSecretExpanded.cut(32)
    val keysDecrypted = CypherUtils.aesDecrypt(keysEncrypted, key)
    whatsappKeys.encKey = keysDecrypted.cut(32)
}

fun decodeBase64EncodedBinaryMessage(payload: String, isRequest: Boolean = false) {
    val binaryMessage = BinaryArray.forBase64(payload)
    val tagAndMessagePair = binaryMessage.indexOf(',').map { binaryMessage.split(it!!) }.orElseThrow()

    val messageTag = tagAndMessagePair.first.toString()
    val messageContent = tagAndMessagePair.second

    val offset = if(isRequest) 2 else 0;
    val message = messageContent.slice(32 + offset)
    val decryptedMessage = CypherUtils.aesDecrypt(message, whatsappKeys.encKey)
    val whatsappMessage = decoder.decodeDecryptedMessage(decryptedMessage)

    when {
        isRequest -> {
            val metric = toMetric(messageContent.at(0))
            val flag = toFlag(messageContent.at(1))
            onBinaryMessageSent(messageTag, whatsappMessage, metric, flag)
        }

        else -> onBinaryMessageReceived(messageTag, whatsappMessage)
    }
}

fun toMetric(byte: Byte): BinaryMetric? {
    return BinaryMetric.values().firstOrNull { it.data() == java.lang.Byte.toUnsignedInt(byte) }
}

fun toFlag(byte: Byte): BinaryFlag? {
    return BinaryFlag.values().first { it.data() == byte }
}

fun initializeSelenium(): ChromeDriver {
    System.setProperty("webdriver.chrome.driver", PathUtils.fromJar("chromedriver${determinePlatformExecutable()}"))
    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({}, 0, 1, TimeUnit.MINUTES)
    return ChromeDriver()
}

fun determinePlatformExecutable(): String {
    val os = System.getProperty("os.name").toLowerCase()
    return when {
        os.contains("win") -> ".exe"
        os.contains("nix") || os.contains("nux") || os.contains("aix") -> ""
        else -> throw UnsupportedOperationException("Whatsapp request analyzer only works on Windows and Linux")
    }
}

fun onWhatsappScriptLoaded(tools: DevTools, script: ScriptParsed) {
    if (!script.url.contains("https://web.whatsapp.com/bootstrap_qr")) {
        return
    }

    val breakpointPos = tools.send(Debugger.getScriptSource(script.scriptId)).scriptSource.indexOf("keyPair:t,")
    if(breakpointPos == -1){
        throw RuntimeException("Cannot find breakpoint position")
    }

    tools.send(Debugger.setBreakpoint(Location(script.scriptId, 0, Optional.of(breakpointPos)), Optional.empty()))
}

fun onBreakpointTriggered(tools: DevTools, paused: Paused) {
    val frame = findKeypairFrame(paused)
    val local = findKeypairObjectId(frame)
    val publicKey = findKeyValue("pubKey", local, tools)
    val privateKey = findKeyValue("privKey", local, tools)
    whatsappKeys = WhatsappKeys(publicKey!!, privateKey!!)
    tools.send(Debugger.resume(Optional.empty()))
}

fun findKeypairObjectId(frame: CallFrame): RemoteObjectId {
    return frame.scopeChain
        .first { it.type == Scope.Type.LOCAL }
        .run { getObject().objectId.orElseThrow() }
}

fun findKeypairFrame(paused: Paused): CallFrame {
    return paused.callFrames.firstOrNull { it.functionName == "t.getOrGenerate" } ?: paused.callFrames[0]
}

fun findKeyValue(property: String, id: RemoteObjectId, tools: DevTools): List<Byte>? {
    tools.send(Runtime.getProperties(id, Optional.empty(), Optional.empty(), Optional.empty()))
        .result
        .forEach {
            return if (it.name == property) extractUnsignedIntArray(tools, it) else it.value
                .filter { obj -> obj.type == RemoteObject.Type.OBJECT }
                .map { obj -> findKeyValue(property, obj.objectId.orElseThrow(), tools) }
                .orElse(null) ?: return@forEach
        }

    return null
}

fun extractUnsignedIntArray(tools: DevTools, prop: PropertyDescriptor): List<Byte> {
    val propsId = tools.send(Runtime.getProperties(prop.value.orElseThrow().objectId.orElseThrow(), Optional.empty(), Optional.empty(), Optional.empty()))
        .internalProperties
        .orElseThrow()
        .first { it.name == "[[Uint8Array]]" }
        .run { value.map { it.objectId.orElseThrow() }.orElseThrow() }

    return tools.send(Runtime.getProperties(propsId, Optional.empty(), Optional.empty(), Optional.empty()))
        .result
        .filter { it.name.toIntOrNull() != null}
        .map { it.value.orElseThrow().value.orElseThrow().toString().toInt().toByte() }
}