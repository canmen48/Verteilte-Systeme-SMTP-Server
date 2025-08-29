package smtp.util;

import smtp.model.SMTPCommand;
import smtp.model.SMTPCommandType;

//dient zum Parsen von SMTP-Befehlen
public class SMTPCommandParser {

    //parst Befehlstyp und Argumente aus der Eingabezeile und gibt ein SMTPCommand-Objekt zurück
    public static SMTPCommand parse(String line) {
        final String trimmed = line.strip();
        final SMTPCommandType type = SMTPCommandType.from(trimmed);
        final String args = switch (type) {
            case HELO -> trimmed.substring(type.commandString.length()+1);
            case MAIL_FROM, RCPT_TO -> extractAddress(line, type.commandString.length()+1);
            case DATA, QUIT -> null;
            case HELP -> trimmed.length() > type.commandString.length()
                         ? trimmed.substring(type.commandString.length()+1)
                         : null;
        };
        return new SMTPCommand(type, args);
    }

    //für den Fall das '<' und '>' in der Adresse sind, werden diese entfernt
    private static String extractAddress(String line, int offset) {
        final String address = line.substring(offset);
        if (address.startsWith("<") && address.endsWith(">")) {
            return address.substring(1, address.length() - 1);
        }
        return address;
    }
}
