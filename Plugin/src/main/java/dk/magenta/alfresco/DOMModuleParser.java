/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco;

import static dk.magenta.alfresco.GenerateJavaMojo.DICTIONARY_URI;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import static dk.magenta.alfresco.GenerateJavaMojo.DICTIONARY_URI;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;
import javax.lang.model.element.Modifier;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.MLText;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.Period;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.VersionNumber;
import org.apache.maven.plugin.logging.Log;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author martin
 */
public class DOMModuleParser {

    public static final String IMPORTS_TAG = "imports";
    public static final String IMPORT_TAG = "import";

    public static final String NAMESPACES_TAG = "namespaces";
    public static final String NAMESPACE_TAG = "namespace";

    public static final String TYPES_TAG = "types";
    public static final String TYPE_TAG = "type";
    public static final String MANDATORY_ASPECTS_TAG = "mandatory-aspects";
    public static final String PARENT_TAG = "parent";
    public static final String MANDATORY_TAG = "mandatory";
    public static final String ARCHIVE_TAG = "archive";

    public static final String ASPECTS_TAG = "aspects";
    public static final String ASPECT_TAG = "aspect";

    public static final String PROPERTIES_TAG = "properties";
    public static final String PROPERTY_TAG = "property";

    public static final String ASSOCIATIONS_TAG = "associations";
    public static final String CHILD_ASSOCIATION_TAG = "child-association";
    public static final String ASSOCIATION_TAG = "association";
    public static final String ASSOCIATION_SOURCE_TAG = "source";
    public static final String ASSOCIATION_TARGET_TAG = "target";
    public static final String ASSOCIATION_MANDATORY_TAG = "mandatory";
    public static final String ASSOCIATION_MANY_TAG = "many";

    public static final String TITLE_TAG = "title";

    public static final String ASPECT_POSTFIX = "Class";

    private final Log log;
    private final List<String> packagePrefixes;
    private final String sourceDir;
    private final ClassName anchorClass;
    private final String encoding = "UTF-8";

    private final Map<String, String> importMappings = new HashMap<>();
    private final Map<String, String> nameSpaceMappings = new HashMap<>();

    private boolean firstElement = true;

    private boolean handlingImports = false;
    private boolean handlingNamespaces = false;
    private boolean handlingTypes = false;
    private boolean handlingAspects = false;
    private boolean handlingProperties = false;
    private boolean handlingMandatoryAspects = false;
    private boolean handlingAssociations = false;
    private boolean handlingAssociationSource = false;
    private boolean handlingAssociationTarget = false;

    private boolean handlingConstraints = false;

    private StringBuilder stringBuilder;

    String fieldClass;
    String fieldName;

    boolean associationSourceMandatory = false;
    boolean associationSourceMany = false;
    boolean associationTargetMandatory = false;
    boolean associationTargetMany = false;

    private Map<String, TypeSpec> fqcnToTypes = new HashMap<>();

    public DOMModuleParser(Log log, List<String> packagePrefixes, String sourceDir, ClassName anchorClass) {
        this.log = log;
        this.packagePrefixes = packagePrefixes;
        this.sourceDir = sourceDir;
        this.anchorClass = anchorClass;
    }

    public void execute(InputStream stream) throws IOException, ParserConfigurationException, SAXException {

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder;
        dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(stream);
        doc.getDocumentElement().normalize();

        handleImports(doc.getDocumentElement().getElementsByTagNameNS(DICTIONARY_URI, IMPORTS_TAG),
                doc.getDocumentElement().getElementsByTagNameNS(DICTIONARY_URI, NAMESPACES_TAG));

        handleTypes(doc.getDocumentElement().getElementsByTagNameNS(DICTIONARY_URI, TYPES_TAG));

        handleAspects(doc.getDocumentElement().getElementsByTagNameNS(DICTIONARY_URI, ASPECTS_TAG));
    }

    protected void handleImports(NodeList imports, NodeList nameSpaces) {
        for (int i = 0; i < imports.getLength(); i++) {
            Node importNode = imports.item(i);
            if (matches(importNode, DICTIONARY_URI, IMPORT_TAG)) {
                String uri = importNode.getAttributes().getNamedItemNS(DICTIONARY_URI, "uri").getNodeValue();
                String prefix = importNode.getAttributes().getNamedItemNS(DICTIONARY_URI, "prefix").getNodeValue();
                importMappings.put(prefix, uri);
            } else {
                log.warn("Found unmatched node in imports: " + importNode);
            }

        }
        for (int i = 0; i < nameSpaces.getLength(); i++) {
            Node importNode = nameSpaces.item(i);
            if (matches(importNode, DICTIONARY_URI, NAMESPACE_TAG)) {
                String uri = importNode.getAttributes().getNamedItemNS(DICTIONARY_URI, "uri").getNodeValue();
                String prefix = importNode.getAttributes().getNamedItemNS(DICTIONARY_URI, "prefix").getNodeValue();
                nameSpaceMappings.put(prefix, uri);
            }

        }
    }

    protected void handleTypes(NodeList types) throws IOException {
        for (int typeIndex = 0; typeIndex < types.getLength(); typeIndex++) {
            Node typeNode = types.item(typeIndex);
            if (matches(typeNode, DICTIONARY_URI, TYPE_TAG)) {
                QName typeQName = getNameAttribute(typeNode);

                List<String> javaTypePackages = getPackages(typeQName.getNamespaceURI());
                String packageName = getPackage(javaTypePackages);
                ClassName clazz = ClassName.get(packageName, capitalize(packageName));
                TypeSpec.Builder javaClassBuilder = TypeSpec.classBuilder(clazz).addModifiers(Modifier.PUBLIC);

                String title = null;
                ClassName parent = anchorClass;

                NodeList typeChildren = typeNode.getChildNodes();
                for (int typeChildIndex = 0; typeChildIndex < types.getLength(); typeChildIndex++) {
                    Node typeChild = typeChildren.item(typeChildIndex);
                    if (typeChild.getNamespaceURI().equals(DICTIONARY_URI)) {
                        switch (typeChild.getLocalName()) {
                            case TITLE_TAG:
                                title = getNodeValue(typeNode);
                                break;
                            case PARENT_TAG:
                                parent = classNameFromQName(getQName(typeChild, getNodeValue(typeNode)));
                                break;
                            case PROPERTIES_TAG:
                                addProperties(null, javaClassBuilder, typeChild.getChildNodes());
                                break;
                            case MANDATORY_ASPECTS_TAG:
                                addMandatoryAspectsToClass(javaClassBuilder, typeChild.getChildNodes());
                                break;
                            default:
                                log.warn("Unexpected tag inside type " + typeChild);
                        }
                    } else {
                        log.warn("Unexpected namespace inside type " + typeChild);
                    }

                }
                JavaFile javaFile = JavaFile.builder(packageName, javaClassBuilder.build()).build();
                javaFile.writeTo(new File(sourceDir));

            }
        }
    }

    protected void handleAspects(NodeList aspects) throws IOException {
        for (int aspectIndex = 0; aspectIndex < aspects.getLength(); aspectIndex++) {
            Node aspectNode = aspects.item(aspectIndex);
            if (matches(aspectNode, DICTIONARY_URI, ASPECT_TAG)) {
                QName aspectName = getNameAttribute(aspectNode);
                
                List<String> javaTypePackages = getPackages(aspectName.getNamespaceURI());
                String packageName = getPackage(javaTypePackages);
                ClassName clazz = ClassName.get(packageName, capitalize(packageName));
                TypeSpec.Builder javaInterfaceBuilder = TypeSpec.classBuilder(clazz).addModifiers(Modifier.PUBLIC);
                TypeSpec.Builder javaClassBuilder = TypeSpec.classBuilder(clazz+ASPECT_POSTFIX).addModifiers(Modifier.PUBLIC);

                
                String aspectTitle = null;
                ClassName parent = anchorClass;
                boolean archive = false;
                NodeList aspectChildren = aspectNode.getChildNodes();
                for (int aspectChildIndex = 0; aspectChildIndex < aspectChildren.getLength(); aspectChildIndex++) {
                    Node aspectChild = aspectChildren.item(aspectChildIndex);
                    if (aspectChild.getNamespaceURI().equals(DICTIONARY_URI)) {
                        switch (aspectChild.getLocalName()) {
                            case TITLE_TAG:
                                aspectTitle = getNodeValue(aspectChild);
                                break;
                            case PARENT_TAG:
                                parent = classNameFromQName(getQName(aspectChild, getNodeValue(aspectChild)));
                                break;
                            case ARCHIVE_TAG:
                                if (getNodeValue(aspectChild).equalsIgnoreCase("true")) {
                                    archive = true;
                                }
                                break;
                            case PROPERTIES_TAG:
                                addProperties(javaInterfaceBuilder, javaClassBuilder, aspectChild.getChildNodes());
                                break;
                        }
                    } else {
                        log.warn("Unexpected namespace in aspect children; " + aspectChild);
                    }
                }
                JavaFile interfaceFile = JavaFile.builder(packageName, javaInterfaceBuilder.build()).build();
                interfaceFile.writeTo(new File(sourceDir));
                JavaFile classFile = JavaFile.builder(packageName, javaClassBuilder.build()).build();
                classFile.writeTo(new File(sourceDir));

            } else {
                log.warn("Unexpected node in aspects: " + aspectNode);
            }
        }
    }
    
    

    protected void addProperties(TypeSpec.Builder interfaceBuilder, TypeSpec.Builder classBuilder, NodeList properties) {
        for (int propertyIndex = 0; propertyIndex < properties.getLength(); propertyIndex++) {
            Node propertyNode = properties.item(propertyIndex);
            if (matches(propertyNode, DICTIONARY_URI, PROPERTY_TAG)) {
                QName classQName = getNameAttribute(propertyNode);
                String title = null;
                ClassName fieldType = null;
                boolean mandatory = false;

                NodeList propertyChildren = propertyNode.getChildNodes();
                for (int propertyChildIndex = 0; propertyChildIndex < propertyChildren.getLength(); propertyChildIndex++) {
                    Node propertyChild = propertyChildren.item(propertyChildIndex);
                    if (propertyChild.getNamespaceURI().equals(DICTIONARY_URI)) {
                        switch (propertyChild.getLocalName()) {
                            case TITLE_TAG:
                                title = getNodeValue(propertyChild);
                                break;
                            case TYPE_TAG:
                                fieldType = classNameFromQName(getQName(propertyChild, getNodeValue(propertyChild)));
                                break;
                            case MANDATORY_TAG:
                                if (getNodeValue(propertyChild).equalsIgnoreCase("true")) {
                                    mandatory = true;
                                }
                                break;
                            default:
                                log.warn("Unexpected tag in property: " + propertyChild);
                        }
                    } else {
                        log.warn("Unexpected namespace inside property: " + propertyChild);
                    }

                }
                if(interfaceBuilder != null){
                    interfaceBuilder.addMethod(MethodSpec.methodBuilder("get" + capitalize(classQName.getLocalName())).addModifiers(Modifier.PUBLIC).returns(fieldType)
                        .addCode("$[return this.$L$];$W", classQName.getLocalName()).build());
                }
                classBuilder.addField(FieldSpec.builder(fieldType, classQName.getLocalName(), Modifier.PRIVATE).build());
                MethodSpec.Builder getterMethod = MethodSpec.methodBuilder("get" + capitalize(classQName.getLocalName())).addModifiers(Modifier.PUBLIC).returns(fieldType)
                        .addCode("$[return this.$L$];$W", classQName.getLocalName());
                if(interfaceBuilder != null){
                    getterMethod.addAnnotation(Override.class);
                }
                classBuilder.addMethod(getterMethod.build());

            } else {
                log.warn("Found unmatched node in properties: " + propertyNode);
            }
        }
    }

    protected void addMandatoryAspectsToClass(TypeSpec.Builder classBuilder, NodeList mandatoryAspects) {

    }

    protected ClassName classNameFromQName(QName name) {
        return ClassName.get(getPackage(getPackages(name.getNamespaceURI())), name.getLocalName());
    }

    protected String getNodeValue(Node titleNode) {
        return titleNode.getNodeValue();
    }

    protected QName getNameAttribute(Node node) {
        return getQName(node, node.getAttributes().getNamedItem("name").getNodeValue());
    }

    protected QName getQName(Node node, String prefixedName) {
        String[] type = prefixedName.split(":");
        if (type.length != 2) {
            //I'm not sure if this case ever happens. So a warn log is here for awareness.
            log.warn(prefixedName + " did not contain a prefix");
            return null;
        }

        String classPrefix = type[0];
        String className = type[1];

        //String classNamespace = resolveNamespaceByPrefix(classPrefix);
        String classNamespace = node.lookupNamespaceURI(classPrefix);

        if (classNamespace == null) {
            log.error("Found type not matched by namespace: " + prefixedName);
        }

        return QName.createQName(classNamespace, className);

    }

    protected List<String> getPackages(String namespaceURI) {
        Scanner uriScanner = new Scanner(namespaceURI).useDelimiter(Pattern.compile("[\\/]"));
        List<String> uriPackages = new LinkedList<>();
        while (uriScanner.hasNext()) {
            uriPackages.add(uriScanner.next());
        }
        while (uriPackages.get(0).startsWith("http") || uriPackages.get(0).isEmpty()) {
            uriPackages.remove(0);
        }
        if (uriPackages.get(0).contains(".")) {
            String addressPart = uriPackages.remove(0);
            Scanner addressScanner = new Scanner(addressPart).useDelimiter("[\\.]");
            while (addressScanner.hasNext()) {
                String token = addressScanner.next();
                if (token.equals("www")) {
                    continue;
                }
                uriPackages.add(0, token);

            }
        }

        List<String> packages = new ArrayList<>(packagePrefixes);
        for (String uriPackage : uriPackages) {
            if (uriPackage.substring(0, 1).matches("^\\d")) {
                uriPackage = "_" + uriPackage;
            }
            packages.add(uriPackage.replaceAll("\\.", "_"));
        }
        return packages;
    }

    protected String getPackage(List<String> packages) {
        boolean first = true;
        String packageString = "";
        for (String pkg : packages) {
            if (!first) {
                packageString = packageString + ".";
            }
            packageString = packageString + pkg;
            first = false;
        }
        return packageString;
    }

    public static String capitalize(String toCapitalize) {
        return toCapitalize.substring(0, 1).toUpperCase() + toCapitalize.substring(1);
    }

    public boolean matches(Node node, String uri, String localName) {
        return node.getNamespaceURI().equals(uri) && node.getLocalName().equals(localName);

    }

    protected Class resolveType(String nameSpace, String localName) {
        if (nameSpace.equals(DICTIONARY_URI)) { //Do we need to match other name spaces here as well, or is this fine until we start matching data-type tags?
            switch (localName) {
                case "text":
                    return String.class;
                case "mltext":
                    return MLText.class;
                case "content":
                    return ContentData.class;
                case "int":
                    return Integer.class;
                case "long":
                    return Long.class;
                case "float":
                    return Float.class;
                case "double":
                    return Double.class;
                case "date":
                    return Date.class;
                case "datetime":
                    return Date.class;
                case "boolean":
                    return Boolean.class;
                case "qname":
                    return QName.class;
                case "noderef":
                    return NodeRef.class;
                case "childassocref":
                    return ChildAssociationRef.class;
                case "assocref":
                    return AssociationRef.class;
                case "path":
                    return Path.class;
                case "category":
                    return NodeRef.class;
                case "locale":
                    return Locale.class;
                case "version":
                    return VersionNumber.class;
                case "period":
                    return Period.class;
                case "any":
                    return Object.class; //This should likely be replaced with the generators base class maybe?
                default:
                    return null; //If nothing matches, fail horribly

            }

        }
        return null; //If nothing matches, fail horribly
    }

}
