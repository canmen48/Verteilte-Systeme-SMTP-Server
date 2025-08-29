package file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Random;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;



public class MailStorageService {

    private static final String SENDER_FILE_NAME_REGEX = "[^a-zA-Z0-9@._-]";
    protected static final String BASE_DIR = "data";
    private final Random random = new Random();

    //ersetzt unerlaubte Zeichen mit Unterstrichen
    protected static String sanitizeSenderForFileName(String sender) {
        return sender.replaceAll(SENDER_FILE_NAME_REGEX, "_");
    }

    protected static String buildFileName(String sender, int messageId) {
        return sanitizeSenderForFileName(sender) + "_" + messageId;
    }

/*Speichert eine E-Mail-Nachricht für alle angegebenen Empfänger im Dateisystem
 * Für jeden Empfänger wird ein Verzeichnis unterhalb des Data-Verzeichnis erstellt (falls nicht vorhanden).
 * Die Nachricht wird mit einem Zeitstempel versehen und als Datei gespeichert.
*/
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void storeMessage(String client, String sender, List<String> recipients, String messageBody) throws IOException {

        messageBody = createTimestampLine(client) + messageBody;

        for (final String recipient : recipients) {
            final int messageId = random.nextInt(10_000);
            final String filename = buildFileName(sender, messageId);
            final Path dirPath = Paths.get(BASE_DIR, recipient);
            Files.createDirectories(dirPath);
            final Path filePath = dirPath.resolve(filename);
            try (final FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                final byte[] ascii = messageBody.getBytes(StandardCharsets.US_ASCII);
                final ByteBuffer buffer = ByteBuffer.wrap(ascii);
                while (buffer.hasRemaining()) {
                    fileChannel.write(buffer);
                }
            }
        }
    }


    public static String createTimestampLine(String clientName) {
        ZonedDateTime now = ZonedDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yy HH:mm:ss z", Locale.ENGLISH);
        String dateTime = now.format(formatter);

        return "Received: FROM " + clientName + " BY SMTPServer ; " + dateTime +"\n";
    }

}