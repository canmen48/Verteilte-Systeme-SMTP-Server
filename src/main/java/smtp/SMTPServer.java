package smtp;

import lombok.extern.java.Log;
import smtp.model.ClientSessionState;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * SMTPServer implementiert einen einfachen, nicht-blockierenden SMTP-Server mit Java NIO.
 *
 * Hauptfunktionen:
 * - Startet einen Server, der auf eingehende SMTP-Verbindungen wartet.
 * - Akzeptiert neue Client-Verbindungen und sendet ein Begrüßungsbanner.
 * - Liest und verarbeitet SMTP-Befehle von Clients (z.\ B. HELO, MAIL FROM, RCPT TO, DATA, QUIT).
 * - Verwaltet den Sitzungszustand jedes Clients über ClientSessionState.
 * - Gibt SMTP-Antworten an den Client zurück und speichert E-Mails über MailStorageService.
 * - Unterstützt mehrere gleichzeitige Verbindungen durch Verwendung von Java NIO Selectors.
 *
 * Der Server kann über die main-Methode gestartet werden. Der Port ist konfigurierbar (Standard: 8025).
 */

@Log
public class SMTPServer {
    private Selector selector;
    private final InetSocketAddress listenAddress;
    private static final int DEFAULT_PORT = 8025;
    private ServerSocketChannel serverSocketChannel;

    //Konstruktor
    public SMTPServer(int port) throws IOException {
        this.listenAddress = new InetSocketAddress(port);
    }

    // Startet den SMTP-Server und verarbeitet eingehende Verbindungen sowie Client-Anfragen
    public void startServer() throws IOException {

        selector = Selector.open();
        //Öffnet einen Selector und einen ServerSocketChannel
        serverSocketChannel = ServerSocketChannel.open();

        //Bindet den Server an die konfigurierte Adresse und Port
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(listenAddress);

        //Registriert den ServerSocketChannel für eingehende Verbindungen
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        log.info("Server started on port:" + listenAddress.getPort());

        try {

            //Wartet in einer Schleife auf Ereignisse (Verbindungen oder Daten von Clients)
            while (selector.isOpen()) {
                selector.select();

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        log.info("Acceptable event detected");
                        acceptConnection(key);
                    } else if (key.isReadable()) {
                        log.info("Readable event detected");
                        readData(key);
                    }
                }
            }
            // Wirft IOException bei Netzwerkfehlern
        } catch (IOException e) {
            log.severe("Server exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
            log.info("Server shutting down.");
        }
    }

    //Akzeptiert neue Verbindungen und sendet eine Begrüßung
    private void acceptConnection(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel == null) {
            return;
        }

            clientChannel.configureBlocking(false);
            InetSocketAddress remoteAddress = (InetSocketAddress) clientChannel.getRemoteAddress();
            log.info("Accepted connection from " + remoteAddress);

            SelectionKey clientKey = clientChannel.register(this.selector, SelectionKey.OP_READ);
            ByteBuffer clientReadBuffer = ByteBuffer.allocate(1024);
            clientKey.attach(new ClientSessionState(clientReadBuffer));

            String greeting = "220 SMTP Server Hausaufgabe\r\n";
            ByteBuffer greetingBuffer = ByteBuffer.wrap(greeting.getBytes(StandardCharsets.US_ASCII));
            while (greetingBuffer.hasRemaining()) {
                clientChannel.write(greetingBuffer);
            }
            log.info("Sent to " + remoteAddress + ": " + greeting.replace("\r\n", "<CRLF>\n"));
    }

    //Liest und verarbeitet SMTP-Befehle von verbundenen Clients
    private void readData(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientSessionState clientState = (ClientSessionState) key.attachment();
        ByteBuffer readBuffer = clientState.getClientReadBuffer();

        if (readBuffer == null) {
            log.severe("Error: No buffer attached to key for " + clientChannel.getRemoteAddress());
            closeClientChannel(clientChannel, key);
            return;
        }

        int bytesRead = clientChannel.read(readBuffer);

        if (bytesRead == -1) {
            // connection closed by client
            InetSocketAddress remoteAddress = (InetSocketAddress) clientChannel.getRemoteAddress();
            log.info("Connection closed by client: " + remoteAddress);
            closeClientChannel(clientChannel, key);
            return;
        }

        if (bytesRead > 0) {
            List<String> responses = clientState.processReadBuffer();

            for (String response : responses) {
                if (response != null) {
                    ByteBuffer writeBuffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.US_ASCII));
                    while (writeBuffer.hasRemaining()) {
                        clientChannel.write(writeBuffer);
                    }
                    log.info("Sent to " + clientChannel.getRemoteAddress() + ": " + response.replace("\r\n", "<CRLF>\n"));
                }
            }

            if (clientState.isQuitSent()) {
                log.info("Closing connection for " + clientChannel.getRemoteAddress() + " after QUIT.");
                closeClientChannel(clientChannel, key);
            }
        }
    }

    //Schließt Verbindungen nach QUIT oder bei Fehlern
    private void closeClientChannel(SocketChannel clientChannel, SelectionKey key) {
        try {
            clientChannel.close();
            key.cancel();
        } catch (IOException e) {
            log.severe("Error closing client channel: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Main-Methode zum Starten des SMTP-Servers.
     * Nimmt einen optionalen Port-Parameter entgegen (Standard: 8025).
     *
     * @param args Kommandozeilenargumente
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                log.severe("Invalid port argument. Using default port " + DEFAULT_PORT);
            }
        }

        try {
            SMTPServer server = new SMTPServer(port);
            server.startServer();
        } catch (IOException e) {
            log.severe("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}