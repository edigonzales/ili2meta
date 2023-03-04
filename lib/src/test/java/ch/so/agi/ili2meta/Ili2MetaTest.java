package ch.so.agi.ili2meta;

import org.junit.jupiter.api.Test;

import ch.interlis.ili2c.Ili2cException;

public class Ili2MetaTest {
   @Test
   public void foo() throws Exception {
       Settings settings = new Settings();
       //settings.setValue(Settings.ILI_FILE_NAME, "src/test/data/ModelA.ili");
       //settings.setValue(Settings.XML_FILE_NAME, "/Users/stefan/tmp/meta.xml");
       
       Ili2Meta ili2meta = new Ili2Meta();
       //ili2meta.runXX("ModelA", settings);
       
       settings.setValue(Settings.CONFIG_ROOT_DIRECTORY, "src/test/data/thema-config/");
       
       ili2meta.run("ch.so.awjf.seltene_baumarten", "ch.so.awjf.seltene_baumarten", settings);
       
   }
}
