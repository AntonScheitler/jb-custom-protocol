import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
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
        println("--------------------------------------------------")

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
            if (messageType == CLOSE_CONNECTION_NUM) {
                channel.close()
                println("Connection closed")
                return
            }

            val messageToServer = when (messageType) {
                TYPES.OK -> {
                    println("Sending OK...")
                    composeOk()
                }
                TYPES.WRITE -> {
                    println("A write message needs content. Please enter it now:")
                    val content = readlnOrNull() ?: ""
                    println("Sending WRITE...")
                    composeWrite(content)
                }
                TYPES.CLEAR -> {
                    println("Sending CLEAR...")
                    composeClear()
                }
                TYPES.ERROR -> {
                    println("An error message can be specified. Please enter it now or hit enter to leave it empty:")
                    val errorMessage = readlnOrNull() ?: ""
                    println("Sending ERROR...")
                    composeError(errorMessage)
                }
                TYPES.PING -> {
                    println("Sending PING...")
                    composePing()
                }
                else -> {
                    println("There is no message associated with this input")
                    println("--------------------------------------------------")
                    continue
                }
            }

            channel.write(messageToServer);
            println("Message sent!")

            // only wait for responses, if the message type expects them
            if (messageType != TYPES.OK && messageType != TYPES.ERROR) {
                val unvalidatedChannelDataResult = readChannelMessage(channel)
                unvalidatedChannelDataResult.onSuccess { unvalidatedData ->
                    val validatedChannelDataResult = validateChannelData(unvalidatedData)
                    validatedChannelDataResult.onSuccess { validatedData ->
                        when (validatedData.type) {
                            TYPES.OK -> {
                                println("Received OK from server")
                            }
                            TYPES.ERROR -> {
                                println("Received ERROR from server")
                                println("Error: ${validatedData.content}")
                            }
                            else -> println("Received unexpected message type from server")
                        }
                    }.onFailure { validationError ->
                        println("Validation of the server response failed")
                        println("Error: ${validationError.message}")
                    }
                }.onFailure { connectionClosedException ->
                    println(connectionClosedException.message)
                    return
                }
            }
            println("--------------------------------------------------")
        }
    }
}