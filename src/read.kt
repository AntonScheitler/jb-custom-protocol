import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

const val HEADER_SIZE = 8

class ConnectionClosedException(): Exception("The connection has been closed by the other party")

data class ChannelData(val typeByte: Byte, val numHeaderBytesRead: Int, val numContentBytesRead: Int,
                       val contentLength: Int, val content: ByteBuffer)

/** Reads a message from the channel
 *
 * @param channel the channel to read from
 *
 * @return the message including some metadata to help with validation or [ConnectionClosedException] if end-of-stream is reached
 */
fun readChannelMessage(channel: SocketChannel): Result<ChannelData> {
    val headerBuffer = ByteBuffer.allocate(HEADER_SIZE);
    val numHeaderBytesRead = channel.read(headerBuffer);

    // if the number returned by channel.read() is -1, then end-of-stream has been reached, which means that the client has closed the connection
    if (numHeaderBytesRead == -1) {
        return Result.failure(ConnectionClosedException())
    }

    // elem.toUByte() is necessary, because negative numbers may appear in headerBuffer[4..7] due to an overflow of Byte
    // if headerBuffer[4..7] for instance were [0, 0, 0, -1], elem.toUByte() would make sure that this -1 would be interpreted as 255
    // this way, there won't be any negative contentLength (except for integer overflows)
    val contentLength = headerBuffer.array().slice(4..7).foldIndexed(0) { idx, acc, elem ->
        (elem.toUByte().toInt() shl ((3 - idx) * 8)) + acc
    }

    // contentLength may overflow, in which case an empty buffer is allocated
    val contentBuffer = ByteBuffer.allocate(if (contentLength > 0) contentLength else 0);
    val numContentBytesRead = channel.read(contentBuffer);

    return Result.success(
        ChannelData(headerBuffer.flip().get(), numHeaderBytesRead, numContentBytesRead, contentLength, contentBuffer.flip())
    )
}