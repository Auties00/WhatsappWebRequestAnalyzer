package it.auties.analyzer

import org.openqa.selenium.devtools.v93.debugger.Debugger
import org.openqa.selenium.devtools.v93.network.Network
import java.util.Optional.empty

val whatsappKeys: Keys = Keys()
private const val STANDARD_BREAKPOINT = "keyPair:t,"
private const val MULTI_DEVICE_BREAKPOINT = "\"AES-GCM\",!1,"

fun main(args: Array<String>) {
    val driver = initialize()
    val tools = driver.devTools
    tools.createSession()
    tools.send(Debugger.enable(empty()))
    tools.send(Debugger.setBreakpointsActive(true))
    tools.addListener(Debugger.scriptParsed()) { onWhatsappScriptLoaded(tools, it, STANDARD_BREAKPOINT) }
    tools.addListener(Debugger.scriptParsed()) { onWhatsappScriptLoaded(tools, it, MULTI_DEVICE_BREAKPOINT) }
    tools.addListener(Debugger.paused()) { onBreakpointTriggered(tools, it) }
    tools.send(Network.enable(empty(), empty(), empty()))
    tools.addListener(Network.webSocketFrameSent()) { handleSentMessage(it) }
    tools.addListener(Network.webSocketFrameReceived()) { handleReceivedMessage(it) }
    driver["https://web.whatsapp.com/"]
}