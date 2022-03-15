package it.auties.analyzer

import it.auties.whatsapp.model.request.Node

fun onMessageSent(node: Node) {
    println("Sent Binary Message: $node")
}

fun onMessageReceived(node: Node) {
    println("Received Binary Message $node")
}
