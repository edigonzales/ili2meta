package ch.so.agi.ili2meta;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.interlis.ili2c.Ili2c;
import ch.interlis.ili2c.Ili2cException;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.interlis.ilirepository.IliManager;
import ch.so.agi.ili2meta.model.ClassType;

public class Ili2Meta {
    static Logger log = LoggerFactory.getLogger(Ili2Meta.class);

    // Verschiedene Möglichkeiten?
    // - by file and name
    // - by repo und name
    // - by name only?
    // - was ist mit Versionen? Für uns spielt das keine Rolle (?).
    
    // Weiss noch nicht genau, was der Output ist (welche "Einheit").
    // Es kann mehrere Modelle in einer Datei geben, was machen wir damit?
    // -> Gefühlt möchte ich wohl immer nur ein Modell beschreiben -> Siehe Fragen oben.
    
    
    public void run(String fileName, String modelName) throws Ili2cException {
        log.info("Hallo Welt.");
        
        List<ClassType> classTypes = new ArrayList<>();
        
        TransferDescription td = getTransferDescriptionFromFileName(fileName);
        
        for (Model model : td.getModelsFromLastFile()) {
            if (!model.getName().equalsIgnoreCase(modelName)) {
                continue;
            }
            
            Iterator<Element> modeli = model.iterator();
            while (modeli.hasNext()) {
                Object tObj = modeli.next();
                
                log.info(tObj.toString());
                log.info(tObj.getClass().toString());
                
                if (tObj instanceof Domain) {
//                    log.info("Ich bin eine Domain.");
//                    log.info("-> Aufzähltypen wegspeichern. Sonst interessiert mich hier nichts (?).");
                } else if (tObj instanceof Table) {
//                    log.info("Ich bin eine Struktur oder abstrakte Klasse, oder ...?");
                    Table tableObj = (Table) tObj;
                    
                    // https://github.com/claeis/ili2c/blob/ccb1331428/ili2c-core/src/main/java/ch/interlis/ili2c/metamodel/Table.java#L30
                    if (tableObj.isIdentifiable()) {
//                        log.info("Abstrakte Klasse");
                        // Kann ich glaube ignorieren, das alles was ich wissen will (? Attribute und Beschreibung) in den spezialisieren Klassen-Objekten vorhanden ist.
                    } else {
//                        log.info("Struktur");
                    }
                } else if (tObj instanceof Topic) {
                    Topic topic = (Topic) tObj;
                    Iterator<?> iter = topic.getViewables().iterator();

                    while (iter.hasNext()) {
                        Object obj = iter.next();
                        log.info(obj.toString());

                        // Viewable ist "alles". Was ist sinnvoll / notwendig für unseren Usecase? Domains?
                        if (obj instanceof Table) {
                            Table t = (Table) obj;
                            log.info(t.toString());
                            
                            // Abstrakte Klasse oder Struktur
                            if (t.isAbstract() || !t.isIdentifiable()) {
                                continue;
                            }
                            
                            
                            String title = t.getMetaValue("title");
                            
                            ClassType classType = new ClassType();
                            classType.setName(t.getName());
                            classType.setTitle(title);
                            classType.setDescription(t.getDocumentation());
                            classType.setModelName(modelName);
                            classType.setTopicName(topic.getName());
                            classTypes.add(classType);
                            
                            
                            
                            
                            
//                            if(isPureRefAssoc(v)) {
//                                continue;
//                            }

//                            String tableName = t.getScopedName(null);
//                            Iterator<?> attri = t.getAttributes();

//                            while (attri.hasNext()) {
//                                Object aObj = attri.next();
//                                log.info("aObj: " + aObj);
//                            }
                            
                        } // else if... DOMAIN, etc?
                        
                        
                    }
                }
                
                
            }

            
        }
        
        for (ClassType classType : classTypes) {
            System.out.println(classType.getQualifiedName());
            System.out.println(classType.getTitle());
            System.out.println(classType.getDescription());
        }
        
        
    }
    
    private TransferDescription getTransferDescriptionFromFileName(String fileName) throws Ili2cException {
        IliManager manager = new IliManager();
        String repositories[] = new String[] { "https://geo.so.ch/models", "http://models.geo.admin.ch/", "http://models.interlis.ch/", "http://models.kkgeo.ch/", "." };
        manager.setRepositories(repositories);
        
        ArrayList<String> ilifiles = new ArrayList<String>();
        ilifiles.add(fileName);
        Configuration config = manager.getConfigWithFiles(ilifiles);
        ch.interlis.ili2c.metamodel.TransferDescription iliTd = Ili2c.runCompiler(config);
                
        if (iliTd == null) {
            throw new IllegalArgumentException("INTERLIS compiler failed");
        }
        return iliTd;
    }
    
    public static boolean isPureRefAssoc(Viewable v) {
        if (!(v instanceof AssociationDef)) {
            return false;
        }
        AssociationDef assoc = (AssociationDef) v;
        // embedded and no attributes/embedded links?
        if (assoc.isLightweight() && !assoc.getAttributes().hasNext()
                && !assoc.getLightweightAssociations().iterator().hasNext()) {
            return true;
        }
        return false;
    }

}
