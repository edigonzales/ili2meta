package ch.so.agi.ili2meta;

import java.util.HashMap;

public class Settings {
    private HashMap<String,String> values = new HashMap<String,String>();

    public static final String ILI_FILE_NAME = "ILI_FILE_NAME";
    public static final String XML_FILE_NAME = "XML_FILE_NAME";
    public static final String CONFIG_ROOT_DIRECTORY = "CONFIG_ROOT_DIRECTORY";
    
    public String getValue(String name) {
        String value = (String)values.get(name);
        return value;
    }

    public void setValue(String name,String value) {
        if (value==null) {
            values.remove(name);
        } else {
            values.put(name, value);
        }
    }
}
