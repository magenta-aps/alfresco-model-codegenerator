/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco;

import org.alfresco.util.ApplicationContextHelper;
import static dk.magenta.alfresco.GenerateJavaMojo.DICTIONARY_URI;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import static dk.magenta.alfresco.GenerateJavaMojo.DICTIONARY_URI;
import dk.magenta.alfresco.anchor.Anchor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;
import javax.crypto.SealedObject;
import javax.lang.model.element.Modifier;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
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
import org.w3c.dom.Element;
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
    public static final String ASSOCIATION_CLASS_TAG = "class";
    public static final String ASSOCIATION_ROLE_TAG = "role";

    public static final String TITLE_TAG = "title";

    public static final String ASPECT_POSTFIX = "Class";

    private final Log log;
    private final List<String> packagePrefixes;
    private final String sourceDir;
    private final ClassName anchorClass;
    private final String encoding = "UTF-8";

    private final Map<String, String> importMappings = new HashMap<>();
    private final Map<String, String> nameSpaceMappings = new HashMap<>();

    boolean associationSourceMandatory = false;
    boolean associationSourceMany = false;
    boolean associationTargetMandatory = false;
    boolean associationTargetMany = false;

    private Map<String, TypeSpec.Builder> fqcnToTypes = new HashMap<>();
    private Map<String, List<MethodSpec>> methodsForClass = new HashMap<>();

    public DOMModuleParser(Log log, List<String> packagePrefixes, String sourceDir, ClassName anchorClass) {
        this.log = log;
        this.packagePrefixes = packagePrefixes;
        this.sourceDir = sourceDir;
        this.anchorClass = anchorClass;
    }

    public void execute(InputStream stream) throws ParserConfigurationException, SAXException, IOException {

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder;
        dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(stream);
        doc.getDocumentElement().normalize();
        //printNodes(doc.getDocumentElement(), 0);
        //printNodeList(doc.getDocumentElement().getElementsByTagNameNS(DICTIONARY_URI, IMPORTS_TAG).item(0).getChildNodes());
        if (doc.getDocumentElement().getElementsByTagNameNS(DICTIONARY_URI, IMPORTS_TAG).getLength() > 0) {
            handleImports(doc.getDocumentElement().getElementsByTagNameNS(DICTIONARY_URI, IMPORTS_TAG).item(0).getChildNodes());
        }
        if (doc.getDocumentElement().getElementsByTagNameNS(DICTIONARY_URI, NAMESPACES_TAG).getLength() > 0) {
            handleNamespaces(doc.getDocumentElement().getElementsByTagNameNS(DICTIONARY_URI, NAMESPACES_TAG).item(0).getChildNodes());
        }

        if (doc.getDocumentElement().getElementsByTagNameNS(DICTIONARY_URI, TYPES_TAG).getLength() > 0) {
            handleTypes(doc.getDocumentElement().getElementsByTagNameNS(DICTIONARY_URI, TYPES_TAG).item(0).getChildNodes());
        }

        if (doc.getDocumentElement().getElementsByTagNameNS(DICTIONARY_URI, ASPECTS_TAG).getLength() > 0) {
            handleAspects(doc.getDocumentElement().getElementsByTagNameNS(DICTIONARY_URI, ASPECTS_TAG).item(0).getChildNodes());
        }
    }
//
//    private void printNodeList(NodeList list) {
//        for (int i = 0; i < list.getLength(); i++) {
//            printNodes(list.item(i), 0);
//        }
//    }
//
//    private void printNodes(Node node, int indention) {
//        System.out.println(getIndention(indention) + node.getNamespaceURI() + " " + node.getLocalName() + " " + node.getTextContent() + ": " + node.toString());
//        NodeList list = node.getChildNodes();
//        for (int i = 0; i < list.getLength(); i++) {
//            printNodes(list.item(i), indention + 4);
//        }
//    }

    public String getIndention(int indention) {
        String toReturn = "";
        for (int i = 0; i < indention; i++) {
            toReturn = toReturn + " ";
        }
        return toReturn;
    }

    public void saveFiles() throws IOException {
        for (String fqcn : fqcnToTypes.keySet()) {
            TypeSpec.Builder typeBuilder = fqcnToTypes.get(fqcn);
            String packageName = getPackageFromFqcn(fqcn);
            JavaFile javaFile = JavaFile.builder(packageName, typeBuilder.build()).build();
            javaFile.writeTo(new File(sourceDir));
        }
    }

    public Map<String, TypeSpec.Builder> getParsedTypes() {
        return Collections.unmodifiableMap(fqcnToTypes);
    }

    protected void handleImports(NodeList imports) {
        for (int i = 0; i < imports.getLength(); i++) {
            Node importNode = imports.item(i);
            if (matches(importNode, DICTIONARY_URI, IMPORT_TAG)) {
                String uri = importNode.getAttributes().getNamedItem("uri").getNodeValue();
                String prefix = importNode.getAttributes().getNamedItem("prefix").getNodeValue();
                importMappings.put(prefix, uri);
            } else {
                log.warn("Found unmatched node in imports: " + importNode);
            }

        }

    }

    protected void handleNamespaces(NodeList nameSpaces) {
        for (int i = 0; i < nameSpaces.getLength(); i++) {
            Node namespaceNode = nameSpaces.item(i);
            if (matches(namespaceNode, DICTIONARY_URI, NAMESPACE_TAG)) {
                String uri = namespaceNode.getAttributes().getNamedItem("uri").getNodeValue();
                String prefix = namespaceNode.getAttributes().getNamedItem("prefix").getNodeValue();
                nameSpaceMappings.put(prefix, uri);
            } else {
                log.warn("Found unmatched node in namespaces: " + namespaceNode);
            }

        }
    }

    protected void handleTypes(NodeList types) throws IOException {
        for (int typeIndex = 0; typeIndex < types.getLength(); typeIndex++) {
            Node typeNode = types.item(typeIndex);
            if (matches(typeNode, DICTIONARY_URI, TYPE_TAG)) {
                QName typeQName = getNameAttribute(typeNode);

                List<String> javaTypePackages = Anchor.getPackages(packagePrefixes, typeQName.getNamespaceURI());
                String packageName = Anchor.getPackage(javaTypePackages);
                ClassName clazz = ClassName.get(packageName, capitalize(typeQName.getLocalName()));
                TypeSpec.Builder javaClassBuilder = TypeSpec.classBuilder(clazz).addModifiers(Modifier.PUBLIC);

                //Add any methods created by parsing other types, such as bidirectional association methods
                List<MethodSpec> aspectMethods = methodsForClass.get(getFqcn(packageName, clazz.simpleName()));
                if (aspectMethods != null) {
                    javaClassBuilder.addMethods(aspectMethods);
                }

                String title = null;
                ClassName parent = anchorClass;

                NodeList typeChildren = typeNode.getChildNodes();
                for (int typeChildIndex = 0; typeChildIndex < typeChildren.getLength(); typeChildIndex++) {
                    Node typeChild = typeChildren.item(typeChildIndex);
                    if (DICTIONARY_URI.equals(typeChild.getNamespaceURI())) {
                        switch (typeChild.getLocalName()) {
                            case TITLE_TAG:
                                title = getNodeValue(typeNode);
                                break;
                            case PARENT_TAG:
                                parent = classNameFromQName(getQName(getNodeValue(typeChild)));
                                break;
                            case PROPERTIES_TAG:
                                addProperties(null, javaClassBuilder, typeChild.getChildNodes());
                                break;
                            case ASSOCIATIONS_TAG:
                                addAssociations(null, javaClassBuilder, typeChild.getChildNodes());
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
                javaClassBuilder.superclass(parent);
                fqcnToTypes.put(getFqcn(packageName, clazz.simpleName()), javaClassBuilder);

//                javaClassBuilder.superclass(parent);
//                JavaFile javaFile = JavaFile.builder(packageName, javaClassBuilder.build()).build();
//                javaFile.writeTo(new File(sourceDir));
            }
        }
    }

    protected void handleAspects(NodeList aspects) throws IOException {
        for (int aspectIndex = 0; aspectIndex < aspects.getLength(); aspectIndex++) {
            Node aspectNode = aspects.item(aspectIndex);
            if (matches(aspectNode, DICTIONARY_URI, ASPECT_TAG)) {
                QName aspectName = getNameAttribute(aspectNode);

                List<String> javaTypePackages = Anchor.getPackages(packagePrefixes, aspectName.getNamespaceURI());
                String packageName = Anchor.getPackage(javaTypePackages);
                String className = capitalizeAndSanitize(aspectName.getLocalName());
                ClassName aspectClass = ClassName.get(packageName, className);
                ClassName aspectImplClass = ClassName.get(packageName, className + ASPECT_POSTFIX);
                TypeSpec.Builder javaInterfaceBuilder = TypeSpec.interfaceBuilder(aspectClass).addModifiers(Modifier.PUBLIC);
                TypeSpec.Builder javaClassBuilder = TypeSpec.classBuilder(aspectImplClass).addModifiers(Modifier.PUBLIC);

                //Add any methods created by parsing other types, such as bidirectional association methods
                List<MethodSpec> aspectMethods = methodsForClass.get(getFqcn(packageName, aspectClass.simpleName()));
                if (aspectMethods != null) {
                    javaInterfaceBuilder.addMethods(aspectMethods);
                }

                List<MethodSpec> implMethods = methodsForClass.get(getFqcn(packageName, aspectImplClass.simpleName()));
                if (implMethods != null) {
                    javaInterfaceBuilder.addMethods(implMethods);
                }

                String aspectTitle = null;
                ClassName parent = null;
                boolean archive = false;
                NodeList aspectChildren = aspectNode.getChildNodes();
                for (int aspectChildIndex = 0; aspectChildIndex < aspectChildren.getLength(); aspectChildIndex++) {
                    Node aspectChild = aspectChildren.item(aspectChildIndex);
                    if (DICTIONARY_URI.equals(aspectChild.getNamespaceURI())) {
                        switch (aspectChild.getLocalName()) {
                            case TITLE_TAG:
                                aspectTitle = getNodeValue(aspectChild);
                                break;
                            case PARENT_TAG:
                                parent = classNameFromQName(getQName(getNodeValue(aspectChild)));
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
                if (parent != null) {
                    ClassName parentImplClass = ClassName.get(parent.packageName(), parent.simpleName() + ASPECT_POSTFIX);
                    javaInterfaceBuilder.addSuperinterface(parent);
                    javaClassBuilder.superclass(parentImplClass);
                } else {
                    javaClassBuilder.superclass(anchorClass);
                }
                javaClassBuilder.addSuperinterface(aspectClass);

//                JavaFile interfaceFile = JavaFile.builder(packageName, javaInterfaceBuilder.build()).build();
//                interfaceFile.writeTo(new File(sourceDir));
//                JavaFile classFile = JavaFile.builder(packageName, javaClassBuilder.build()).build();
//                classFile.writeTo(new File(sourceDir));
                TypeSpec aspect = javaInterfaceBuilder.build();
                TypeSpec aspectImpl = javaClassBuilder.build();
                fqcnToTypes.put(getFqcn(packageName, aspectClass.simpleName()), javaInterfaceBuilder);
                fqcnToTypes.put(getFqcn(packageName, aspectImplClass.simpleName()), javaClassBuilder);

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
                QName fieldType = null;
                boolean mandatory = false;

                NodeList propertyChildren = propertyNode.getChildNodes();
                for (int propertyChildIndex = 0; propertyChildIndex < propertyChildren.getLength(); propertyChildIndex++) {
                    Node propertyChild = propertyChildren.item(propertyChildIndex);
                    if (DICTIONARY_URI.equals(propertyChild.getNamespaceURI())) {
                        switch (propertyChild.getLocalName()) {
                            case TITLE_TAG:
                                title = getNodeValue(propertyChild);
                                break;
                            case TYPE_TAG:
                                fieldType = getQName(getNodeValue(propertyChild));
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

                if (fieldType == null) {
                    throw new RuntimeException("Could not parse property " + classQName + ". Could not find a type");//Replace with something less runtime
                }

                try {
                    String propertyFieldName = sanitize(classQName.getLocalName());
                    String getterMethodName = capitalizeAndSanitize(classQName.getLocalName());

                    Class methodReturnType = resolveType(fieldType.getNamespaceURI(), fieldType.getLocalName());
                    if (interfaceBuilder != null) {
                        interfaceBuilder.addMethod(MethodSpec.methodBuilder("get" + getterMethodName).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).returns(methodReturnType).build());
                    }
                    classBuilder.addField(FieldSpec.builder(QName.class, propertyFieldName, Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC).initializer("$T.createQName($S, $S)", QName.class, classQName.getNamespaceURI(), classQName.getLocalName()).build());
                    MethodSpec.Builder getterMethod = MethodSpec.methodBuilder("get" + getterMethodName).addModifiers(Modifier.PUBLIC).returns(methodReturnType)
                            .addCode("$[return ($T)getNodeService().getProperty(getNodeRef(), $L)$];\n", methodReturnType, propertyFieldName);
                    if (interfaceBuilder != null) {
                        getterMethod.addAnnotation(Override.class);
                    }
                    classBuilder.addMethod(getterMethod.build());
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to parse property:\n" + nodeToString(propertyNode), ex);//Do something less runtime
                }

            } else {
                log.warn("Found unmatched node in properties: " + propertyNode);
            }
        }
    }

    protected void addAssociations(TypeSpec.Builder interfaceBuilder, TypeSpec.Builder classBuilder, NodeList associations) {
        for (int assocIndex = 0; assocIndex < associations.getLength(); assocIndex++) {
            Node associationNode = associations.item(assocIndex);
            if (matches(associationNode, DICTIONARY_URI, ASSOCIATION_TAG) || matches(associationNode, DICTIONARY_URI, CHILD_ASSOCIATION_TAG)) {
                QName associationName = getNameAttribute(associationNode);
                SourceTag source = null;
                TargetTag target = null;

                NodeList associationChildren = associationNode.getChildNodes();
                for (int associationChildIndex = 0; associationChildIndex < associationChildren.getLength(); associationChildIndex++) {
                    Node associationChild = associationChildren.item(associationChildIndex);
                    if (DICTIONARY_URI.equals(associationChild.getNamespaceURI())) {
                        switch (associationChild.getLocalName()) {
                            case ASSOCIATION_SOURCE_TAG:
                                source = handleSourceTag(associationChild);
                                break;
                            case ASSOCIATION_TARGET_TAG:
                                target = handleTargetTag(associationChild);
                                break;
                            default:
                                log.warn("Encountered unknown tag in association:\n" + nodeToString(associationChild));

                        }
                    } else {
                        log.warn("Unexpected namespace inside association:\n" + nodeToString(associationChild));
                    }
                }
                
                if(source == null || target == null){
                    throw new RuntimeException("Could not parse association. Source or Target tag was missing:\n"+nodeToString(associationNode));//Do something less runtimey
                }
                
                //Create towards-target methods
                
                addOutboundMethod(interfaceBuilder, classBuilder, associationName, target.isMany(), target.getType(), matches(associationNode, DICTIONARY_URI, CHILD_ASSOCIATION_TAG));
                
            }
        }
    }
    
    private void addOutboundMethod(TypeSpec.Builder interfaceBuilder, TypeSpec.Builder classBuilder, QName assocName, boolean many, ClassName targetClass, boolean isChild){
        ClassName list = ClassName.get("java.util", "List");
                String targetName = targetClass.simpleName();
                String methodName = "get"+assocName.getLocalName();
                String associationFieldName = sanitize(assocName.getLocalName()+"Association");
                TypeName listOfType = ParameterizedTypeName.get(list, targetClass);
                TypeName returnType;
                if(many){
                    returnType = listOfType;
                }else{
                    returnType =targetClass;
                }
                
                FieldSpec.Builder field = FieldSpec.builder(QName.class, associationFieldName, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL).initializer("$T.createQName($S, $S)", QName.class, assocName.getNamespaceURI(), assocName.getLocalName());
                MethodSpec.Builder method = MethodSpec.methodBuilder(methodName).returns(returnType).addModifiers(Modifier.PUBLIC);
                if(interfaceBuilder != null){
                    interfaceBuilder.addMethod(method.build());
                }
                if(interfaceBuilder != null){
                    method.addAnnotation(Override.class);
                }
                if(isChild){
                    if(many){
                        method.addCode("$[return getChildAssociations($T.class, $L);$]\n", targetClass, associationFieldName);
                    }else{
                        method.addCode("$[$T types = getChildAssociations($T.class, $L);$]\n$[return types != null && !types.isEmpty() ? types.get(0) : return null;$]\n", listOfType, targetClass, associationFieldName);
                    }
                }else{
                    if(many){
                        method.addCode("$[return getTargetAssociations($T.class, $L);$]\n", targetClass, associationFieldName);
                    }else{
                        method.addCode("$[$T types = getTargetAssociations($T.class, $L);$]\n$[return types != null && !types.isEmpty() ? types.get(0) : return null;$]\n", listOfType, targetClass, associationFieldName);

                    }
                }
                
                classBuilder.addField(field.build());
                classBuilder.addMethod(method.build());
                
    }

    private SourceTag handleSourceTag(Node source) {
        NodeList sourceChildren = source.getChildNodes();
        SourceTag toReturn = new SourceTag();
        for (int sourceChildIndex = 0; sourceChildIndex < sourceChildren.getLength(); sourceChildIndex++) {
            Node sourceChild = sourceChildren.item(sourceChildIndex);
            if (DICTIONARY_URI.equals(sourceChild.getNamespaceURI())) {
                switch (sourceChild.getLocalName()) {
                    case ASSOCIATION_MANDATORY_TAG:
                        String mandatory = getNodeValue(sourceChild);
                        if (mandatory.equalsIgnoreCase("true")) {
                            toReturn.setMandatory(true);
                        } else {
                            toReturn.setMandatory(false);
                        }
                        break;
                    case ASSOCIATION_MANY_TAG:
                        String many = getNodeValue(sourceChild);
                        if (many.equalsIgnoreCase("true")) {
                            toReturn.setMany(true);
                        } else {
                            toReturn.setMany(false);
                        }
                        break;
                    case ASSOCIATION_ROLE_TAG:
                        toReturn.setRole(getNodeValue(sourceChild));
                        break;
                    default:
                        log.warn("Encountered unknown tag in association:\n" + nodeToString(sourceChild));

                }
            } else {
                log.warn("Unexpected namespace inside association:\n" + nodeToString(sourceChild));
            }
        }
        return toReturn;

    }

    private TargetTag handleTargetTag(Node target) {
        NodeList targetChildren = target.getChildNodes();
        TargetTag toReturn = new TargetTag();
        for (int targetChildrenIndex = 0; targetChildrenIndex < targetChildren.getLength(); targetChildrenIndex++) {
            Node targetChild = targetChildren.item(targetChildrenIndex);
            if (DICTIONARY_URI.equals(targetChild.getNamespaceURI())) {
                switch (targetChild.getLocalName()) {
                    case ASSOCIATION_MANDATORY_TAG:
                        String mandatory = getNodeValue(targetChild);
                        if (mandatory.equalsIgnoreCase("true")) {
                            toReturn.setMandatory(true);
                        } else {
                            toReturn.setMandatory(false);
                        }
                        break;
                    case ASSOCIATION_MANY_TAG:
                        String many = getNodeValue(targetChild);
                        if (many.equalsIgnoreCase("true")) {
                            toReturn.setMany(true);
                        } else {
                            toReturn.setMany(false);
                        }
                        break;
                    case ASSOCIATION_ROLE_TAG:
                        toReturn.setRole(getNodeValue(targetChild));
                        break;
                    case ASSOCIATION_CLASS_TAG:
                        String targetClass = getNodeValue(targetChild);
                        QName className = getQName(targetClass);
                        toReturn.setType(ClassName.get(Anchor.getPackage(Anchor.getPackages(packagePrefixes, className.getNamespaceURI())), capitalizeAndSanitize(className.getLocalName())));
                    default:
                        log.warn("Encountered unknown tag in association:\n" + nodeToString(targetChild));

                }
            } else {
                log.warn("Unexpected namespace inside association:\n" + nodeToString(targetChild));
            }
        }
        return toReturn;
    }

    protected void addMandatoryAspectsToClass(TypeSpec.Builder classBuilder, NodeList mandatoryAspects) {

    }

    protected ClassName classNameFromQName(QName name) {
        return ClassName.get(Anchor.getPackage(Anchor.getPackages(packagePrefixes, name.getNamespaceURI())), capitalizeAndSanitize(name.getLocalName()));
    }

    protected String getNodeValue(Node titleNode) {
        return titleNode.getTextContent();
    }

    protected QName getNameAttribute(Node node) {
        return getQName(node.getAttributes().getNamedItem("name").getNodeValue());
    }

    protected QName getQName(String prefixedName) {
        String[] type = prefixedName.split(":");
        if (type.length != 2) {
            //I'm not sure if this case ever happens. So a warn log is here for awareness.
            log.warn(prefixedName + " did not contain a prefix");
            return null;
        }

        String classPrefix = type[0];
        String className = type[1];

        //String classNamespace = resolveNamespaceByPrefix(classPrefix);
        String classNamespace = resolveNamespaceByPrefix(classPrefix);

        if (classNamespace == null) {
            log.error("Found type not matched by namespace: " + prefixedName);
        }

        return QName.createQName(classNamespace, className);

    }

//    public  List<String> getPackages(String namespaceURI) {
//        Scanner uriScanner = new Scanner(namespaceURI).useDelimiter(Pattern.compile("[\\/]"));
//        List<String> uriPackages = new LinkedList<>();
//        while (uriScanner.hasNext()) {
//            uriPackages.add(uriScanner.next());
//        }
//        while (uriPackages.get(0).startsWith("http") || uriPackages.get(0).isEmpty()) {
//            uriPackages.remove(0);
//        }
//        if (uriPackages.get(0).contains(".")) {
//            String addressPart = uriPackages.remove(0);
//            Scanner addressScanner = new Scanner(addressPart).useDelimiter("[\\.]");
//            while (addressScanner.hasNext()) {
//                String token = addressScanner.next();
//                if (token.equals("www")) {
//                    continue;
//                }
//                uriPackages.add(0, token);
//
//            }
//        }
//
//        List<String> packages = new ArrayList<>(packagePrefixes);
//        for (String uriPackage : uriPackages) {
//            if (uriPackage.substring(0, 1).matches("^\\d")) {
//                uriPackage = "_" + uriPackage;
//            }
//            packages.add(uriPackage.replaceAll("\\.", "_"));
//        }
//        return packages;
//    }
//
//    protected String getPackage(List<String> packages) {
//        boolean first = true;
//        String packageString = "";
//        for (String pkg : packages) {
//            if (!first) {
//                packageString = packageString + ".";
//            }
//            packageString = packageString + pkg;
//            first = false;
//        }
//        return packageString;
//    }

    public static String capitalize(String toCapitalize) {
        return toCapitalize.substring(0, 1).toUpperCase() + toCapitalize.substring(1);
    }

    public static String sanitize(String toSanitize) {
        return toSanitize.replaceAll("[-]", "_");
    }

    public static String capitalizeAndSanitize(String toHandle) {
        return capitalize(sanitize(toHandle));

    }

    public boolean matches(Node node, String uri, String localName) {
        return uri.equals(node.getNamespaceURI()) && localName.equals(node.getLocalName());

    }

    protected Class resolveType(String nameSpace, String localName) {
        if (nameSpace.equals(DICTIONARY_URI)) { //Do we need to match other name spaces here as well, or is this fine until we start matching data-type tags?
            switch (localName) {
                case "encrypted":
                    return SealedObject.class;
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
                    return null;

            }

        }
        return null; //If nothing matches, fail horribly
    }

    protected String resolveNamespaceByPrefix(String prefix) {
        String importNamespace = importMappings.get(prefix);
        if (importNamespace != null) {
            return importNamespace;
        }
        return nameSpaceMappings.get(prefix);
    }

    protected String getFqcn(String packageName, String className) {
        return packageName + "." + className;
    }

    protected String getPackageFromFqcn(String fqcn) {
        return fqcn.substring(0, fqcn.lastIndexOf("."));
    }

    private String nodeToString(Node node) {
        if (node == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        try {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.transform(new DOMSource(node), new StreamResult(sw));
        } catch (TransformerException te) {
            log.warn("Attempted to transport XML for node " + node.getBaseURI());
        }
        return sw.toString();
    }

    private static class SourceTag {

        private boolean mandatory;
        private boolean many;
        private String role;

        public SourceTag() {
        }

        public SourceTag(boolean mandatory, boolean many, String role) {
            this.mandatory = mandatory;
            this.many = many;
            this.role = role;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public boolean isMandatory() {
            return mandatory;
        }

        public void setMandatory(boolean mandatory) {
            this.mandatory = mandatory;
        }

        public boolean isMany() {
            return many;
        }

        public void setMany(boolean many) {
            this.many = many;
        }

    }

    private static class TargetTag extends SourceTag {

        private ClassName type;

        public TargetTag() {
        }

        public TargetTag(ClassName type) {
            this.type = type;
        }

        public TargetTag(ClassName type, boolean mandatory, boolean many, String role) {
            super(mandatory, many, role);
            this.type = type;
        }

        public ClassName getType() {
            return type;
        }

        public void setType(ClassName type) {
            this.type = type;
        }

    }

}
