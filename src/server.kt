import java.io.FileOutputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    // todo validate input
    // todo only one argument (socketPath) is allowed
    val socketPath = args[0];
    Files.deleteIfExists(Path.of(socketPath));

    // todo validate input
    // get the path to the file from stdin
    println("Please specify the path to the file, which will be edited:")
    val filePath = readlnOrNull() ?: ""

    // bind to the socket in the argument
    val address = UnixDomainSocketAddress.of(socketPath);
    ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(address).use { serverChannel ->
        // todo create loop / coroutine to handle client connections
        println("Server listening at address: ${serverChannel.localAddress}");

        // todo use coroutines
        // listen for incoming connections
        val clientChannel = serverChannel.accept();
        println("Client connection accepted");
        println("--------------------------------------------------")
        while (true) {
            val unvalidatedChannelDataResult = readChannelMessage(clientChannel);
            unvalidatedChannelDataResult.onSuccess { unvalidatedData ->
                val validatedChannelDataResult = validateChannelData(unvalidatedData)
                validatedChannelDataResult.onSuccess { validatedData ->
                    val messageToClient: ByteBuffer? = when (validatedData.type) {
                        TYPES.OK -> {
                            println("Received OK Message from client")
                            println("Sending no response")
                            println("--------------------------------------------------")
                            null
                        }
                        TYPES.WRITE -> {
                            println("Received WRITE Message from client")
                            println("Writing content to the file...")
                            FileOutputStream(filePath, true).write(validatedData.content.array());
                            FileOutputStream(filePath, true).write(10);
                            println("Content written!")
                            println("Responding OK...")
                            composeOk();
                        }
                        TYPES.CLEAR -> {
                            println("Received CLEAR Message from client")
                            println("Clearing file...")
                            FileOutputStream(filePath).write(ByteArray(0));
                            println("File cleared!")
                            println("Responding OK...")
                            composeOk()
                        }
                        TYPES.ERROR -> {
                            println("Received ERROR Message from client")
                            println("Error message: ${StandardCharsets.UTF_8.decode(validatedData.content.flip())}")
                            println("Sending no response")
                            println("--------------------------------------------------")
                            null
                        }
                        TYPES.PING -> {
                            println("Received PING Message from client")
                            println("Responding OK...")
                            composeOk()
                        }
                        else -> composeError("The message is valid but the server failed to determine it's type")
                    }
                    if (messageToClient != null) {
                        clientChannel.write(messageToClient)
                        println("Response sent!")
                        println("--------------------------------------------------")
                    }
                }.onFailure { validationException ->
                    println("Validation of the message failed")
                    println("Error: ${validationException.message}")
                    println("Responding with ERROR")
                    clientChannel.write(composeError(validationException.message ?: ""))
                    println("Response sent!")
                    println("--------------------------------------------------")
                }
                // todo other exceptions besides connectionClosed may appear (faulty client, doesn't read buffer and closes connection)
            }.onFailure { connectionClosedException ->
                println(connectionClosedException.message)
                return
            }
        }
    }
}