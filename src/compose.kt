import java.nio.ByteBuffer

fun composeOk(): ByteBuffer {
    return composeMessageWithoutContent(Types.OK);
}

fun composeWrite(content: String): ByteBuffer {
    return composeMessageWithContent(Types.WRITE, content);
}

fun composeClear(): ByteBuffer {
    return composeMessageWithoutContent(Types.CLEAR);
}


fun composeError(errorMessage: String? = ""): ByteBuffer {
    return composeMessageWithContent(Types.ERROR, errorMessage ?: "");
}

fun composePing(): ByteBuffer {
    return composeMessageWithoutContent(Types.PING);
}

private fun composeMessageWithoutContent(messageType: Types): ByteBuffer {
    val bytes = ByteArray(8);
    bytes[0] = messageType.value;
    bytes.fill(0, 1, 8);
    return ByteBuffer.wrap(bytes);
}

private fun composeMessageWithContent(messageType: Types, content: String): ByteBuffer {
    val bytes = ByteArray(8 + content.length);
    bytes[0] = messageType.value;
    bytes.fill(0, 1, 4);

    // using .toByte() means that there will be an overflow for numbers greater than 127
    // so if the content length were 255, bytes[4..7] would resemble [0, 0, 0, -1]
    // the interpretation of the bytes[4..7] needs to be done using .toUByte() as not to return a negative contentLength
    for (i in 0..3) bytes[i + 4] = (content.length shr ((3 - i) * 8)).toByte();
    for (i in content.indices) bytes[i + 8] = content[i].code.toByte()
    return ByteBuffer.wrap(bytes)
}
