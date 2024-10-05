import java.nio.ByteBuffer

/** composes a message with a type, that doesn't have to be part of [Types] enum class
 *
 * @param messageTypeByte the byte that represents the type of the message
 *
 * @return the malicious message with the specified type in the form of a [ByteBuffer]
 */
fun composeMessageWithUnknownType(messageTypeByte: Byte): ByteBuffer {
    val bytes = ByteArray(8);
    bytes[0] = messageTypeByte;
    bytes.fill(0, 1, 8);
    return ByteBuffer.wrap(bytes);
}

/** composes a message with a contentLength header field that differs from the actual length of the content
 *
 * @param messageType the type of the message
 * @param content the content of the message
 * @param contentLengthOffset the offset to change the contentLength header field by
 *
 * @return the malicious message in the form of a [ByteBuffer]
 */
fun composeMessageWithIncorrectContentLength(messageType: Types, content: String, contentLengthOffset: Int): ByteBuffer {
    val bytes = ByteArray(8 + content.length);
    bytes[0] = messageType.value;
    bytes.fill(0, 1, 4);

    // using .toByte() means that there will be an overflow for numbers greater than 127
    // so if the content length were 255, bytes[4..7] would resemble [0, 0, 0, -1]
    // the interpretation of the bytes[4..7] needs to be done using .toUByte() as not to return a negative contentLength
    for (i in 0..3) bytes[i + 4] = ((content.length + contentLengthOffset) shr ((3 - i) * 8)).toByte();
    for (i in content.indices) bytes[i + 8] = content[i].code.toByte()
    return ByteBuffer.wrap(bytes)
}
