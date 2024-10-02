import java.nio.ByteBuffer

class UnknownMessageTypeException: Exception("The type of this message is unknown")

class MessageTooShortException: Exception("The message is too short to include a header")
class ContentTooShortException: Exception("The content length specified in the header is greater than the actual content length")

abstract class ContentNotEmptyException(messageType: String): Exception("The content of messages with type $messageType must be empty!")
class OkMessageContentNotEmptyException: ContentNotEmptyException("OK")
class ClearMessageContentNotEmptyException: ContentNotEmptyException("CLEAR")
class PingMessageContentNotEmptyException: ContentNotEmptyException("PING")

// todo figure out how to use 1, 2, 3, 4, 5 instead of Byte as the type for @param type
data class ValidatedChannelData(val type: Byte, val content: ByteBuffer)

fun validateChannelData(data: ChannelData) : Result<ValidatedChannelData> {
    val successData = ValidatedChannelData(data.type, data.content)

    // check if reading the header worked properly
    if (data.numHeaderBytesRead < 8) {
        return Result.failure(MessageTooShortException())
    }

    // check if reading the content worked properly
    if (data.numContentBytesRead < data.contentLength) {
        return Result.failure(ContentTooShortException())
    }

    // check if message type conforms with the content length
    val result = when (data.type)  {
        TYPES.OK -> if (data.contentLength == 0) Result.success(successData)
                    else Result.failure(OkMessageContentNotEmptyException())
        TYPES.WRITE -> Result.success(successData)
        TYPES.CLEAR -> if (data.contentLength == 0) Result.success(successData)
                    else Result.failure(ClearMessageContentNotEmptyException())
        TYPES.ERROR -> Result.success(successData)
        TYPES.PING -> if (data.contentLength == 0) Result.success(successData)
                    else Result.failure(PingMessageContentNotEmptyException())
        else -> Result.failure(UnknownMessageTypeException())
    }

    return result
}