import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

class FileNotRegularException: Exception("The file is not regular")
class FileNotWriteableException: Exception("The file cannot be written to")

class ChannelNotOpenException: Exception("The socket channel is not open")
class ChannelDisconnectedException: Exception("The socket channel has been disconnected")

fun safeWriteFile(content: ByteBuffer, filePath: Path, append: Boolean): Result<Unit> {
    // create file, if it has been deleted while the server runs
    if (Files.notExists(filePath)) {
        Files.createFile(filePath);
    }

    // check if the file is irregular/writeable before attempting to write
    if (!Files.isRegularFile(filePath)) {
        return Result.failure(FileNotRegularException())
    }  else if (!Files.isWritable(filePath)) {
        return Result.failure(FileNotWriteableException())
    } else {
        FileOutputStream(filePath.pathString, append).write(content.array());
    }

    return Result.success(Unit)
}

fun safeWriteFileWithNewline(content: ByteBuffer, filePath: Path, append: Boolean): Result<Unit> {
    val contentWithNewline = ByteBuffer.allocate(content.capacity() + 1)
    contentWithNewline.put(content)
    contentWithNewline.put(10)
    return safeWriteFile(contentWithNewline.flip(), filePath, append)
}

fun safeWriteChannel(content: ByteBuffer, channel: SocketChannel): Result<Unit> {
    if (!channel.isOpen) {
        return Result.failure(ChannelNotOpenException())
    }
    if (!channel.isConnected) {
        return Result.failure(ChannelDisconnectedException())
    }
    channel.write(content)
    return Result.success(Unit)
}
