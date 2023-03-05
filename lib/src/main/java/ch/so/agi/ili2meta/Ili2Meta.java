package ch.so.agi.ili2meta;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ch.interlis.ili2c.Ili2c;
import ch.interlis.ili2c.Ili2cException;
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

    // Verschiedene Möglichkeiten?
    // - by file and name
    // - by repo und name
    // - by name only?
    // - was ist mit Versionen? Für uns spielt das keine Rolle (?).

    // Weiss noch nicht genau, was der Output ist (welche "Einheit").
    // Es kann mehrere Modelle in einer Datei geben, was machen wir damit?
    // -> Gefühlt möchte ich wohl immer nur ein Modell beschreiben -> Siehe Fragen
    // oben.

//    public void run(String modelName) {} // Such im Verzeichnis (welches? hardcodiert ../ili/ ?) und geo.so.ch/models
//    public void run(String repo, String modelName) // Sucht im Repo (kann auch Verzeichnis sein) und geo.so.ch/models

    private void writeXml(String xmlFileName) throws IOException {
        XmlMapper xmlMapper = new XmlMapper();
        xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
        xmlMapper.enable(SerializationFeature.INDENT_OUTPUT);
        xmlMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        xmlMapper.registerModule(new JavaTimeModule());
        
     
        XMLOutputFactory xof = XMLOutputFactory.newFactory();
        try {
            XMLStreamWriter xsw = xof.createXMLStreamWriter(new FileWriter(new File(xmlFileName)));
            xsw.writeStartDocument("utf-8", "1.0");
            xsw.writeStartElement("themePublications");
            
            // <tablesInfo><tableInfo>
            
            // Ah ich brauch das Modell als Fremdschlüssel
            
//            while(themePublicationsIterator.hasNext()) {
//                var themePub = themePublicationsIterator.next();
//                xmlMapper.writeValue(xsw, themePub);
//            }
            
            xsw.writeEndElement();
            xsw.writeEndDocument();
            xsw.flush();
            xsw.close();
            
        } catch (XMLStreamException | IOException e) {
            //e.printStackTrace();
            log.error(e.getMessage());
            throw new IOException(e.getMessage());
        }

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

    private Map<String, ClassDescription> parseModel(String modelName, String themeRootDirectory) throws Ili2cException, IOException {
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
    
    private void overrideModelInfo(Map<String, ClassDescription> classDescriptions, TomlParseResult metaTomlResult) {
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
        }
    }

    // Wahrscheinlich ist themePublication eineindeutig. Sicherheitshalber mit theme.
    // Zudem einfacher zu programmieren, das nicht mehr durchsucht werden muss.
    // (0) Modellnamen aus Toml-Datei lesen.
    // (1) Zuerst wird das Modell geparsed.
    // (2) Anschliessend die Toml-Datei mit zusätzlichen Informationen lesen und ggf. 
    // die aus (1) eruierten Infos überschreiben.
    public void run(String themePublication, String theme, Settings settings) {
        String configRootDirectory = settings.getValue(Settings.CONFIG_ROOT_DIRECTORY);
        String themeRootDirectory = Paths.get(configRootDirectory, theme).toFile().getAbsolutePath();
        File tomlFile = Paths.get(configRootDirectory, theme, "publication", themePublication, "meta.toml").toFile();

        try {
            TomlParseResult metaTomlResult = Toml.parse(tomlFile.toPath());

            // (0) Modellnamen wird auch für (1) benötigt.
            String modelName = metaTomlResult.getString("basic.model");
        
            // (1) Informationen aus ILI-Modell lesen.
            Map<String, ClassDescription> classDescriptions = parseModel(modelName, themeRootDirectory);
        
            // (2) Weitere Informationen aus Toml-Datei lesen und ggf. Modellinformationen übersteuern.
            String identifier = metaTomlResult.getString("basic.identifier");
            String title = metaTomlResult.getString("basic.title");
            String description = metaTomlResult.getString("basic.description");
            String keywords = metaTomlResult.getString("basic.keywords");
            String synonmys = metaTomlResult.getString("basic.synonmys");
            String owner = metaTomlResult.getString("basic.owner");
            String servicer = metaTomlResult.getString("basic.servicer");
            
            // ACHTUNG: owner und servicer sind bis jetzt nur "link".
            // Ich muss noch das Modell anpassen und ein XTF mit den Dienststellen machen und auch einlesen in eine Map.
            
            overrideModelInfo(classDescriptions, metaTomlResult);
                        
        } catch (IOException | Ili2cException e) {
            e.printStackTrace();
        }
    }
    
//    public void runXX(String modelName, Settings settings) throws Ili2MetaException {
//        List<ClassDescription> classTypes = new ArrayList<>();
//
//        // https://github.com/sogis/interlis-repository-creator/blob/e857016b25cb83ebe518b13409ad31a261385693/src/main/java/ch/so/agi/tasks/InterlisRepositoryCreator.java#L306
//        // getTdFromModelName
//
//        // TODO ...
//        String fileName = settings.getValue(settings.ILI_FILE_NAME);
//        
//        TransferDescription td = null;
//        try {
//            td = getTransferDescriptionFromFileName(fileName);
//        } catch (Ili2cException e) {
//            throw new Ili2MetaException(e);
//        }
//
//        for (Model model : td.getModelsFromLastFile()) {
//            if (!model.getName().equalsIgnoreCase(modelName)) {
//                continue;
//            }
//
//            Iterator<Element> modeli = model.iterator();
//            while (modeli.hasNext()) {
//                Object tObj = modeli.next();
//
////                log.info(tObj.toString());
////                log.info(tObj.getClass().toString());
//
//                if (tObj instanceof Domain) {
//                    // TODO falls ich die Werte will
//                } else if (tObj instanceof Table) {
//                    Table tableObj = (Table) tObj;
//                    // https://github.com/claeis/ili2c/blob/ccb1331428/ili2c-core/src/main/java/ch/interlis/ili2c/metamodel/Table.java#L30
//                    if (tableObj.isIdentifiable()) {
//                        // Abstrakte Klasse
//                        // Kann ich ignorieren, da alles was ich wissen will (? Attribute und
//                        // Beschreibung) in den spezialisieren Klassen vorhanden ist.
//                    } else {
//                        // Struktur
//                        // Momentan nicht von Interesse
//                    }
//                } else if (tObj instanceof Topic) {
//                    Topic topic = (Topic) tObj;
//                    Iterator<?> iter = topic.getViewables().iterator();
//
//                    while (iter.hasNext()) {
//                        Object obj = iter.next();
////                        log.info(obj.toString());
//
//                        // Viewable wäre "alles". Was ist sinnvoll/notwendig für unseren Usecase?
//                        // Domains?
//                        // Momentan nur Table berücksichtigen.
//                        if (obj instanceof Table) {
//                            Table table = (Table) obj;
//
//                            // Abstrakte Klasse oder Struktur
//                            // Abstrakte Klasse interessiert uns nicht, da alle
//                            // Attribute in der spezialisierte Klass vorhanden sind.
//                            // Struktur interessiert uns vielleicht später aber
//                            // zum jetzigen Zeitpunkt brauchen wir dieses Wissen
//                            // nicht.
//                            if (table.isAbstract() || !table.isIdentifiable()) {
//                                continue;
//                            }
//
//                            ClassDescription classType = new ClassDescription();
//                            classType.setName(table.getName());
//                            classType.setTitle(table.getMetaValue("title"));
//                            classType.setDescription(table.getDocumentation());
//                            classType.setModelName(modelName);
//                            classType.setTopicName(topic.getName());
//
//                            // String tableName = table.getScopedName(null);
//                            Iterator<?> attri = table.getAttributes();
//
//                            List<AttributeDescription> attributes = new ArrayList<>();
//                            while (attri.hasNext()) {
//                                Object aObj = attri.next();
//                                AttributeDescription attributeType = new AttributeDescription();
//
//                                if (aObj instanceof AttributeDef) {
//                                    AttributeDef attr = (AttributeDef) aObj;
//                                    attributeType.setName(attr.getName());
//                                    attributeType.setDescription(attr.getDocumentation());
//
//                                    Type type = attr.getDomainResolvingAll();
//                                    attributeType.setMandatory(type.isMandatory() ? true : false);
//
//                                    if (type instanceof TextType t) {
//                                        attributeType.setDataType(t.isNormalized() ? DataType.TEXT : DataType.MTEXT);
//                                    } else if (type instanceof NumericType n) {
//                                        attributeType.setDataType(n.getMinimum().getAccuracy() == 0 ? DataType.INTEGER : DataType.DOUBLE);
//                                    } else if (type instanceof EnumerationType e) {
//                                        attributeType.setDataType(DataType.ENUMERATION);
//                                    } else if (type instanceof SurfaceOrAreaType s) {
//                                        attributeType.setDataType(DataType.POLYGON);
//                                    } else if (type instanceof PolylineType p) {
//                                        attributeType.setDataType(DataType.LINESTRING);
//                                    } else if (type instanceof CoordType c) {
//                                        attributeType.setDataType(DataType.POINT);
//                                    } else if (type instanceof FormattedType f) {
//                                        String format = f.getFormat();
//                                        if (format.contains("Year") && !format.contains("Hours")) {
//                                            attributeType.setDataType(DataType.DATE);
//                                        } else if (format.contains("Year") && format.contains("Hours")) {
//                                            attributeType.setDataType(DataType.DATETIME);
//                                        }
//                                        // else if...
//                                    } else if (type instanceof CompositionType c) {
//
//                                        if (attr.getMetaValue(IliMetaAttrNames.METAATTR_MAPPING)!= null && attr.getMetaValue(IliMetaAttrNames.METAATTR_MAPPING).equals(IliMetaAttrNames.METAATTR_MAPPING_JSON)) {
//                                            attributeType.setDataType(DataType.JSON_TEXT);
//                                        } else {
//                                            Table struct = c.getComponentType();
//
//                                            // Wenn es
//                                            // keine richtigen Multigeometrie-Datentypen
//                                            // gibt, geht es nicht 100% robust.
//                                            if (c.getCardinality().getMaximum() != 1) {
//                                                attributeType.setDataType(DataType.UNDEFINED); // oder DataType.STRUCTURE ?
//                                            } else {
//                                                if (Ili2cUtility.isPureChbaseMultiSurface(td, attr)) {
//                                                    attributeType.setDataType(DataType.MULTIPOLYGON);
//                                                } else if (Ili2cUtility.isPureChbaseMultiLine(td, attr)) {
//                                                    attributeType.setDataType(DataType.MULTILINESTRING);
//                                                } else {
//                                                    String metaValue = struct.getMetaValue(IliMetaAttrNames.METAATTR_MAPPING);
//
//                                                    if (metaValue == null) {
//                                                        attributeType.setDataType(DataType.UNDEFINED);
//                                                    } else if (metaValue
//                                                            .equals(IliMetaAttrNames.METAATTR_MAPPING_MULTISURFACE)) {
//                                                        attributeType.setDataType(DataType.MULTIPOLYGON);
//                                                    } else if (metaValue
//                                                            .equals(IliMetaAttrNames.METAATTR_MAPPING_MULTILINE)) {
//                                                        attributeType.setDataType(DataType.MULTILINESTRING);
//                                                    } else if (metaValue
//                                                            .equals(IliMetaAttrNames.METAATTR_MAPPING_MULTIPOINT)) {
//                                                        attributeType.setDataType(DataType.MULTIPOINT);
//                                                    } else {
//                                                        attributeType.setDataType(DataType.UNDEFINED);
//                                                    }
//                                                }
//                                            }
//                                        }
//                                    }
//                                }
//                                attributes.add(attributeType);
//                            }
//                            classType.setAttributes(attributes);
//                            classTypes.add(classType);
//                        } // else if... DOMAIN, etc.? DOMAIN nur, falls ich die Werte wirklich ausweisen will.
//                    }
//                }
//            }
//        }

//        for (ClassType classType : classTypes) {
//            System.out.println(classType.getQualifiedName());
//            System.out.println(classType.getTitle());
//            System.out.println(classType.getDescription());            
//            System.out.println(classType.getAttributes());
//        }
        
//        String xmlFileName = settings.getValue(settings.XML_FILE_NAME);
//        if (xmlFileName != null) {
//            try {
//                writeXml(xmlFileName);
//            } catch (IOException e) {
//                throw new Ili2MetaException(e);
//            }
//        }
//    }

    private TransferDescription getTransferDescriptionFromFileName(String fileName) throws Ili2cException {
        IliManager manager = new IliManager();
        String repositories[] = new String[] { "https://geo.so.ch/models", "http://models.geo.admin.ch/",
                "http://models.interlis.ch/", "http://models.kkgeo.ch/", "." };
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
