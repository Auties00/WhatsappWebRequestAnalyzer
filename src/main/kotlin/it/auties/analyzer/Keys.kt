package it.auties.analyzer

import it.auties.whatsapp4j.common.binary.BinaryArray
import it.auties.whatsapp4j.common.binary.BinaryArray.empty

data class Keys(
    var standardPublicKey: List<Byte> = emptyList(),
    var standardPrivateKey: List<Byte> = emptyList(),
    var standardReadKey: BinaryArray = empty(),

    var multiDevice: Boolean = false,
    var multiDeviceKeys: ArrayList<List<Byte>> = ArrayList(),
    var multiDeviceReads: Long = 0,
    var multiDeviceWrites: Long = 0
) {
    fun incrementIO(request: Boolean): Long =
        if (request) whatsappKeys.multiDeviceReads++ else whatsappKeys.multiDeviceWrites++
}
