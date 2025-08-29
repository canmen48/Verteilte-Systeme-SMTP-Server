package smtp.model;

import lombok.Getter;

@Getter
public class SMTPCommand {
    private final SMTPCommandType type;
    private final String arguments;

    public SMTPCommand(SMTPCommandType type, String arguments) {
        this.type = type;
        this.arguments = arguments;
    }
}
