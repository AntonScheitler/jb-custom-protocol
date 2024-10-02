import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Path

const val CLOSE_CONNECTION_NUM: Byte = 0;

object TYPES {
    const val OK: Byte = 1
    const val WRITE: Byte = 2
    const val CLEAR: Byte = 3
    const val ERROR: Byte = 4
    const val PING: Byte = 5
}

fun main(args: Array<String>) {
    val socketPath = args[0];
    val address = UnixDomainSocketAddress.of(Path.of(socketPath));

    SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
        // todo handle connections properly (validation, check if open, etc)
        channel.connect(address);
        println("Successfully connected to the server!")

        while (channel.isOpen) {
            println(
                "To send a message to the server, type the corresponding number, or type 0 to close the connection\n" +
                "${TYPES.OK} -> Ok\n" +
                "${TYPES.WRITE} -> Write\n" +
                "${TYPES.CLEAR} -> Clear\n" +
                "${TYPES.ERROR} -> Error\n" +
                "${TYPES.PING} -> Ping"
            )
            // todo handle input properly
            val messageType = readlnOrNull()?.toByte();

            val messageToServer = when (messageType) {
                CLOSE_CONNECTION_NUM -> {
                    channel.close()
                    continue
                }
                TYPES.OK -> composeOk()
                TYPES.WRITE -> {
                    println("A write message needs content. Please enter it now:")
                    composeWrite(readlnOrNull() ?: "")
                }
                TYPES.CLEAR -> composeClear()
                TYPES.ERROR -> {
                    println("An error message can be specified. Please enter it now or hit enter to leave it empty:")
                    composeError(readlnOrNull() ?: "")
                }
                TYPES.PING -> composePing()
                else -> {
                    println("There is no message associated with this input")
                    continue
                }
            }

            channel.write(messageToServer);
            println("Message sent to the server!")

            val unvalidatedChannelDataResult = readChannelMessage(channel)
            unvalidatedChannelDataResult.onSuccess { unvalidatedData ->
                val validatedChannelDataResult = validateChannelData(unvalidatedData)
                validatedChannelDataResult.onSuccess { validatedData ->
                    when (validatedData.type) {
                        TYPES.OK -> println("Server responds OK")
                        TYPES.ERROR -> {
                            println("Server responds with error")
                            println("Error: ${validatedData.content}")
                        }
                        else -> println("Server responds with an illegal message type")
                    }
                }.onFailure { validationError ->
                    println(validationError.message)
                }
            }.onFailure { readingError ->
                println(readingError.message)
            }
        }

        println("The connection to the server has been closed")
    }
}
