package smtp.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public enum SMTPCommandType {
    HELO("HELO"), //
    MAIL_FROM("MAIL FROM:"), //
    RCPT_TO("RCPT TO:"), //
    DATA("DATA"), //
    QUIT("QUIT"), //
    HELP("HELP");

    public final String commandString;

    SMTPCommandType(String commandString) {
        this.commandString = commandString;
    }

    //formatiert Command in einheitliches Format
    public static SMTPCommandType from(String line) {
        final String trimmed = line.strip().toUpperCase();
        return Arrays.stream(values()) //
                .filter(type -> trimmed.startsWith(type.commandString)) //
                .findFirst() //
                .orElseThrow(() -> new IllegalArgumentException(line));
    }

}
