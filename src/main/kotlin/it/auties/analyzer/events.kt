package it.auties.analyzer

import it.auties.whatsapp4j.common.binary.BinaryFlag
import it.auties.whatsapp4j.common.response.JsonResponse
import it.auties.whatsapp4j.common.response.Response
import it.auties.whatsapp4j.standard.binary.BinaryMetric

fun onJsonMessageSent(request: Response<*>) {
    println("Sent JSON Message $request")
}

fun onJsonMessageReceived(response: Response<*>) {
    println("Received JSON Message $response")
}

fun onBinaryMessageSent(tag: String? = null, content: Any, metric: BinaryMetric? = null, flag: BinaryFlag? = null) {
    if (tag == null) {
        println("Sent Binary Message $content")
        return
    }

    println("Sent Binary Message(${metric?.name},${flag?.name}) $tag,$content")
}

fun onBinaryMessageReceived(tag: String? = null, content: Any) {
    if (tag == null) {
        println("Received Binary Message $content")
        return
    }

    println("Received Binary Message $tag,$content")
}

fun switchToMultiDevice(response: JsonResponse) {
    if (response.content()["type"] != "upgrade_md_prod") {
        return
    }

    println("Switching to multi device beta")
    whatsappKeys.multiDevice = true
}
