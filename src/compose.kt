import java.nio.ByteBuffer

/** composes an OK message that conforms with the standard
 *
 * @return the OK message in the form of a [ByteBuffer]
 */
fun composeOk(): ByteBuffer {
    return composeMessageWithoutContent(Types.OK);
}

/** composes a WRITE message that conforms with the standard
 *
 * @return the WRITE message in the form of a [ByteBuffer]
 */
fun composeWrite(content: String): ByteBuffer {
    return composeMessageWithContent(Types.WRITE, content);
}

/** composes a CLEAR message that conforms with the standard
 *
 * @return the CLEAR message in the form of a [ByteBuffer]
 */
fun composeClear(): ByteBuffer {
    return composeMessageWithoutContent(Types.CLEAR);
}

/** composes an ERROR message that conforms with the standard
 *
 * @return the ERROR message in the form of a [ByteBuffer]
 */
fun composeError(errorMessage: String? = ""): ByteBuffer {
    return composeMessageWithContent(Types.ERROR, errorMessage ?: "");
}

/** composes a PING message that conforms with the standard
 *
 * @return the PING message in the form of a [ByteBuffer]
 */
fun composePing(): ByteBuffer {
    return composeMessageWithoutContent(Types.PING);
}

/** composes a message that has no content
 *
 * @param messageType the type of the message
 *
 * @return the composed message in the form of a [ByteBuffer]
 */
private fun composeMessageWithoutContent(messageType: Types): ByteBuffer {
    val bytes = ByteArray(8);
    bytes[0] = messageType.value;
    bytes.fill(0, 1, 8);
    return ByteBuffer.wrap(bytes);
}

/** composes a message with some content
 *
 * @param messageType the type of the message
 * @param content the content of the message
 *
 * @return the composed message in the form of a [ByteBuffer]
 */
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