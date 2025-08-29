package smtp.model;

import file.MailStorageService;
import lombok.Getter;
import smtp.util.SMTPCommandParser;
import lombok.extern.java.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ClientSessionState verwaltet den Zustand einer SMTP-Client-Sitzung
 * speichert Informationen über den aktuellen Zustand, den Absender, die Empfänger und die Nachrichtendaten
 */
@Log
public class ClientSessionState {
    @Getter
    private final ByteBuffer clientReadBuffer;
    private ClientSMTPState currentState;

    private String clientName;
    private String mailFromSender;
    private final List<String> rcptToRecipients = new ArrayList<>();

    //definiert User, die E-Mails empfangen dürfen
    private static final Set<String> validRecipients = Set.of(
            "abc@def.edu",
            "ghi@jkl.com",
            "nmo@pqr.gov",
            "stu@vwx.de"
    );
    private StringBuilder messageData = new StringBuilder();

    private MailStorageService filestorage = new MailStorageService();

    public ClientSessionState(ByteBuffer clientReadBuffer) {
        this.clientReadBuffer = clientReadBuffer;
        this.currentState = ClientSMTPState.CONNECTED;
    }
    // Definiert verschiedene Zustände, in denen sich ein SMTP-Client befinden kann
    public enum ClientSMTPState {
        CONNECTED,
        WAITING_MAIL_FROM,
        WAITING_RCPT_TO,
        WAITING_DATA,
        RECEIVING_DATA,
        QUIT_SENT,
        CLOSING
    }

    public void setCurrentState(ClientSMTPState currentState) {
        this.currentState = currentState;
        log.info("Client state changed to: " + currentState);
    }

    //Diese Methode liest den ByteBuffer zeilenweise (\r\n als Zeilenende) und gibt eine Liste von Antworten zurück
    public List<String> processReadBuffer() {
        List<String> responses = new ArrayList<>();


        clientReadBuffer.flip();
        int lineStart = clientReadBuffer.position();

        try{
            // Iteriert über den ByteBuffer und sucht nach Zeilenenden (\r\n)
            while (clientReadBuffer.hasRemaining()) {
                byte b = clientReadBuffer.get();

                if (b == '\r') {
                    if (clientReadBuffer.hasRemaining()) {
                        byte nextB = clientReadBuffer.get();
                        if (nextB == '\n') {
                            // found line ending \r\n
                            // Zeilenende erkannt, verarbeite die Zeile
                            int lineEnd = clientReadBuffer.position() - 2;
                            int lineLength = lineEnd - lineStart;

                            //formatiert Buffer zu einem String
                            if (lineLength >= 0) {
                                ByteBuffer lineBuffer = clientReadBuffer.duplicate().position(lineStart).limit(lineEnd);
                                byte[] lineBytes = new byte[lineLength];
                                lineBuffer.get(lineBytes);
                                String completeLine = new String(lineBytes, StandardCharsets.US_ASCII);

                                //wenn Server im receiving_data state ist, wird die Zeile als Datenzeile verarbeitet
                                if (currentState == ClientSMTPState.RECEIVING_DATA) {
                                    String dataResponse = processDataLine(completeLine);
                                    if (dataResponse != null) {
                                        responses.add(dataResponse);
                                    }
                                //sonst wird die Zeile als Befehl verarbeitet
                                } else {
                                    String commandResponse = processCommandLine(completeLine);
                                    if (commandResponse != null) {
                                        responses.add(commandResponse);
                                    }
                                }

                                // Setze den Start der nächsten Zeile auf die aktuelle Position
                                lineStart = clientReadBuffer.position();

                                //Zeile ist leer
                            } else {
                                log.warning("Error processing line segment in buffer.");
                                responses.add("500 Internal server error\r\n");
                                return responses;
                            }

                        } else {
                            // found \r but not \n
                            clientReadBuffer.position(clientReadBuffer.position() - 1);
                            clientReadBuffer.position(clientReadBuffer.position() - 1);
                            break;
                        }
                    } else {
                        // found \r as last last byte
                        clientReadBuffer.position(clientReadBuffer.position() - 1);
                        break;
                    }
                    }
                }
            } catch (IllegalArgumentException e) {
                responses.add("500 Syntax error, command unrecognized\r\n");
            } catch (Exception e) {
                responses.add("451 Requested action aborted: local error in processing\r\n");
            }

            clientReadBuffer.compact();

            return responses;
        }

    //Verarbeitet den Befehl und gibt die Antwort zurück
    private String processCommandLine(String commandLine) {
        SMTPCommand command = SMTPCommandParser.parse(commandLine);
        log.info("Parsed command: " + command.getType() + " with args: '" + command.getArguments() + "'");

        SMTPCommandType commandType = command.getType();
        String arguments = command.getArguments();
        String response;

        switch (currentState) {
            //Wenn der Client verbunden ist, wird der HELO-Befehl erwartet
            case CONNECTED:
                if (commandType == SMTPCommandType.HELO) {
                    setCurrentState(ClientSMTPState.WAITING_MAIL_FROM);
                    clientName = arguments;
                    response = "250 " + (arguments != null && !arguments.isBlank() ? arguments : "localhost") + "\r\n";
                } else if (commandType == SMTPCommandType.QUIT) {
                    setCurrentState(ClientSMTPState.QUIT_SENT);
                    response = "221 Tschau Kakao\r\n";
                } else if (commandType == SMTPCommandType.HELP) {

                    response = getHelp(arguments);

                } else {
                    response = "503 Bad sequence of commands\r\n";
                }
                break;

            //Wenn der Client im WAITING_MAIL_FROM-Zustand ist, wird der MAIL FROM-Befehl erwartet
            case WAITING_MAIL_FROM:
                if (commandType == SMTPCommandType.MAIL_FROM) {
                    if (arguments != null && !arguments.isBlank()) {
                        mailFromSender = arguments;
                        rcptToRecipients.clear();
                        messageData = new StringBuilder();
                        setCurrentState(ClientSMTPState.WAITING_RCPT_TO);
                        response = "250 Ok\r\n";
                    } else {
                        response = "501 Syntax error in parameters or arguments\r\n";
                    }
                } else if (commandType == SMTPCommandType.QUIT) {
                    setCurrentState(ClientSMTPState.QUIT_SENT);
                    response = "221 Tschau Kakao\r\n";
                } else if (commandType == SMTPCommandType.HELP) {

                    response = getHelp(arguments);

                } else {
                    response = "503 Bad sequence of commands\r\n";
                }
                break;

            //Wenn der Client im WAITING_RCPT_TO-Zustand ist, wird der RCPT TO-Befehl erwartet
            case WAITING_RCPT_TO:
                if (commandType == SMTPCommandType.RCPT_TO) {
                    if (arguments != null && !arguments.isBlank()) {
                        if (validRecipients.contains(arguments)) {
                            if (!rcptToRecipients.contains(arguments)) {
                                rcptToRecipients.add(arguments);
                                response = "250 Ok\r\n";
                            } else {
                                response = "550 duplicate recipient not allowed\r\n";
                            }
                        } else {
                            response = "550 recipient not found: unknown recipient\r\n";
                        }
                    } else {
                        response = "501 Syntax error in parameters or arguments\r\n";
                    }
                } else if (commandType == SMTPCommandType.DATA) {
                    if (!rcptToRecipients.isEmpty()) {
                        setCurrentState(ClientSMTPState.RECEIVING_DATA);
                        response = "354 Start mail input; end with <CRLF>.<CRLF>\r\n";
                        messageData = new StringBuilder();
                    } else {
                        response = "554 No valid recipients\r\n";
                    }
                } else if (commandType == SMTPCommandType.QUIT) {
                    setCurrentState(ClientSMTPState.QUIT_SENT);
                    response = "221 Tschau Kakao\r\n";
                } else if (commandType == SMTPCommandType.HELP) {

                    response = getHelp(arguments);

                } else {
                    response = "503 Bad sequence of commands\r\n";
                }
                break;

            // keine Antwort, wenn der Client QUIT gesendet hat oder der Server schließt
            case QUIT_SENT, CLOSING:
                response = null;
                break;

            // default-case für alle anderen schlechten Zustände
            default:
                response = "500 Internal server error (unknown state)\r\n";
                log.severe("Client in unknown state: " + currentState + " received command: " + commandLine);
                break;
        }

        return response;
    }





    //listet alle Befehle auf oder gibt eine Beschreibung des angegebenen Befehls zurück
    private String getHelp(String arguments) {
        return switch (arguments) {
            case null -> "214 Supported commands: HELO, MAIL FROM, RCPT TO, DATA, HELP, QUIT\r\n";
            case "HELO" -> "214 HELO <hostname>: identify yourself to the server\r\n";
            case "MAIL", "MAIL FROM" -> "214 MAIL FROM:<address>: specify sender\r\n";
            case "RCPT", "RCPT TO" -> "214 RCPT TO:<address>: specify recipient\r\n";
            case "DATA" -> "214 DATA: send email body, end with <CRLF>.<CRLF>\r\n";
            case "QUIT" -> "214 QUIT: terminate the session\r\n";
            default -> "502 Command not implemented or wrong spelling\r\n";
        };
    }

    //Verarbeitet die Datenzeile und speichert die Nachricht, wenn das Ende erreicht ist
    private String processDataLine(String dataLine) {
        if (".".equals(dataLine.strip())) {
            log.info("End of DATA detected.");


            //Speichert Nachricht im Data-Verzeichnis
            try {
                filestorage.storeMessage(clientName, mailFromSender, rcptToRecipients, messageData.toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            //setzt Zustand zurück
            resetTransactionState();
            setCurrentState(ClientSMTPState.WAITING_MAIL_FROM);
            return "250 Ok: message queued\r\n";
        } else {
            messageData.append(dataLine).append("\n");
            return null;
        }
    }

    public boolean isQuitSent() {
        return currentState == ClientSMTPState.QUIT_SENT;
    }

    private void resetTransactionState() {
        mailFromSender = null;
        rcptToRecipients.clear();
        messageData = new StringBuilder();
        log.info("Transaction state reset.");
    }
}