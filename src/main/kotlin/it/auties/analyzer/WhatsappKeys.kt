package it.auties.analyzer

import it.auties.whatsapp4j.common.binary.BinaryArray
import it.auties.whatsapp4j.common.binary.BinaryArray.empty

data class WhatsappKeys(
    var publicKey: List<Byte>,
    var privateKey: List<Byte>,
    var encKey: BinaryArray = empty(),
    var readKey: List<Byte>? = null,
    var writeKey: List<Byte>? = null,
    var readCounter: Long = 0,
    var writeCounter: Long = 0
)
