package it.auties.analyzer

import org.openqa.selenium.devtools.DevTools
import org.openqa.selenium.devtools.v124.runtime.Runtime
import org.openqa.selenium.devtools.v124.debugger.model.CallFrame
import org.openqa.selenium.devtools.v124.debugger.model.Scope
import org.openqa.selenium.devtools.v124.runtime.model.PropertyDescriptor
import org.openqa.selenium.devtools.v124.runtime.model.RemoteObject
import org.openqa.selenium.devtools.v124.runtime.model.RemoteObjectId
import java.util.*

fun getKeypairObjectId(frame: CallFrame): RemoteObjectId = frame.scopeChain
    .first { it.type == Scope.Type.LOCAL }
    .run { getObject().objectId.orThrow() }

fun getKeyValue(property: String, id: RemoteObjectId, tools: DevTools): List<Byte>? = tools
    .send(Runtime.getProperties(id, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
    .result
    .firstNotNullOfOrNull { if (it.name == property) parseUnsignedIntArray(tools, it) else getKeyValueDeep(it, property, tools) }

private fun getKeyValueDeep(it: PropertyDescriptor, property: String, tools: DevTools): List<Byte>? = it.value
    .filter { obj -> obj.type == RemoteObject.Type.OBJECT }
    .map { obj -> getKeyValue(property, obj.objectId.orThrow(), tools) }
    .orNull()

private fun parseUnsignedIntArray(tools: DevTools, prop: PropertyDescriptor): List<Byte> {
    val propValue = prop.value.orNull() ?: return emptyList()
    val propValueId = propValue.objectId.orNull() ?: return emptyList()
    val propsId = getArrayPropertyId(tools, propValueId) ?: return emptyList()
    return tools.send(Runtime.getProperties(propsId, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
        .result
        .filter { it.name.toIntOrNull() != null }
        .map { it.value.orThrow().value.orThrow().toString().toInt().toByte() }
}

private fun getArrayPropertyId(tools: DevTools, propValueId: RemoteObjectId): RemoteObjectId? =
    tools.send(Runtime.getProperties(propValueId, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
        .internalProperties
        .orThrow()
        .firstOrNull { it.name == "[[Uint8Array]]" }
        ?.run { value.flatMap { it.objectId }.orNull() }