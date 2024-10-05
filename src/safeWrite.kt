import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

class FileNotRegularException: Exception("The file is not regular")
class FileNotWriteableException: Exception("The file cannot be written to")

/** writes content to a file, while performing checks if the file exists and is writable
 *
 * @param content the content to be written to the file
 * @param filePath the path to the file to be written to
 * @param append specifies if the content should be appended to the file or not
 *
 * @return [Unit] if writing was successful and [Exception] otherwise
 */
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