package it.auties.analyzer

import org.openqa.selenium.devtools.DevTools
import org.openqa.selenium.devtools.v93.debugger.Debugger
import org.openqa.selenium.devtools.v93.debugger.model.Location
import org.openqa.selenium.devtools.v93.debugger.model.Paused
import org.openqa.selenium.devtools.v93.debugger.model.ScriptParsed
import java.lang.IllegalArgumentException
import java.util.Optional.empty
import java.util.Optional.of

fun onWhatsappScriptLoaded(tools: DevTools, script: ScriptParsed, breakpointCode: String) {
    if (!script.url.contains("https://web.whatsapp.com/bootstrap_qr")) {
        return
    }

    tools.send(Debugger.getScriptSource(script.scriptId))
        .scriptSource
        .lines()
        .mapIndexed { lineNumber, line -> lineNumber to line.indexesOf(breakpointCode) }
        .filter { it.second.isNotEmpty() }
        .forEach { it.second.forEach { column -> tools.send(Debugger.setBreakpoint(Location(script.scriptId, it.first, of(column)), empty())) } }
}

fun onBreakpointTriggered(tools: DevTools, paused: Paused) {
    println("Called breakpoint at ${paused.hitBreakpoints}")
    val frame = paused.callFrames.first()
    val local = getKeypairObjectId(frame)
    if (frame.functionName == "t.getOrGenerate") {
        whatsappKeys.standardPublicKey = getKeyValue("pubKey", local, tools)!!
        whatsappKeys.standardPrivateKey = getKeyValue("privKey", local, tools)!!
        tools.send(Debugger.resume(empty()))
        return
    }

    foldCounter()
    val key = getKeyValue("e", local, tools)!!
    whatsappKeys.multiDeviceKeys.add(key)
    tools.send(Debugger.resume(empty()))
}

private fun foldCounter() {
    if (whatsappKeys.multiDeviceKeys.size % 2 == 0) {
        whatsappKeys.multiDeviceReads = 0
        return
    }

    whatsappKeys.multiDeviceWrites = 0
}
