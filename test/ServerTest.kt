import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Path
import kotlin.random.Random

class ServerTest {
    private val socketPath = "/home/anton/suck"
    private var channel: SocketChannel = SocketChannel.open()

    @BeforeEach
    fun setupChannel() {
        val address = UnixDomainSocketAddress.of(Path.of(socketPath))
        channel = SocketChannel.open(StandardProtocolFamily.UNIX)
        channel.connect(address)
    }

    @Test
    fun `server returns OK on WRITE`() {
        val message = composeWrite("regular write");
        assert(isServerResponseCorrect(message, Types.OK))
    }

    @Test
    fun `server returns OK on CLEAR`() {
        val message = composeClear();
        assert(isServerResponseCorrect(message, Types.OK))
    }

    @Test
    fun `server returns OK on PING`() {
        val message = composePing();
        assert(isServerResponseCorrect(message, Types.OK))
    }

    @Test
    fun `server returns error on OK message with content`() {
        val message = composeMessageWithIncorrectContentLength(Types.OK, "should not be here", 0)
        assert(isServerResponseCorrect(message, Types.ERROR))
    }

    @Test
    fun `server returns error on CLEAR message with content`() {
        val message = composeMessageWithIncorrectContentLength(Types.CLEAR, "should not be here", 0)
        assert(isServerResponseCorrect(message, Types.ERROR))
    }

    @Test
    fun `server returns error on PING message with content`() {
        val message = composeMessageWithIncorrectContentLength(Types.PING, "should not be here", 0)
        assert(isServerResponseCorrect(message, Types.ERROR))
    }

    @Test
    fun `server returns error if contentLength is longer than content`() {
        val message = composeMessageWithIncorrectContentLength(Types.WRITE, "write this, please", 4)
        assert(isServerResponseCorrect(message, Types.ERROR))
    }

    @Test
    fun `server returns ok and then error if contentLength is shorter than content`() {
        val message = composeMessageWithIncorrectContentLength(Types.WRITE, "write this, please", -4)
        // since the contentLength is shorter than the content, only a part of the content will be read and written
        // since the server can't know that the actual content is longer, it returns OK
        assert(isServerResponseCorrect(message, Types.OK))
        // when the server then reads the stream again it finds the part of the content that was ignored previously
        // since this is just random data (in this case "ease") it cannot interpret it as a message and returns an error
        val unvalidatedChannelResult = readChannelMessage(channel)
        unvalidatedChannelResult.onSuccess { unvalidatedChannelData ->
            val validatedChannelResult = validateChannelMessage(unvalidatedChannelData)
            validatedChannelResult.onSuccess { validatedChannelData ->
                assertEquals(validatedChannelData.type, Types.ERROR)
            }.onFailure {
                assert(false)
            }
        }.onFailure {
            assert(false)
        }
    }

    @Test
    fun `server returns error on unknown type`() {
        val message = composeMessageWithUnknownType(-1)
        assert(isServerResponseCorrect(message, Types.ERROR))
    }

    @Test
    fun `server returns error on false random long input`() {
        val bytes = Random(1234).nextBytes(50)
        // make sure that the type is unknown to avoid false positives (unlikely but still...)
        bytes[0] = -1
        assert(isServerResponseCorrect(ByteBuffer.wrap(bytes), Types.ERROR))
    }

    @Test
    fun `server returns error on false random short input`() {
        val bytes = Random(1234).nextBytes(5)
        // same as before: use an unknown type to avoid false positives
        bytes[0] = -1
        assert(isServerResponseCorrect(ByteBuffer.wrap(bytes), Types.ERROR))
    }

    private fun isServerResponseCorrect(message: ByteBuffer, returnType: Types): Boolean {
        channel.write(message)
        val unvalidatedChannelResult = readChannelMessage(channel)
        unvalidatedChannelResult.onSuccess { unvalidatedChannelData ->
            val validatedChannelResult = validateChannelMessage(unvalidatedChannelData)
            validatedChannelResult.onSuccess { validatedChannelData ->
                return validatedChannelData.type == returnType
            }.onFailure { _ ->
                return false
            }
        }.onFailure { _ ->
            return false
        }
        return false
    }

}