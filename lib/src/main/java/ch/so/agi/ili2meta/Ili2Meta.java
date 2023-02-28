package ch.so.agi.ili2meta;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.SerializationFeature;
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
        
     
        var xof = XMLOutputFactory.newFactory();
        try {
            var xsw = xof.createXMLStreamWriter(new FileWriter(new File(xmlFileName)));
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
    
    public void run(String modelName, Settings settings) throws Ili2MetaException {
        List<ClassType> classTypes = new ArrayList<>();

        // https://github.com/sogis/interlis-repository-creator/blob/e857016b25cb83ebe518b13409ad31a261385693/src/main/java/ch/so/agi/tasks/InterlisRepositoryCreator.java#L306
        // getTdFromModelName

        // TODO ...
        String fileName = settings.getValue(settings.ILI_FILE_NAME);
        
        TransferDescription td = null;
        try {
            td = getTransferDescriptionFromFileName(fileName);
        } catch (Ili2cException e) {
            throw new Ili2MetaException(e);
        }

        for (Model model : td.getModelsFromLastFile()) {
            if (!model.getName().equalsIgnoreCase(modelName)) {
                continue;
            }

            Iterator<Element> modeli = model.iterator();
            while (modeli.hasNext()) {
                Object tObj = modeli.next();

//                log.info(tObj.toString());
//                log.info(tObj.getClass().toString());

                if (tObj instanceof Domain) {
                    // TODO falls ich die Werte will
                } else if (tObj instanceof Table) {
                    Table tableObj = (Table) tObj;
                    // https://github.com/claeis/ili2c/blob/ccb1331428/ili2c-core/src/main/java/ch/interlis/ili2c/metamodel/Table.java#L30
                    if (tableObj.isIdentifiable()) {
                        // Abstrakte Klasse
                        // Kann ich ignorieren, da alles was ich wissen will (? Attribute und
                        // Beschreibung) in den spezialisieren Klassen vorhanden ist.
                    } else {
                        // Struktur
                        // Momentan nicht von Interesse
                    }
                } else if (tObj instanceof Topic) {
                    Topic topic = (Topic) tObj;
                    Iterator<?> iter = topic.getViewables().iterator();

                    while (iter.hasNext()) {
                        Object obj = iter.next();
//                        log.info(obj.toString());

                        // Viewable wäre "alles". Was ist sinnvoll/notwendig für unseren Usecase?
                        // Domains?
                        // Momentan nur Table berücksichtigen.
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

                            // String tableName = table.getScopedName(null);
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
                                    attributeType.setMandatory(type.isMandatory() ? true : false);

                                    if (type instanceof TextType t) {
                                        attributeType.setDataType(t.isNormalized() ? DataType.TEXT : DataType.MTEXT);
                                    } else if (type instanceof NumericType n) {
                                        attributeType.setDataType(n.getMinimum().getAccuracy() == 0 ? DataType.INTEGER : DataType.DOUBLE);
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
                                    } else if (type instanceof CompositionType c) {

                                        if (attr.getMetaValue(IliMetaAttrNames.METAATTR_MAPPING)!= null && attr.getMetaValue(IliMetaAttrNames.METAATTR_MAPPING).equals(IliMetaAttrNames.METAATTR_MAPPING_JSON)) {
                                            attributeType.setDataType(DataType.JSON_TEXT);
                                        } else {
                                            Table struct = c.getComponentType();

                                            // Wenn es
                                            // keine richtigen Multigeometrie-Datentypen
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
                            classTypes.add(classType);
                        } // else if... DOMAIN, etc.? DOMAIN nur, falls ich die Werte wirklich ausweisen will.
                    }
                }
            }
        }

//        for (ClassType classType : classTypes) {
//            System.out.println(classType.getQualifiedName());
//            System.out.println(classType.getTitle());
//            System.out.println(classType.getDescription());            
//            System.out.println(classType.getAttributes());
//        }
        
        String xmlFileName = settings.getValue(settings.XML_FILE_NAME);
        if (xmlFileName != null) {
            try {
                writeXml(xmlFileName);
            } catch (IOException e) {
                throw new Ili2MetaException(e);
            }
        }
    }

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
