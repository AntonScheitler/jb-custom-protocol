import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileOutputStream
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

fun main(args: Array<String>) = runBlocking {
    if (args.size != 2) {
        throw RuntimeException("Usage: ./server [SOCKET_PATH] [FILE_PATH]")
    }
    val socketPath = args[0];
    Files.deleteIfExists(Path.of(socketPath));

    val filePath = Path.of(args[1])
    // create file if it doesn't exist
    if (!Files.exists(filePath)) {
        Files.createFile(filePath)
    }
    // file must be regular to be written to and/or cleared
    if (!Files.isRegularFile(filePath)) {
        throw IOException("File must be regular")
    }

    // bind to the socket in the argument
    val address = UnixDomainSocketAddress.of(socketPath);
    ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(address).use { serverChannel ->
        println("Server listening at address: ${serverChannel.localAddress}");

        // listen for incoming connections
        var id = 0
        while (true) {
            val clientChannel = serverChannel.accept()
            launch(Dispatchers.IO) {
                handleClient(clientChannel, filePath.pathString, id)
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
                        // todo possible IO Exception
                        FileOutputStream(filePath, true).write(validatedData.content.array());
                        FileOutputStream(filePath, true).write(10);
                        printlnWithCoroutineId(coroutineId, "Responding OK...")
                        composeOk();
                    }
                    TYPES.CLEAR -> {
                        printlnWithCoroutineId(coroutineId, "Received CLEAR Message")
                        // todo possible IO Exception
                        FileOutputStream(filePath).write(ByteArray(0));
                        printlnWithCoroutineId(coroutineId, "Responding OK...")
                        composeOk()
                    }
                    TYPES.ERROR -> {
                        printlnWithCoroutineId(coroutineId, "Received ERROR Message")
                        printlnWithCoroutineId(coroutineId, "Error: ${StandardCharsets.UTF_8.decode(validatedData.content.flip())}")
                        printlnWithCoroutineId(coroutineId, "Sending no response")
                        printlnWithCoroutineId(coroutineId, "--------------------------------------------------")
                        null
                    }
                    TYPES.PING -> {
                        printlnWithCoroutineId(coroutineId, "Received PING Message")
                        printlnWithCoroutineId(coroutineId, "Responding OK...")
                        composeOk()
                    }
                    // todo refactor, so else wont show up here
                    else -> composeError("The message is valid but the server failed to determine it's type")
                }
                if (messageToClient != null) {
                    // todo might fail
                    clientChannel.write(messageToClient)
                    printlnWithCoroutineId(coroutineId, "Response sent!")
                    printlnWithCoroutineId(coroutineId, "--------------------------------------------------")
                }
            }.onFailure { validationException ->
                printlnWithCoroutineId(coroutineId, "Validation of the message failed")
                printlnWithCoroutineId(coroutineId, "Error: ${validationException.message}")
                printlnWithCoroutineId(coroutineId, "Responding with ERROR")
                // todo might fail
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