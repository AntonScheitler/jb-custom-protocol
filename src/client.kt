import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Path

const val CLOSE_CONNECTION_NUM: Byte = 0;

/** runs a client that connects to the specified socket and sends messages to the server.
 * this can be used to try out some of the server behavior in an interactive way.
 * however, to see if the server really behaves properly, tests are better suited
 */
fun main(args: Array<String>) {
    val socketPath = args[0];
    val address = UnixDomainSocketAddress.of(Path.of(socketPath));

    // connect to the socket
    SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
        channel.connect(address);
        println("Successfully connected to the server!")
        println("--------------------------------------------------")

        while (channel.isOpen) {
            // get input from the user to generate the message from
            println(
                "To send a message to the server, type the corresponding number, or type 0 to close the connection\n" +
                "${Types.OK.value} -> Ok\n" +
                "${Types.WRITE.value} -> Write\n" +
                "${Types.CLEAR.value} -> Clear\n" +
                "${Types.ERROR.value} -> Error\n" +
                "${Types.PING.value} -> Ping"
            )
            val messageTypeInput = readlnOrNull()?.toByte()

            // check if the client wants to close the connection
            if (messageTypeInput == CLOSE_CONNECTION_NUM) {
                channel.close()
                println("Connection closed")
                return
            }

            // get the Type based on the input
            val messageType = Types.fromByte(messageTypeInput ?: -1)
            if (messageType == null){
                println("Unknown message type")
            }

            // send a message corresponding to the type
            val messageToServer = when (messageType) {
                Types.OK -> {
                    println("Sending OK...")
                    composeOk()
                }
                Types.WRITE -> {
                    println("A write message needs content. Please enter it now:")
                    val content = readlnOrNull() ?: ""
                    println("Sending WRITE...")
                    composeWrite(content)
                }
                Types.CLEAR -> {
                    println("Sending CLEAR...")
                    composeClear()
                }
                Types.ERROR -> {
                    println("An error message can be specified. Please enter it now or hit enter to leave it empty:")
                    val errorMessage = readlnOrNull() ?: ""
                    println("Sending ERROR...")
                    composeError(errorMessage)
                }
                Types.PING -> {
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

            // since both the client and the server adhere to the standard, there won't be a response to an OK or ERROR
            if (messageType != Types.OK && messageType != Types.ERROR) {
                // read from the channel
                val unvalidatedChannelDataResult = readChannelMessage(channel)
                unvalidatedChannelDataResult.onSuccess { unvalidatedData ->
                    // validate the message
                    val validatedChannelDataResult = validateChannelMessage(unvalidatedData)
                    validatedChannelDataResult.onSuccess { validatedData ->
                        when (validatedData.type) {
                            Types.OK -> {
                                println("Received OK from server")
                            }
                            Types.ERROR -> {
                                println("Received ERROR from server")
                                println("Error: ${StandardCharsets.UTF_8.decode(validatedData.content)}")
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