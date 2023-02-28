package ch.so.agi.ili2meta;

import java.text.MessageFormat;

public class Ili2MetaException extends Exception {
    public Ili2MetaException(String msg) {
        super(msg);
    }

    public Ili2MetaException(String msgTemplate, Object... msgArgs) {
        super(MessageFormat.format(msgTemplate, msgArgs));
    }

    public Ili2MetaException(Exception inner) {
        super(inner);
    }
}
