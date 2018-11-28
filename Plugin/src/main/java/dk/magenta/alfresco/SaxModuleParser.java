/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import static dk.magenta.alfresco.GenerateJavaMojo.DICTIONARY_URI;
import static dk.magenta.alfresco.GenerateJavaMojo.MODEL_TAG;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileManager;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.MLText;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.Period;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.VersionNumber;
import org.apache.maven.plugin.logging.Log;
import org.hibernate.type.ClassType;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.SAXParseException;

/**
 *
 * @author martin
 */
public class SaxModuleParser extends DefaultHandler {

    public static final String IMPORTS_TAG = "imports";
    public static final String IMPORT_TAG = "import";

    public static final String NAMESPACES_TAG = "namespaces";
    public static final String NAMESPACE_TAG = "namespace";

    public static final String TYPES_TAG = "types";
    public static final String TYPE_TAG = "type";
    public static final String MANDATORY_ASPECTS_TAG = "mandatory-aspects";
    public static final String PARENT_TAG = "parent";

    public static final String ASPECTS_TAG = "aspects";
    public static final String ASPECT_TAG = "aspect";

    public static final String PROPERTIES_TAG = "properties";
    public static final String PROPERTY_TAG = "property";

    public static final String ASSOCIATIONS_TAG = "associations";
    public static final String CHILD_ASSOCIATION_TAG = "child-association";
    public static final String ASSOCIATION_TAG = "association";

    public static final String ASPECT_POSTFIX = "Class";
    
    private final Log log;
    private final List<String> packagePrefixes;
    private final String sourceDir;
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
    private boolean handlingConstraints = false;

    private StringBuilder stringBuilder;

    private TypeSpec.Builder javaInterfaceBuilder;
    private TypeSpec.Builder javaClassBuilder;
    private List<String> javaTypePackages;
    private ClassName parent;
    
    String fieldClass;
    String fieldName;

    private Map<String, TypeSpec> fqcnToTypes = new HashMap<>();

    public SaxModuleParser(Log log, List<String> packagePrefixes, String sourceDir) {
        this.log = log;
        this.packagePrefixes = packagePrefixes;
        this.sourceDir = sourceDir;
    }

    @Override
    public void startDocument() throws SAXException {

    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes attributes) throws SAXException {
        if (firstElement && !(uri.equals(DICTIONARY_URI) || localName.equals(MODEL_TAG))) {
            throw new NotModuleException("First element was not a " + MODEL_TAG + " with namespace " + DICTIONARY_URI, null);
        }
        firstElement = false;
        log.info("Parsing element: URI: " + uri + " localName: " + localName + " qName: " + qName + " Attributes: " + attributes.getLength());
        if (uri.equals(DICTIONARY_URI)) {
            switch (localName) {
                case IMPORTS_TAG:
                    handlingImports = true;
                    break;
                case NAMESPACES_TAG:
                    handlingNamespaces = true;
                    break;
                case TYPES_TAG:
                    handlingTypes = true;
                    break;
                case ASPECTS_TAG:
                    handlingAspects = true;
                    break;
                case MANDATORY_ASPECTS_TAG:
                    handlingMandatoryAspects = true;
                    break;
                case PROPERTIES_TAG:
                    handlingProperties = true;
                    break;
                case ASSOCIATIONS_TAG:
                    handlingAssociations = true;
                    break;
                case IMPORT_TAG:
                    startImport(uri, localName, qName, attributes);
                    break;
                case NAMESPACE_TAG:
                    startNamespace(uri, localName, qName, attributes);
                    break;
                case TYPE_TAG:
                    if (handlingProperties) {
                        startPropertyClass(uri, localName, qName, attributes);
                    } else {
                        startType(uri, localName, qName, attributes);
                    }
                    break;
                case ASPECT_TAG:
                    if (handlingAspects) {
                        startAspect(uri, localName, qName, attributes);
                    } else if (handlingMandatoryAspects) {
                        if (handlingTypes) {
                            startTypeMandatoryAspect(uri, localName, qName, attributes);
                        } else if (handlingAspects) {
                            startAspectMandatoryAspect(uri, localName, qName, attributes);
                        }

                    }
                    break;
                case PARENT_TAG:
                    if (handlingTypes) {
                        startTypeParent(uri, localName, qName, attributes);
                    } else if (handlingAspects) {
                        startAspectParent(uri, localName, qName, attributes);
                    }
                    break;
                case PROPERTY_TAG:
                    if (handlingTypes) {
                        startTypeProperty(uri, localName, qName, attributes);
                    } else if (handlingAspects) {
                        startAspectProperty(uri, localName, qName, attributes);
                    }
                    break;
                case ASSOCIATION_TAG:
                    if (handlingTypes) {
                        startTypeAssociation(uri, localName, qName, attributes);
                    } else if (handlingAssociations) {
                        startAspectAssociation(uri, localName, qName, attributes);
                    }
                    break;
                case CHILD_ASSOCIATION_TAG:
                    if (handlingTypes) {
                        startTypeChildAssociation(uri, localName, qName, attributes);
                    } else if (handlingAssociations) {
                        startAspectChildAssociation(uri, localName, qName, attributes);
                    }
                    break;
                default:
                    if (!handlingConstraints) {
                        log.warn("OOoops. Found unhandled opening tag: URI: " + uri + " localName " + localName);
                    }

            }

        }

    }

    protected void startImport(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {
        String namespacePrefix = attributes.getValue("", "prefix");
        String namespaceURI = attributes.getValue("", "uri");
        importMappings.put(namespacePrefix, namespaceURI);
    }

    protected void startNamespace(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {
        String namespacePrefix = attributes.getValue("", "prefix");
        String namespaceURI = attributes.getValue("", "uri");
        nameSpaceMappings.put(namespacePrefix, namespaceURI);
    }

    protected void startType(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {
//        String nodeRefFieldName ="nodeRef";
//        String nodeIDFieldName = "nodeID";
//        
        String typeName = attributes.getValue("name");
        log.info("TypeName " + typeName);
        String[] type = typeName.split(":");
        if (type.length != 2) {
            //I'm not sure if this case ever happens. So a warn log is here for awareness.
            log.warn(typeName + " did not contain a prefix");
            return;
        }
        String classPrefix = type[0];
        String className = capitalize(type[1]);
        String classNamespace = resolveNamespaceByPrefix(classPrefix);

        if (classNamespace == null) {
            log.error("Found type not matched by namespace: " + typeName);
        }

        javaTypePackages = getPackages(classNamespace);
        String packageName = getPackage(javaTypePackages);
        ClassName clazz = ClassName.get(packageName, className);
        javaClassBuilder = TypeSpec.classBuilder(clazz);

//        FieldSpec nodeRefField = FieldSpec.builder(String.class, nodeRefFieldName, Modifier.PROTECTED).build();
//        FieldSpec nodeIDField = FieldSpec.builder(String.class, nodeIDFieldName, Modifier.PROTECTED).build();
//        javaTypeBuilder.addField(nodeRefField);
//        javaTypeBuilder.addField(nodeIDField);
//        javaTypeBuilder.addMethod(buildGetter(nodeRefField));
//        javaTypeBuilder.addMethod(buildGetter(nodeIDField));
//        javaTypeBuilder.addMethod(MethodSpec.constructorBuilder()
//                .addParameter(String.class, nodeRefField.name)
//                .addParameter(String.class, nodeIDField.name)
//                .addCode("$[this.$L=$L$];$W$[this.$L=$L$];$W", nodeRefField.name, nodeRefField.name, nodeIDField.name, nodeIDField.name).build());
    }
    
    protected void startTypeParent(String uri, String localName, String qName, org.xml.sax.Attributes attributes){
        stringBuilder = new StringBuilder();
    }

    protected void startTypeMandatoryAspect(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {

    }

    protected void startTypeProperty(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {
        String propertyNameAttribute = attributes.getValue("name");
        log.info("PropertyName " + propertyNameAttribute);
        String[] type = propertyNameAttribute.split(":");
        if (type.length != 2) {
            //I'm not sure if this case ever happens. So a warn log is here for awareness.
            log.warn(propertyNameAttribute + " did not contain a prefix");
            return;
        }
        String propPrefix = type[0];
        String propName = type[1];
        String propertyNamespace = resolveNamespaceByPrefix(propPrefix);

        if (propertyNamespace == null) {
            log.error("Found type not matched by namespace: " + propertyNameAttribute);
        }

        fieldName = propName;
    }

    protected void startTypeChildAssociation(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {

    }

    protected void startTypeAssociation(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {

    }

    protected void startAspect(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {
        String aspectName = attributes.getValue("name");
        log.info("AspectName " + aspectName);
        String[] type = aspectName.split(":");
        if (type.length != 2) {
            //I'm not sure if this case ever happens. So a warn log is here for awareness.
            log.warn(aspectName + " did not contain a prefix");
            return;
        }
        String classPrefix = type[0];
        String className = capitalize(type[1]);
        String classNamespace = resolveNamespaceByPrefix(classPrefix);

        if (classNamespace == null) {
            log.error("Found type not matched by namespace: " + aspectName);
        }

        javaTypePackages = getPackages(classNamespace);
        String packageName = getPackage(javaTypePackages);
        ClassName aspect = ClassName.get(packageName, className);
        ClassName aspectImplementation = ClassName.get(packageName, className + ASPECT_POSTFIX);
        javaInterfaceBuilder = TypeSpec.interfaceBuilder(aspect);
        javaClassBuilder = TypeSpec.classBuilder(aspectImplementation);

    }
    
    protected void startAspectParent(String uri, String localName, String qName, org.xml.sax.Attributes attributes){
        startTypeParent(uri, localName, qName, attributes);
    }

    protected void startAspectMandatoryAspect(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {

    }

    protected void startAspectProperty(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {
        startTypeProperty(uri, localName, qName, attributes);
    }

    protected void startAspectChildAssociation(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {

    }

    protected void startAspectAssociation(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {

    }

    protected void startPropertyClass(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {
        stringBuilder = new StringBuilder();
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        try {
            switch (localName) {
                case IMPORTS_TAG:
                    handlingImports = false;
                    break;
                case NAMESPACES_TAG:
                    handlingNamespaces = false;
                    break;
                case TYPES_TAG:
                    handlingTypes = false;
                    break;
                case ASPECTS_TAG:
                    handlingAspects = false;
                    break;
                case MANDATORY_ASPECTS_TAG:
                    handlingMandatoryAspects = false;
                    break;
                case PROPERTIES_TAG:
                    handlingProperties = false;
                    break;
                case ASSOCIATIONS_TAG:
                    handlingAssociations = false;
                    break;
                case IMPORT_TAG:
                    endImport(uri, localName, qName);
                    break;
                case NAMESPACE_TAG:
                    endNamespace(uri, localName, qName);
                    break;
                case TYPE_TAG:
                    if (handlingProperties) {
                        endPropertyClass(uri, localName, qName);
                    } else {
                        endType(uri, localName, qName);
                    }
                    break;
                case ASPECT_TAG:
                    if (handlingAspects) {
                        endAspect(uri, localName, qName);
                    } else if (handlingMandatoryAspects) {
                        if (handlingTypes) {
                            endTypeMandatoryAspect(uri, localName, qName);
                        } else if (handlingAspects) {
                            endAspectMandatoryAspect(uri, localName, qName);
                        }

                    }
                    break;
                case PARENT_TAG:
                    if (handlingTypes) {
                        endTypeParent(uri, localName, qName);
                    } else if (handlingAspects) {
                        endAspectParent(uri, localName, qName);
                    }
                    break;
                case PROPERTY_TAG:
                    if (handlingTypes) {
                        endTypeProperty(uri, localName, qName);
                    } else if (handlingAspects) {
                        endAspectProperty(uri, localName, qName);
                    }
                    break;
                case ASSOCIATION_TAG:
                    if (handlingTypes) {
                        endTypeAssociation(uri, localName, qName);
                    } else if (handlingAssociations) {
                        endAspectAssociation(uri, localName, qName);
                    }
                    break;
                case CHILD_ASSOCIATION_TAG:
                    if (handlingTypes) {
                        endTypeChildAssociation(uri, localName, qName);
                    } else if (handlingAssociations) {
                        endAspectChildAssociation(uri, localName, qName);
                    }
                    break;
                default:
                    log.warn("OOoops. Found unhandled opening tag: URI: " + uri + " localName " + localName);

            }
        } catch (Exception ex) {
            throw new SAXException(ex);
        }
    }

    protected void endImport(String uri, String localName, String qName) {

    }

    protected void endNamespace(String uri, String localName, String qName) {

    }

    protected void endType(String uri, String localName, String qName) throws IOException {
        String packageName = getPackage(javaTypePackages);
        if(parent != null){
            javaClassBuilder.superclass(parent);
        }
        TypeSpec type = javaClassBuilder.build();
        log.info("end type :\n" + type);

        fqcnToTypes.put(getFqcn(packageName, type.name), type);
        parent = null;
        
        JavaFile javaFile = JavaFile.builder(packageName, type).build();
        javaFile.writeTo(new File(sourceDir));
    }

    protected void endTypeMandatoryAspect(String uri, String localName, String qName) {

    }

    protected void endTypeProperty(String uri, String localName, String qName) {
        String[] classPrefixAndName = fieldClass.split(":");

        if (classPrefixAndName.length != 2) {
            //I'm not sure if this case ever happens. So a warn log is here for awareness.
            log.warn(fieldClass + " field did not contain a prefix");
            return;
        }

        String nameSpace = resolveNamespaceByPrefix(classPrefixAndName[0]);

        FieldSpec getterToQNameField = FieldSpec.builder(QName.class, fieldName, Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC).initializer("$T.createQName($S, $S)", QName.class, nameSpace, fieldName).build();

        String methodName = "get" + "get" + capitalize(fieldName);
        MethodSpec getter = MethodSpec.methodBuilder(methodName).returns(resolveType(nameSpace, classPrefixAndName[1])).addModifiers(Modifier.PUBLIC)
                .addCode("return null;\n").build(); //return null is placeholder; Should call nodeservice.getProperty(qName) with the node and propertys qName

        if (handlingTypes) { //Maybe we can reuse javaTypeBuilder for aspect?
            javaClassBuilder.addField(getterToQNameField);
            javaClassBuilder.addMethod(getter);
            log.info("Adding to type\n:" + getterToQNameField + "\n" + getter);
        }
        fieldName = null;
        fieldClass = null;

    }
    
    protected void endTypeParent(String uri, String localName, String qName){
        String parentTag = stringBuilder.toString();
        stringBuilder = null;
        
        String[] type = parentTag.split(":");
        if (type.length != 2) {
            //I'm not sure if this case ever happens. So a warn log is here for awareness.
            log.warn(parentTag + " Parent did not contain a prefix");
            return;
        }
        String parentPrefix = type[0];
        String parentClassName = type[1];
        String parentNamespace = resolveNamespaceByPrefix(parentPrefix);
        List<String> parentPackages = getPackages(parentNamespace);
        
        parent = ClassName.get(getPackage(parentPackages), capitalize(parentClassName));
        
    }

    protected void endTypeChildAssociation(String uri, String localName, String qName) {

    }

    protected void endTypeAssociation(String uri, String localName, String qName) {

    }

    protected void endAspect(String uri, String localName, String qName) throws IOException {
        String packageName = getPackage(javaTypePackages);
        
        if(parent != null){
            javaInterfaceBuilder.addSuperinterface(parent);
            ClassName parentImplClassName = ClassName.get(parent.packageName(), parent.simpleName()+ASPECT_POSTFIX);
            javaClassBuilder.superclass(parentImplClassName);
        }
        
        TypeSpec aspect = javaClassBuilder.build();
        TypeSpec aspectImpl = javaInterfaceBuilder.addSuperinterface(aspect.getClass()).build();
        log.info("end aspect :\n" + aspect + "\n" + aspectImpl);

        fqcnToTypes.put(getFqcn(packageName, aspect.name), aspect);
        fqcnToTypes.put(getFqcn(packageName, aspectImpl.name), aspectImpl);
        parent = null;
        
        JavaFile.builder(packageName, aspect).build().writeTo(new File(sourceDir));
        JavaFile.builder(packageName, aspectImpl).build().writeTo(new File(sourceDir));
        
    }
    
    protected void endAspectParent(String uri, String localName, String qName) {
        endTypeParent(uri, localName, qName);
    }

    protected void endAspectMandatoryAspect(String uri, String localName, String qName) {

    }

    protected void endAspectProperty(String uri, String localName, String qName) {
        String[] classPrefixAndName = fieldClass.split(":");

        if (classPrefixAndName.length != 2) {
            //I'm not sure if this case ever happens. So a warn log is here for awareness.
            log.warn(fieldClass + " field did not contain a prefix");
            return;
        }

        String nameSpace = resolveNamespaceByPrefix(classPrefixAndName[0]);

        FieldSpec getterToQNameField = FieldSpec.builder(QName.class, fieldName, Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC).initializer("$T.createQName($S, $S)", QName.class, nameSpace, fieldName).build();

        String methodName = "get" + capitalize(fieldName);
        MethodSpec getter = MethodSpec.methodBuilder(methodName).returns(resolveType(nameSpace, classPrefixAndName[1])).addModifiers(Modifier.PUBLIC)
                .addCode("return null;\n").build();
        MethodSpec getterImpl = MethodSpec.methodBuilder(methodName).returns(resolveType(nameSpace, classPrefixAndName[1])).addModifiers(Modifier.PUBLIC)
                .addCode("return null;\n").build(); //return null is placeholder; Should call nodeservice.getProperty(qName) with the node and propertys qName

        javaInterfaceBuilder.addMethod(getter);
        javaClassBuilder.addField(getterToQNameField);
        javaClassBuilder.addMethod(getterImpl);
        log.info("Adding to type\n:" + getterToQNameField + "\n" + getter);

        fieldName = null;
        fieldClass = null;
    }

    protected void endAspectChildAssociation(String uri, String localName, String qName) {

    }

    protected void endAspectAssociation(String uri, String localName, String qName) {

    }

    protected void endPropertyClass(String uri, String localName, String qName) {
        fieldClass = stringBuilder.toString();
        stringBuilder = null;
        log.info("fieldClass: " + fieldClass);

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

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        super.startPrefixMapping(prefix, uri); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        super.endPrefixMapping(prefix); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (stringBuilder != null) {
            stringBuilder.append(Arrays.copyOfRange(ch, start, start + length));
        }
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
        super.fatalError(e); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
        super.error(e); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void warning(SAXParseException e) throws SAXException {
        super.warning(e); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName) throws SAXException {
        super.unparsedEntityDecl(name, publicId, systemId, notationName); //To change body of generated methods, choose Tools | Templates.
    }

    public static String capitalize(String toCapitalize) {
        return toCapitalize.substring(0, 1).toUpperCase() + toCapitalize.substring(1);
    }

    protected String resolveNamespaceByPrefix(String prefix) {
        String importNamespace = importMappings.get(prefix);
        if (importNamespace != null) {
            return importNamespace;
        }
        return nameSpaceMappings.get(prefix);
    }

    protected File getOutputPath(String sourceDir, List<String> packages, String fileName) throws FileNotFoundException {
        String fullPath = sourceDir;
        if (!fullPath.endsWith("/")) {
            fullPath = fullPath + "/";
        }
        for (String pkg : packages) {
            fullPath = fullPath + pkg + "/";
        }
        String relativeFileName = fullPath + fileName;

        return new File(relativeFileName);
    }

    protected MethodSpec buildGetter(FieldSpec spec) {
        return MethodSpec.methodBuilder("get" + capitalize(spec.name)).returns(spec.type).addModifiers(Modifier.PUBLIC).addCode("$[return this.$L$];$W", spec.name).build();
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

    protected String getFqcn(String packageName, String className) {
        return packageName + "." + className;
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

    public Map<String, TypeSpec> getFqcnToTypes() {
        return fqcnToTypes;
    }
}
