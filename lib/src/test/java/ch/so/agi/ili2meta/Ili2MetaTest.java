package ch.so.agi.ili2meta;

import org.junit.jupiter.api.Test;

import ch.interlis.ili2c.Ili2cException;

public class Ili2MetaTest {
   @Test
   public void foo() throws Exception {
       Ili2Meta ili2meta = new Ili2Meta();
       ili2meta.run("src/test/data/ModelA.ili", "ModelA");
   }
}
