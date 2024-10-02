import java.io.FileOutputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
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
        while (true) {
            val unvalidatedChannelDataResult = readChannelMessage(clientChannel);
            unvalidatedChannelDataResult.onSuccess { unvalidatedData ->
                val validatedChannelDataResult = validateChannelData(unvalidatedData)
                validatedChannelDataResult.onSuccess { validatedData ->
                    val messageToClient: ByteBuffer? = when (validatedData.type) {
                        TYPES.OK -> null
                        TYPES.WRITE -> {
                            FileOutputStream(filePath, true).write(validatedData.content.array());
                            FileOutputStream(filePath, true).write(10);
                            composeOk();
                        }
                        TYPES.CLEAR -> {
                            FileOutputStream(filePath).write(ByteArray(0));
                            composeOk()
                        }
                        TYPES.ERROR -> {
                            println(validatedData.content);
                            composeOk()
                        }
                        TYPES.PING -> {
                            composeOk()
                        }
                        else -> composeError("The message is valid but the server failed to determine it's type")
                    }
                    if (messageToClient != null) clientChannel.write(messageToClient)
                }.onFailure { validationException ->
                    clientChannel.write(composeError(validationException.message ?: ""))
                }
            }.onFailure { readingException ->
                println(readingException.message)
                return
            }
        }
    }
}