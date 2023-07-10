package it.auties.analyzer

import org.openqa.selenium.devtools.DevTools
import org.openqa.selenium.devtools.v114.debugger.Debugger
import org.openqa.selenium.devtools.v114.debugger.model.Location
import org.openqa.selenium.devtools.v114.debugger.model.Paused
import org.openqa.selenium.devtools.v114.debugger.model.ScriptParsed
import java.util.Optional.empty
import java.util.Optional.of

fun onWhatsappScriptLoaded(tools: DevTools, script: ScriptParsed, breakpointCode: String) {
    downloadScript(tools, script)
        ?.lines()
        ?.mapIndexed { lineNumber, line -> lineNumber to line.indexesOf(breakpointCode) }
        ?.filter { it.second.isNotEmpty() }
        ?.forEach { it.second.forEach { column -> createBreakpoint(tools, script, it.first, column) } }
}

private fun downloadScript(tools: DevTools, script: ScriptParsed) =
    runCatching { tools.send(Debugger.getScriptSource(script.scriptId)).scriptSource }.getOrNull()

private fun createBreakpoint(
    tools: DevTools,
    script: ScriptParsed,
    line: Int,
    column: Int
) {
    println("Creating breakpoint at $line:$column in script at ${script.url}")
    tools.send(Debugger.setBreakpoint(Location(script.scriptId, line, of(column)), empty()))
}

fun onBreakpointTriggered(tools: DevTools, paused: Paused) {
    val frame = paused.callFrames.first()
    val local = getKeypairObjectId(frame)
    val key = getKeyValue("e", local, tools)!!
    whatsappKeys.writeIv.set(0)
    whatsappKeys.readIv.set(0)
    println("Adding key: $key")
    whatsappKeys.keys.add(key.toByteArray())
    tools.send(Debugger.resume(empty()))
}