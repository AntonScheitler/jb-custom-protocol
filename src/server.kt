import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

enum class Types(val value: Byte) {
    OK(1),
    WRITE(2),
    CLEAR(3),
    ERROR(4),
    PING(5);

    companion object {
        fun fromByte(value: Byte) = entries.firstOrNull {it.value == value}
    }
}

fun main(args: Array<String>) {
    if (args.size != 2) {
        throw RuntimeException("Usage: ./server [SOCKET_PATH] [FILE_PATH]")
    }
    val socketPath = Path.of(args[0]);

    // check if a unix domain socket can be created at the specified socket path
    if (!Files.isWritable(socketPath.parent)) {
        println("permission denied to create a unix domain socket at the specified location")
        return
    }
    Files.deleteIfExists(socketPath)

    val filePath = Path.of(args[1])
    // create file if it doesn't exist
    if (!Files.exists(filePath)) {
        Files.createFile(filePath)
    }
    // file must be regular to be written to and/or cleared
    if (!Files.isRegularFile(filePath)) {
        println("The file is not regular")
        return
    }
    // check if file can be written to
    if (!Files.isWritable(filePath)) {
        println("The file cannot be written to")
        return
    }


    runBlocking {
        // bind to the socket
        val address = UnixDomainSocketAddress.of(socketPath);
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(address).use { serverChannel ->
            println("Server listening at address: ${serverChannel.localAddress}");

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
}

suspend fun handleClient(clientChannel: SocketChannel, filePath: Path, coroutineId: Int) {
    printlnWithCoroutineId(coroutineId, "Client connection accepted")
    printlnWithCoroutineId(coroutineId, "--------------------------------------------------")
    while (clientChannel.isOpen) {
        // read message from the channel
        val unvalidatedChannelDataResult = readChannelMessage(clientChannel);
        unvalidatedChannelDataResult.onSuccess { unvalidatedData ->
            // validate the message just read
            val validatedChannelDataResult = validateChannelMessage(unvalidatedData)
            validatedChannelDataResult.onSuccess { validatedData ->
                // respond according to the type of the message (if it is valid)
                val messageToClient: ByteBuffer? = when (validatedData.type) {
                    Types.OK -> {
                        printlnWithCoroutineId(coroutineId, "Received OK Message")
                        printlnWithCoroutineId(coroutineId, "Sending no response")
                        null
                    }
                    Types.WRITE -> {
                        printlnWithCoroutineId(coroutineId, "Received WRITE Message")
                        // create file, if it has been deleted while the server runs
                        val contentWriteResult = safeWriteFile(validatedData.content, filePath, true)
                        var response = composeOk()
                        // respond OK if write was successful and ERROR otherwise
                        contentWriteResult.onSuccess { _ ->
                            printlnWithCoroutineId(coroutineId, "Write successful")
                            printlnWithCoroutineId(coroutineId, "Responding OK...")
                        }.onFailure { writeException ->
                            printlnWithCoroutineId(coroutineId, writeException.message ?: "Write failed")
                            printlnWithCoroutineId(coroutineId, "Responding with ERROR...")
                            response = composeError(writeException.message ?: "Write failed")
                        }
                        response
                    }
                    Types.CLEAR -> {
                        printlnWithCoroutineId(coroutineId, "Received CLEAR Message")
                        // create file, if it has been deleted while the server runs
                        val fileWriteResult = safeWriteFile(ByteBuffer.wrap(ByteArray(0)), filePath, false)
                        var response = composeOk()
                        // respond OK if write was successful and ERROR otherwise
                        fileWriteResult.onSuccess { _ ->
                            printlnWithCoroutineId(coroutineId, "Clear successful")
                            printlnWithCoroutineId(coroutineId, "Responding OK...")
                        }.onFailure { writeException ->
                            printlnWithCoroutineId(coroutineId, writeException.message ?: "Clear failed")
                            printlnWithCoroutineId(coroutineId, "Responding with ERROR...")
                            response = composeError(writeException.message ?: "Clear failed")
                        }
                        response
                    }
                    Types.ERROR -> {
                        printlnWithCoroutineId(coroutineId, "Received ERROR Message")
                        printlnWithCoroutineId(coroutineId, "Error: ${StandardCharsets.UTF_8.decode(validatedData.content)}")
                        printlnWithCoroutineId(coroutineId, "Sending no response")
                        null
                    }
                    Types.PING -> {
                        printlnWithCoroutineId(coroutineId, "Received PING Message")
                        printlnWithCoroutineId(coroutineId, "Responding OK...")
                        composeOk()
                    }
                }
                if (messageToClient != null) {
                    clientChannel.write(messageToClient)
                }
            }.onFailure { validationException ->
                // reply with an error if the validation has failed
                printlnWithCoroutineId(coroutineId, "Validation of the message failed")
                printlnWithCoroutineId(coroutineId, "Error: ${validationException.message}")
                printlnWithCoroutineId(coroutineId, "Responding with ERROR")
                clientChannel.write(composeError(validationException.message))
            }
        }.onFailure { connectionClosedException ->
            printlnWithCoroutineId(coroutineId, connectionClosedException.message ?: "")
            return
        }
        printlnWithCoroutineId(coroutineId, "--------------------------------------------------")
    }
}

fun printlnWithCoroutineId(coroutineId: Int, string: String?) {
    println("[$coroutineId] $string")
}