package ch.so.agi.ili2meta;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

//import com.fasterxml.jackson.databind.SerializationFeature;
//import com.fasterxml.jackson.dataformat.toml.TomlMapper;
//import com.fasterxml.jackson.dataformat.xml.XmlMapper;
//import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
//import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ch.interlis.ili2c.Ili2c;
import ch.interlis.ili2c.Ili2cException;
import ch.interlis.ili2c.Ili2cFailure;
import ch.interlis.ili2c.config.Configuration;
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
import ch.interlis.ilirepository.IliManager;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iom_j.xtf.XtfReader;
import ch.interlis.iom_j.xtf.XtfWriter;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox_j.ObjectEvent;
import ch.interlis.iox_j.EndBasketEvent;
import ch.interlis.iox_j.EndTransferEvent;
import ch.interlis.iox_j.StartBasketEvent;
import ch.interlis.iox_j.StartTransferEvent;
import ch.so.agi.ili2meta.derived.Ili2cUtility;
import ch.so.agi.ili2meta.derived.IliMetaAttrNames;
import ch.so.agi.ili2meta.model.AttributeDescription;
import ch.so.agi.ili2meta.model.ClassDescription;
import ch.so.agi.ili2meta.model.DataType;

public class Ili2Meta {
    static Logger log = LoggerFactory.getLogger(Ili2Meta.class);
    
    private static final List<String> META_TOML_CONFIG_RESERVED_WORDS = new ArrayList<String>() {{
        add("basic");
        add("formats");
    }};

    private static final String ILI_MODEL_METADATA = "SO_AGI_Metadata_20230304.ili";

    private static final String ILI_TOPIC = "SO_AGI_Metadata_20230304.ThemePublications";
    private static final String BID = "SO_AGI_Metadata_20230304.ThemePublications";
    private static final String TAG = "SO_AGI_Metadata_20230304.ThemePublications.ThemePublication";

    private Map<String, ClassDescription> getModelDescription(String modelName, String themeRootDirectory) throws Ili2cException, IOException {
        String localRepo = Paths.get(themeRootDirectory, "ili").toFile().getAbsolutePath();
        
        TransferDescription td = getTransferDescriptionFromModelName(modelName, localRepo);
        
        Map<String, ClassDescription> classTypes = new HashMap<>();

        for (Model model : td.getModelsFromLastFile()) {
            if (!model.getName().equalsIgnoreCase(modelName)) {
                continue;
            }

            Iterator<Element> modeli = model.iterator();
            while (modeli.hasNext()) {
                Object tObj = modeli.next();

                if (tObj instanceof Domain) {
                    // Falls ich die Werte will.
                } else if (tObj instanceof Table) {
                    Table tableObj = (Table) tObj;
                    // https://github.com/claeis/ili2c/blob/ccb1331428/ili2c-core/src/main/java/ch/interlis/ili2c/metamodel/Table.java#L30
                    if (tableObj.isIdentifiable()) {
                        // Abstrakte Klasse:
                        // Kann ich ignorieren, da alles was ich wissen will (? Attribute und
                        // Beschreibung) in den spezialisieren Klassen vorhanden ist.
                    } else {
                        // Struktur:
                        // Momentan nicht von Interesse.
                    }
                } else if (tObj instanceof Topic) {
                    Topic topic = (Topic) tObj;
                    Iterator<?> iter = topic.getViewables().iterator();

                    while (iter.hasNext()) {
                        Object obj = iter.next();

                        // Viewable wäre "alles". Was ist sinnvoll/notwendig für unseren Usecase?
                        // Domains?
                        // Momentan nur Table berücksichtigen.
                        if (obj instanceof Table) {
                            Table table = (Table) obj;

                            // Abstrakte Klasse oder Struktur:
                            // Abstrakte Klasse interessiert uns nicht, da alle
                            // Attribute in der spezialisierte Klass vorhanden sind.
                            // Struktur interessiert uns vielleicht später aber
                            // zum jetzigen Zeitpunkt brauchen wir dieses Wissen
                            // nicht.
                            if (table.isAbstract() || !table.isIdentifiable()) {
                                continue;
                            }

                            ClassDescription classType = new ClassDescription();
                            classType.setName(table.getName());
                            classType.setTitle(table.getMetaValue("title"));
                            classType.setDescription(table.getDocumentation());
                            classType.setModelName(modelName);
                            classType.setTopicName(topic.getName());

                            Iterator<?> attri = table.getAttributes();

                            List<AttributeDescription> attributes = new ArrayList<>();
                            while (attri.hasNext()) {
                                Object aObj = attri.next();
                                AttributeDescription attributeType = new AttributeDescription();

                                if (aObj instanceof AttributeDef) {
                                    AttributeDef attr = (AttributeDef) aObj;
                                    attributeType.setName(attr.getName());
                                    attributeType.setDescription(attr.getDocumentation());

                                    Type type = attr.getDomainResolvingAll();
                                    attributeType.setMandatory(type.isMandatory() ? true : false);

                                    if (type instanceof TextType) {
                                        TextType t = (TextType) type; 
                                        attributeType.setDataType(t.isNormalized() ? DataType.TEXT : DataType.MTEXT);
                                    } else if (type instanceof NumericType) {
                                        NumericType n = (NumericType) type;
                                        attributeType.setDataType(n.getMinimum().getAccuracy() == 0 ? DataType.INTEGER : DataType.DOUBLE);
                                    } else if (type instanceof EnumerationType) {
                                        EnumerationType e = (EnumerationType) type;
                                        attributeType.setDataType(DataType.ENUMERATION);
                                    } else if (type instanceof SurfaceOrAreaType) {
                                        SurfaceOrAreaType s = (SurfaceOrAreaType) type;
                                        attributeType.setDataType(DataType.POLYGON);
                                    } else if (type instanceof PolylineType) {
                                        PolylineType p = (PolylineType) type;
                                        attributeType.setDataType(DataType.LINESTRING);
                                    } else if (type instanceof CoordType) {
                                        CoordType c = (CoordType) type;
                                        attributeType.setDataType(DataType.POINT);
                                    } else if (type instanceof FormattedType) {
                                        FormattedType f = (FormattedType) type;
                                        String format = f.getFormat();
                                        if (format.contains("Year") && !format.contains("Hours")) {
                                            attributeType.setDataType(DataType.DATE);
                                        } else if (format.contains("Year") && format.contains("Hours")) {
                                            attributeType.setDataType(DataType.DATETIME);
                                        }
                                        // else if...
                                    } else if (type instanceof CompositionType) {
                                        CompositionType c = (CompositionType) type;

                                        if (attr.getMetaValue(IliMetaAttrNames.METAATTR_MAPPING)!= null && attr.getMetaValue(IliMetaAttrNames.METAATTR_MAPPING).equals(IliMetaAttrNames.METAATTR_MAPPING_JSON)) {
                                            attributeType.setDataType(DataType.JSON_TEXT);
                                        } else {
                                            Table struct = c.getComponentType();

                                            // Wenn es keine richtigen Multigeometrie-Datentypen
                                            // gibt, geht es nicht 100% robust.
                                            if (c.getCardinality().getMaximum() != 1) {
                                                attributeType.setDataType(DataType.UNDEFINED); // oder DataType.STRUCTURE ?
                                            } else {
                                                if (Ili2cUtility.isPureChbaseMultiSurface(td, attr)) {
                                                    attributeType.setDataType(DataType.MULTIPOLYGON);
                                                } else if (Ili2cUtility.isPureChbaseMultiLine(td, attr)) {
                                                    attributeType.setDataType(DataType.MULTILINESTRING);
                                                } else {
                                                    String metaValue = struct.getMetaValue(IliMetaAttrNames.METAATTR_MAPPING);

                                                    if (metaValue == null) {
                                                        attributeType.setDataType(DataType.UNDEFINED);
                                                    } else if (metaValue
                                                            .equals(IliMetaAttrNames.METAATTR_MAPPING_MULTISURFACE)) {
                                                        attributeType.setDataType(DataType.MULTIPOLYGON);
                                                    } else if (metaValue
                                                            .equals(IliMetaAttrNames.METAATTR_MAPPING_MULTILINE)) {
                                                        attributeType.setDataType(DataType.MULTILINESTRING);
                                                    } else if (metaValue
                                                            .equals(IliMetaAttrNames.METAATTR_MAPPING_MULTIPOINT)) {
                                                        attributeType.setDataType(DataType.MULTIPOINT);
                                                    } else {
                                                        attributeType.setDataType(DataType.UNDEFINED);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                attributes.add(attributeType);
                            }
                            classType.setAttributes(attributes);
                            classTypes.put(classType.getQualifiedName(), classType);
                        } // else if... DOMAIN, etc.? DOMAIN nur, falls ich die Werte wirklich ausweisen will.
                    }
                }
            }
        }
        return classTypes;
    }
    
    private void overrideModelDescription(Map<String, ClassDescription> classDescriptions, TomlParseResult metaTomlResult) {
        Map<String, Object> metaTomlMap = metaTomlResult.toMap();
        for (Map.Entry<String, Object> entry : metaTomlMap.entrySet()) {
            if (!META_TOML_CONFIG_RESERVED_WORDS.contains(entry.getKey())) {
                String modelName = (String) entry.getKey();
                parseTopicDesc(classDescriptions, modelName, (TomlTable) entry.getValue());
            }      
        }
    }
    
    private void parseTopicDesc(Map<String, ClassDescription> classDescriptions, String modelName, TomlTable topicDescs) {
        for (Map.Entry<String, Object> entry : topicDescs.entrySet()) {
            String topicName = (String) entry.getKey();
            parseClassDesc(classDescriptions, modelName, topicName, (TomlTable) entry.getValue());   
        }
    }
    
    private void parseClassDesc(Map<String, ClassDescription> classDescriptions, String modelName, String topicName, TomlTable classDescs) {
        for (Map.Entry<String, Object> entry : classDescs.entrySet()) {
            String className = (String) entry.getKey();
            
            TomlTable classDesc = (TomlTable) entry.getValue();
            String title = classDesc.getString("title");
            String description = classDesc.getString("description");

            String qualifiedClassName = modelName + "." + topicName + "." + className;
            System.out.println("qualifiedClassName: " + qualifiedClassName);
            System.out.println("className: " + className);
            System.out.println("title: " + title);
            System.out.println("description: " + description);
            
            ClassDescription classDescription = classDescriptions.get(qualifiedClassName);
            System.out.println(classDescription);
            
            if (title != null) {
                classDescription.setTitle(title);
            } 

            if (description != null) {
                classDescription.setDescription(description);
            }
        }
    }
    
    private void convertOfficeToStructure(IomObject officeObj) {
        officeObj.setobjecttag("SO_AGI_Metadata_20230304.Office_");
        officeObj.setobjectoid(null);
    }

    
    // TODO: wie kann ich alle auf einmal in einem XTF exportieren?
    // run() müsste IomObj zurückliefern.
    // run() nochmals abstrahieren, damit run() nichts zurückliefern muss.
    
    
    /*
     * run(Settings settings) {
     *  // alle publications (-> Modelle) eruieren
     *  for ()  {
     *      IomObject iomObj = getPublicationInfo(themePubliction, theme, settings)
     *  }
     *  // alle iomObjs schreiben
     * } 
     * 
     * run(themePublication, theme, settings) {
     *   IomObject iomObj = getPublicationInfo(themePubliction, theme, settings)
     * }
     * 
     *  
     */
    
    
    /*
     * Mmmh, die Frage kann auch sagen: WANN schreibe ich das gesamte XML? Nicht nach einem
     * normalen GRETL-Job. Woher soll man dann die jeweiligen Publikationsdatum kennen?
     * Ist es nicht "richtiger" die XTF auf dem SFTP zu publizieren und die Datensuche
     * holt die XTF von dort. Müsste auch mit S3 oder so gehen, da es immer eine Logik gibt,
     * die relativ simple ist.
     */
    
    
    // Woher nehme ich im "alles"-Anwendungsfall das Publiaktionsdatum?
    // Im Einzelfall gehe ich momentan davon aus, dass das XTF im Gretljob erzeugt wird.
    // Oder es ist gar nicht nötig (wobei ich das bissle schade finde). Datensuche 
    // könnten einfach alle ~150 XTF irgendwo herunterladen und lesen.
    // Ah, Publikationsdatum der Subunits ist ja noch ein Knackpunkt...
    // Im hardodierten Fall wohl einfach -> hardcodiert.
    // Aber AV und NPL??
    // Dienste sind natürlich noch ne Info, die so nicht wirklich machbar ist.
    // Das geht erst, wenn alles im Repo ist, dann ist auch diese Info vorhanden.
    
    
    // Wahrscheinlich ist themePublication eineindeutig. Sicherheitshalber mit theme.
    // Zudem einfacher zu programmieren, das nicht mehr durchsucht werden muss.
    // (0) Modellnamen aus Toml-Datei lesen.
    // (1) Zuerst wird das Modell geparsed.
    // (2) Anschliessend die Toml-Datei mit zusätzlichen Informationen lesen und ggf. 
    // die aus (1) eruierten Infos überschreiben.
    // (3) XTF schreiben
    public void run(String themePublication, String theme, Settings settings) {
        String configRootDirectory = settings.getValue(Settings.CONFIG_ROOT_DIRECTORY);
        String themeRootDirectory = Paths.get(configRootDirectory, theme).toFile().getAbsolutePath();
        File tomlFile = Paths.get(configRootDirectory, theme, "publication", themePublication, "meta.toml").toFile();

        try {
            TomlParseResult metaTomlResult = Toml.parse(tomlFile.toPath());

            // (0) Modellnamen wird auch für (1) benötigt.
            String modelName = metaTomlResult.getString("basic.model");
        
            // (1) Informationen aus ILI-Modell lesen.
            Map<String, ClassDescription> classDescriptions = getModelDescription(modelName, themeRootDirectory);
        
            // (2) Weitere Informationen aus Toml-Datei lesen und ggf. Modellinformationen übersteuern.
            String identifier = metaTomlResult.getString("basic.identifier");
            String title = metaTomlResult.getString("basic.title");
            String description = metaTomlResult.getString("basic.description");
            String keywords = metaTomlResult.getString("basic.keywords");
            String synonyms = metaTomlResult.getString("basic.synonyms");
            String owner = metaTomlResult.getString("basic.owner");
            String servicer = metaTomlResult.getString("basic.servicer");
            String licence = metaTomlResult.getString("basic.licence");
            String furtherInformation = metaTomlResult.getString("basic.furtherInformation");
            
            overrideModelDescription(classDescriptions, metaTomlResult);

            IomObject servicerIomObject = getOfficeById(servicer, configRootDirectory);
            IomObject ownerIomObject = getOfficeById(owner, configRootDirectory);

            // (3) XTF schreiben.
            String outputDirectory = Paths.get(configRootDirectory, theme, "publication", themePublication).toFile().getAbsolutePath();
            IoxWriter ioxWriter = createMetaIoxWriter(outputDirectory, identifier);
            ioxWriter.write(new StartTransferEvent("SOGIS-20230305", "", null));
            ioxWriter.write(new StartBasketEvent(ILI_TOPIC,BID));

            Iom_jObject iomObj = new Iom_jObject(TAG, String.valueOf(1));
            iomObj.setattrvalue("identifier", identifier);
            iomObj.setattrvalue("title", title);
            if (description!=null) iomObj.setattrvalue("shortDescription", description); // CDATA wird nicht berücksichtigt, d.h. auch mit einem CDATA-Block werden die "<"-Zeichen etc. escaped.
            iomObj.setattrvalue("licence", licence);
            if (furtherInformation!=null) iomObj.setattrvalue("furtherInformation", keywords);            
            if (keywords!=null) iomObj.setattrvalue("keywords", keywords);
            if (synonyms!=null) iomObj.setattrvalue("synonyms", synonyms);
            
            if (servicerIomObject!=null) {
                convertOfficeToStructure(servicerIomObject); 
                iomObj.addattrobj("servicer", servicerIomObject);
            }

            if (ownerIomObject!=null) {
                convertOfficeToStructure(ownerIomObject); 
                iomObj.addattrobj("owner", ownerIomObject);   
            }

            // TODO: add missing attributes etc.
            
            for (Map.Entry<String, ClassDescription> entry : classDescriptions.entrySet()) {
                Iom_jObject classDescObj = new Iom_jObject("SO_AGI_Metadata_20230304.ClassDescription", null); 

                ClassDescription classDescription = entry.getValue();

                classDescObj.setattrvalue("name", classDescription.getName());
                classDescObj.setattrvalue("title", classDescription.getTitle());
                classDescObj.setattrvalue("shortDescription", classDescription.getDescription());

                List<AttributeDescription> attributeDescriptions = classDescription.getAttributes();
                for (AttributeDescription attributeDescription : attributeDescriptions) {
                    Iom_jObject attributeDescObj = new Iom_jObject("SO_AGI_Metadata_20230304.AttributeDescription", null); 

                    attributeDescObj.setattrvalue("name", attributeDescription.getName());
                    if (attributeDescription.getDescription()!=null) attributeDescObj.setattrvalue("shortDescription", attributeDescription.getDescription());
                    attributeDescObj.setattrvalue("dataType", attributeDescription.getDataType().name());
                    attributeDescObj.setattrvalue("isMandatory", attributeDescription.isMandatory()?"true":"false");
                    classDescObj.addattrobj("attributeDescription", attributeDescObj);
                }
                iomObj.addattrobj("classDescription", classDescObj);
            }
            
            ioxWriter.write(new ObjectEvent(iomObj));
            
            ioxWriter.write(new EndBasketEvent());
            ioxWriter.write(new EndTransferEvent());
            ioxWriter.flush();
            ioxWriter.close();       
        } catch (IOException | Ili2cException | IoxException e) {
            e.printStackTrace();
        }
    }

    private IomObject getOfficeById(String id, String configRootDirectory) throws IOException, IoxException {
        File xtfFile = Paths.get(configRootDirectory, "shared", "core_data", "offices.xtf").toFile();
        System.out.println(xtfFile.getAbsolutePath());
        XtfReader xtfReader = new XtfReader(xtfFile);
        
        IoxEvent event = xtfReader.read();
        while (event instanceof IoxEvent) {
            if (event instanceof ObjectEvent) {
                ObjectEvent objectEvent = (ObjectEvent) event;
                IomObject iomObj = objectEvent.getIomObject();
                if (iomObj.getobjectoid().equalsIgnoreCase(id)) {
                    return iomObj;
                }
            }
            event = xtfReader.read();
        }
        return null;
    }

    private IoxWriter createMetaIoxWriter(String outDirectory, String identifier) throws IOException, Ili2cFailure, IoxException {
        TransferDescription td = getMetadataTransferdescription();
        
        File dataFile = Paths.get(outDirectory, "meta-"+identifier+".xtf").toFile();
        IoxWriter ioxWriter = new XtfWriter(dataFile, td);
        
        return ioxWriter;
    }    

    private TransferDescription getMetadataTransferdescription() throws IOException, Ili2cFailure {        
        String tmpdir = System.getProperty("java.io.tmpdir");
        File iliFile = Paths.get(tmpdir, ILI_MODEL_METADATA).toFile();
        InputStream resource = Ili2Meta.class.getResourceAsStream("/ili/"+ILI_MODEL_METADATA);
        Files.copy(resource, iliFile.toPath(), StandardCopyOption.REPLACE_EXISTING);        
        iliFile.delete();

        ArrayList<String> filev = new ArrayList<String>() {{ add(iliFile.getAbsolutePath()); }};
        TransferDescription td = Ili2c.compileIliFiles(filev, null);

        if (td == null) {
            throw new IllegalArgumentException("INTERLIS compiler failed");
        }

        return td;
    }
    
    private TransferDescription getTransferDescriptionFromModelName(String modelName, String localRepo) throws Ili2cException, IOException {
        IliManager manager = new IliManager();
        File ilicacheFolder = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), ".ilicache_").toFile();        
        manager.setCache(ilicacheFolder); // TODO https://github.com/edigonzales/ili2meta/issues/1
        String repositories[] = new String[] { localRepo, "http://models.interlis.ch/" };
        manager.setRepositories(repositories);
        ArrayList<String> modelNames = new ArrayList<String>();
        modelNames.add(modelName);
        Configuration config = manager.getConfig(modelNames, 2.3);
        TransferDescription td = Ili2c.runCompiler(config);

        if (td == null) {
            throw new IllegalArgumentException("INTERLIS compiler failed"); 
        }
        
        return td;
    }
}
