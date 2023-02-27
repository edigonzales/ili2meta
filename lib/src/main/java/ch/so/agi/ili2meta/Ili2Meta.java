package ch.so.agi.ili2meta;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.Ili2c;
import ch.interlis.ili2c.Ili2cException;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.EnumerationType;
import ch.interlis.ili2c.metamodel.FormattedType;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.NumericType;
import ch.interlis.ili2c.metamodel.PolylineType;
import ch.interlis.ili2c.metamodel.SurfaceOrAreaType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.TextType;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.interlis.ilirepository.IliManager;
import ch.so.agi.ili2meta.derived.Ili2cUtility;
import ch.so.agi.ili2meta.derived.IliMetaAttrNames;
import ch.so.agi.ili2meta.model.AttributeType;
import ch.so.agi.ili2meta.model.ClassType;
import ch.so.agi.ili2meta.model.DataType;

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
    
    
//    public void run(String modelName) {} // Such im Verzeichnis (welches? hardcodiert ../ili/ ?) und geo.so.ch/models
//    public void run(String repo, String modelName) // Sucht im Repo (kann auch Verzeichnis sein) und geo.so.ch/models

    
    public void run(String fileName, String modelName) throws Ili2cException {
        log.info("Hallo Welt.");
        
        List<ClassType> classTypes = new ArrayList<>();
    
        //https://github.com/sogis/interlis-repository-creator/blob/e857016b25cb83ebe518b13409ad31a261385693/src/main/java/ch/so/agi/tasks/InterlisRepositoryCreator.java#L306
        
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
                            Table table = (Table) obj;
                            
                            // Abstrakte Klasse oder Struktur
                            // Abstrakte Klasse interessiert uns nicht, da alle
                            // Attribute in der spezialisierte Klass vorhanden sind.
                            // Struktur interessiert uns vielleicht später aber
                            // zum jetzigen Zeitpunkt brauchen wir dieses Wissen 
                            // nicht.
                            if (table.isAbstract() || !table.isIdentifiable()) {
                                continue;
                            }

                            ClassType classType = new ClassType();
                            classType.setName(table.getName());
                            classType.setTitle(table.getMetaValue("title"));
                            classType.setDescription(table.getDocumentation());
                            classType.setModelName(modelName);
                            classType.setTopicName(topic.getName());

                            String tableName = table.getScopedName(null);
                            Iterator<?> attri = table.getAttributes();

                            List<AttributeType> attributes = new ArrayList<>();
                            while (attri.hasNext()) {
                                Object aObj = attri.next();
                                AttributeType attributeType = new AttributeType();
                                
                                if (aObj instanceof AttributeDef) {
                                    AttributeDef attr = (AttributeDef) aObj;
                                    attributeType.setName(attr.getName());
                                    attributeType.setDescription(attr.getDocumentation());
                                    
                                    Type type = attr.getDomainResolvingAll();  
                                    attributeType.setMandatory(type.isMandatory()?true:false);
                                    
                                    if (type instanceof TextType t) {
                                        attributeType.setDataType(t.isNormalized()?DataType.TEXT:DataType.MTEXT);
                                    } else if (type instanceof NumericType n) {
                                        attributeType.setDataType(n.getMinimum().getAccuracy()==0?DataType.INTEGER:DataType.DOUBLE);
                                    } else if (type instanceof EnumerationType e) {
                                       attributeType.setDataType(DataType.ENUMERATION);
                                    } else if (type instanceof SurfaceOrAreaType s) {
                                       attributeType.setDataType(DataType.POLYGON);
                                    } else if (type instanceof PolylineType p) {
                                        attributeType.setDataType(DataType.LINESTRING);
                                    } else if (type instanceof CoordType c) {
                                        attributeType.setDataType(DataType.POINT);
                                    } else if (type instanceof FormattedType f) {
                                        String format = f.getFormat();
                                        if (format.contains("Year") && !format.contains("Hours")) {
                                            attributeType.setDataType(DataType.DATE);
                                        } else if (format.contains("Year") && format.contains("Hours")) {
                                            attributeType.setDataType(DataType.DATETIME);
                                        }
                                        // else if... 
                                    } 
                                    else if (type instanceof CompositionType c) {
                                       // So richtig robust ist es nicht. Aber wenn es
                                       // keine richtigen Multigeometrie-Datentypen
                                       // gibt, geht es nicht 100% robust.
                                       if (c.getCardinality().getMaximum() != 1) {
                                           attributeType.setDataType(DataType.UNDEFINED);
                                       } else {
                                           Table struct = c.getComponentType();
                                           
                                           if (Ili2cUtility.isPureChbaseMultiSurface(td, attr)) {
                                               attributeType.setDataType(DataType.MULTIPOLYGON);
                                           } else if (Ili2cUtility.isPureChbaseMultiLine(td, attr)) {
                                               attributeType.setDataType(DataType.MULTILINESTRING);
                                           } else {
                                               String metaValue = struct.getMetaValue(IliMetaAttrNames.METAATTR_MAPPING);
                                               
                                               // TODO: JSON
                                           
                                               if (metaValue == null) {
                                                   attributeType.setDataType(DataType.UNDEFINED);
                                               } else if (metaValue.equals(IliMetaAttrNames.METAATTR_MAPPING_MULTISURFACE)) {
                                                   attributeType.setDataType(DataType.MULTIPOLYGON);
                                               } else if (metaValue.equals(IliMetaAttrNames.METAATTR_MAPPING_MULTILINE)) {
                                                   attributeType.setDataType(DataType.MULTILINESTRING);
                                               } else if (metaValue.equals(IliMetaAttrNames.METAATTR_MAPPING_MULTIPOINT)) {
                                                   attributeType.setDataType(DataType.MULTIPOINT);
                                               } else {
                                                   attributeType.setDataType(DataType.UNDEFINED);
                                               }
                                           }
                                       }
                                    } 
                                }
                                attributes.add(attributeType);
                            }
                            classType.setAttributes(attributes);
                            classTypes.add(classType);
                            
                        } // else if... DOMAIN, etc.? DOMAIN nur, falls ich die Werte wirklich ausweisen will.
                    }
                }
            }
        }
        
        for (ClassType classType : classTypes) {
            System.out.println(classType.toString());
//            System.out.println(classType.getQualifiedName());
//            System.out.println(classType.getTitle());
//            System.out.println(classType.getDescription());
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
    


}
