import it.auties.whatsapp4j.binary.BinaryArray

data class WhatsappKeys(var publicKey: List<Byte>, var privateKey: List<Byte>, var encKey: BinaryArray = BinaryArray.empty())
