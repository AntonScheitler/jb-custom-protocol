import java.nio.ByteBuffer

class UnknownMessageTypeException: Exception("The type of this message is unknown")
class MessageTooShortException: Exception("The message is too short to include a header")
class ContentTooShortException: Exception("The content length specified in the header is greater than the actual content length")
class ContentTooLongException: Exception("The content length exceeds Int.MAX_VALUE")

abstract class ContentNotEmptyException(messageType: String): Exception("The content of messages with type $messageType must be empty!")
class OkMessageContentNotEmptyException: ContentNotEmptyException("OK")
class ClearMessageContentNotEmptyException: ContentNotEmptyException("CLEAR")
class PingMessageContentNotEmptyException: ContentNotEmptyException("PING")

data class ValidatedChannelData(val type: Types, val content: ByteBuffer)

/** validates the message read from the channel
 *
 * @param data the unvalidated data returned by [readChannelMessage]
 *
 * @return the type and content of the message or an [Exception] if the message is invalid
 */
fun validateChannelMessage(data: ChannelData) : Result<ValidatedChannelData> {
    // check if reading the header worked properly
    if (data.numHeaderBytesRead < 8) {
        return Result.failure(MessageTooShortException())
    }

    // check if contentLength has overflowed
    if (data.contentLength < 0) {
        return Result.failure(ContentTooLongException())
    }

    // check if fewer bytes were read than the contentLength in the header specified
    if (data.numContentBytesRead < data.contentLength) {
        return Result.failure(ContentTooShortException())
    }

    // check if message type conforms with the contentLength
    val result = when (data.typeByte)  {
        Types.OK.value -> if (data.contentLength == 0) Result.success(ValidatedChannelData(Types.OK, data.content))
                    else Result.failure(OkMessageContentNotEmptyException())
        Types.WRITE.value -> Result.success(ValidatedChannelData(Types.WRITE, data.content))
        Types.CLEAR.value -> if (data.contentLength == 0) Result.success(ValidatedChannelData(Types.CLEAR, data.content))
                    else Result.failure(ClearMessageContentNotEmptyException())
        Types.ERROR.value -> Result.success(ValidatedChannelData(Types.ERROR, data.content))
        Types.PING.value -> if (data.contentLength == 0) Result.success(ValidatedChannelData(Types.PING, data.content))
                    else Result.failure(PingMessageContentNotEmptyException())
        else -> Result.failure(UnknownMessageTypeException())
    }

    return result
}