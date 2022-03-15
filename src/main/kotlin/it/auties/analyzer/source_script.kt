package it.auties.analyzer

import it.auties.bytes.Bytes
import org.openqa.selenium.devtools.DevTools
import org.openqa.selenium.devtools.v97.debugger.Debugger
import org.openqa.selenium.devtools.v97.debugger.model.Location
import org.openqa.selenium.devtools.v97.debugger.model.Paused
import org.openqa.selenium.devtools.v97.debugger.model.ScriptParsed
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
        .forEach { it.second.forEach { column -> createBreakpoint(tools, script, it.first, column) } }
}

private fun createBreakpoint(
    tools: DevTools,
    script: ScriptParsed,
    line: Int,
    column: Int
) {
    println("Creating breakpoint at $line:$column")
    tools.send(Debugger.setBreakpoint(Location(script.scriptId, line, of(column)), empty()))
}

fun onBreakpointTriggered(tools: DevTools, paused: Paused) {
    val frame = paused.callFrames.first()
    val local = getKeypairObjectId(frame)
    val key = getKeyValue("e", local, tools)!!
    whatsappKeys.writeIv.set(0)
    whatsappKeys.readIv.set(0)
    whatsappKeys.keys.add(key.toBytes())
    tools.send(Debugger.resume(empty()))
}