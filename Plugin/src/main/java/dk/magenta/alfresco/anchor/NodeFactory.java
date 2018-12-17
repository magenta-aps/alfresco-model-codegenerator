/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco.anchor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.QName;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;

/**
 *
 * @author martin
 */
public class NodeFactory {

    public static String COULD_NOT_FIND_CLASS = "generated.code.cnf";
    public static final String ASPECT_POSTFIX = "Class";

    private ServiceRegistry serviceRegistry;

    protected ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
        //return getApplicationContext().getBean(ServiceRegistry.class);
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    protected NodeService getNodeService() {
        return getServiceRegistry().getNodeService();
        //return getApplicationContext().getBean("nodeService", NodeService.class);
    }

    public List<StoreRef> getStores() {
        return getNodeService().getStores();
    }

    protected String getPackageName(QName qName) {
        return getPackage(qName.getNamespaceURI());
    }

    public <T extends NodeBase> T getNode(NodeRef nodeRef, Class<T> type) {
        try {
            T node = type.cast(getNode(nodeRef));
            return node;
        } catch (RuntimeException ex) {
            throw new AlfrescoRuntimeException(COULD_NOT_FIND_CLASS, ex);
        }
    }

    public NodeBase getNode(NodeRef ref) {
        try {
            QName name = serviceRegistry.getNodeService().getType(ref);
            String fqcn = getPackage(name.getNamespaceURI()) + "." + capitalize(name.getLocalName());
            Class toBuild = Class.forName(fqcn);
            NodeBase newNode = (NodeBase) toBuild.newInstance();
            newNode.setNodeRef(ref);
            newNode.setQName(name);
            newNode.setFactory(this);
            return newNode;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            throw new AlfrescoRuntimeException(COULD_NOT_FIND_CLASS, ex);
        }
    }

    public Object newAspect(QName name, NodeRef owningNodeRef) {
        try {
            String fqcn = getPackage(name.getNamespaceURI()) + "." + capitalize(name.getLocalName()) + ASPECT_POSTFIX;
            Class toBuild = Class.forName(fqcn);
            NodeBase newAspect = (NodeBase) toBuild.newInstance();
            newAspect.setNodeRef(owningNodeRef);
            newAspect.setQName(name);
            newAspect.setFactory(this);
            return newAspect;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            throw new AlfrescoRuntimeException(COULD_NOT_FIND_CLASS, ex);
        }
    }

    public static String getPackage(String namespaceURI) {
        List<String> packagePrefixes = Arrays.asList(NodeBase.class.getPackage().getName().split("\\."));
        return getPackage(getPackages(packagePrefixes, namespaceURI));
    }

    public static List<String> getPackages(List<String> prefixes, String namespaceURI) {
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

        List<String> packages = new ArrayList<>(prefixes);
        for (String uriPackage : uriPackages) {
            if (uriPackage.substring(0, 1).matches("^\\d")) {
                uriPackage = "_" + uriPackage;
            }
            packages.add(uriPackage.replaceAll("\\.", "_"));
        }
        return packages;
    }

    public static String getPackage(List<String> packages) {
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

    public static String sanitize(String toSanitize) {
        return toSanitize.replaceAll("[-]", "_");
    }

    public static String capitalizeAndSanitize(String toHandle) {
        return capitalize(sanitize(toHandle));
    }
    
    public static String getNamespace(Class<? extends NodeBase> clazz){
        Name annotation = clazz.getAnnotation(Name.class);
        return annotation.namespace();
    }
    
    public static QName getQNameFromClass(Class<? extends NodeBase> clazz){
        Name annotation = clazz.getAnnotation(Name.class);
        return getQName(annotation.namespace(), annotation.localName());
    }
    
    public static QName getQName(String namespace, String localName){
        return QName.createQName(namespace, localName);
    }

    protected static NodeFactory getFactoryFromCurrentContext() {
        WebApplicationContext context = ContextLoader.getCurrentWebApplicationContext();
        if (context == null) {
            throw new RuntimeException("Could not find an ApplicationContext");
        }
        ServiceRegistry registry = context.getBean(ServiceRegistry.class);
        NodeFactory factory = new NodeFactory();
        factory.setServiceRegistry(registry);
        return factory;
    }

}
