package it.auties.analyzer

import org.openqa.selenium.devtools.v121.debugger.Debugger
import org.openqa.selenium.devtools.v121.network.Network
import java.util.Optional.empty

val whatsappKeys: Keys = Keys()

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    val driver = initialize()
    val tools = driver.devTools
    tools.createSession()
    tools.send(Debugger.enable(empty()))
    tools.send(Debugger.setBreakpointsActive(true))
    tools.addListener(Debugger.scriptParsed()) { onWhatsappScriptLoaded(tools, it, "\"AES-GCM\",!1") }
    tools.addListener(Debugger.paused()) { onBreakpointTriggered(tools, it) }
    tools.send(Network.enable(empty(), empty(), empty()))
    tools.addListener(Network.webSocketFrameSent()) { handleSentMessage(it) }
    tools.addListener(Network.webSocketFrameReceived()) { handleReceivedMessage(it) }
    driver["https://web.whatsapp.com/"]
}