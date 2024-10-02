import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileOutputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) = runBlocking {
    // todo validate input
    if (args.size != 2) {
        println("Usage: ./server [SOCKET_PATH] [FILE_PATH]")
    }
    val socketPath = args[0];
    Files.deleteIfExists(Path.of(socketPath));

    // todo validate input
    // get the path to the file from stdin
    val filePath = args[1]

    // bind to the socket in the argument
    val address = UnixDomainSocketAddress.of(socketPath);
    ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(address).use { serverChannel ->
        // todo create loop / coroutine to handle client connections
        println("Server listening at address: ${serverChannel.localAddress}");

        // todo use coroutines
        // listen for incoming connections
        var id = 0
        while (true) {
            val clientChannel = serverChannel.accept()
            launch(Dispatchers.IO) {
                handleClient(clientChannel, filePath, id)
            }
            id++
        }
    }
}

suspend fun handleClient(clientChannel: SocketChannel, filePath: String, coroutineId: Int) {
    printlnWithCoroutineId(coroutineId, "Client connection accepted")
    printlnWithCoroutineId(coroutineId, "--------------------------------------------------")
    while (clientChannel.isOpen) {
        val unvalidatedChannelDataResult = readChannelMessage(clientChannel);
        unvalidatedChannelDataResult.onSuccess { unvalidatedData ->
            val validatedChannelDataResult = validateChannelData(unvalidatedData)
            validatedChannelDataResult.onSuccess { validatedData ->
                val messageToClient: ByteBuffer? = when (validatedData.type) {
                    TYPES.OK -> {
                        printlnWithCoroutineId(coroutineId, "Received OK Message")
                        printlnWithCoroutineId(coroutineId, "Sending no response")
                        printlnWithCoroutineId(coroutineId, "--------------------------------------------------")
                        null
                    }
                    TYPES.WRITE -> {
                        printlnWithCoroutineId(coroutineId, "Received WRITE Message")
                        printlnWithCoroutineId(coroutineId, "Writing content to the file...")
                        FileOutputStream(filePath, true).write(validatedData.content.array());
                        FileOutputStream(filePath, true).write(10);
                        printlnWithCoroutineId(coroutineId, "Content written!")
                        printlnWithCoroutineId(coroutineId, "Responding OK...")
                        composeOk();
                    }
                    TYPES.CLEAR -> {
                        printlnWithCoroutineId(coroutineId, "Received CLEAR Message")
                        printlnWithCoroutineId(coroutineId, "Clearing file...")
                        FileOutputStream(filePath).write(ByteArray(0));
                        printlnWithCoroutineId(coroutineId, "File cleared!")
                        printlnWithCoroutineId(coroutineId, "Responding OK...")
                        composeOk()
                    }
                    TYPES.ERROR -> {
                        printlnWithCoroutineId(coroutineId, "Received ERROR Message")
                        printlnWithCoroutineId(coroutineId, "Error message: ${StandardCharsets.UTF_8.decode(validatedData.content.flip())}")
                        printlnWithCoroutineId(coroutineId, "Sending no response")
                        printlnWithCoroutineId(coroutineId, "--------------------------------------------------")
                        null
                    }
                    TYPES.PING -> {
                        printlnWithCoroutineId(coroutineId, "Received PING Message")
                        printlnWithCoroutineId(coroutineId, "Responding OK...")
                        composeOk()
                    }
                    else -> composeError("The message is valid but the server failed to determine it's type")
                }
                if (messageToClient != null) {
                    clientChannel.write(messageToClient)
                    printlnWithCoroutineId(coroutineId, "Response sent!")
                    printlnWithCoroutineId(coroutineId, "--------------------------------------------------")
                }
            }.onFailure { validationException ->
                printlnWithCoroutineId(coroutineId, "Validation of the message failed")
                printlnWithCoroutineId(coroutineId, "Error: ${validationException.message}")
                printlnWithCoroutineId(coroutineId, "Responding with ERROR")
                clientChannel.write(composeError(validationException.message ?: ""))
                printlnWithCoroutineId(coroutineId, "Response sent!")
                printlnWithCoroutineId(coroutineId, "--------------------------------------------------")
            }
            // todo other exceptions besides connectionClosed may appear (faulty client, doesn't read buffer and closes connection)
        }.onFailure { connectionClosedException ->
            printlnWithCoroutineId(coroutineId, connectionClosedException.message ?: "")
            return
        }
    }
}

fun printlnWithCoroutineId(coroutineId: Int, string: String) {
    println("[$coroutineId] $string")
}