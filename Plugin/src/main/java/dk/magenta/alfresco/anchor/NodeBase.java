/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco.anchor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.ApplicationContextHelper;
import org.springframework.context.ApplicationContext;

/**
 *
 * @author martin
 */
public class NodeBase {

    public static String COULD_NOT_FIND_CLASS = "generated.code.cnf";
    public static final String ASPECT_POSTFIX = "Class";

    private NodeRef nodeRef;

    private QName qName;

    public NodeRef getNodeRef() {
        return this.nodeRef;
    }

    public QName getQName() {
        return this.qName;
    }

    void setNodeRef(NodeRef nodeRef) {
        this.nodeRef = nodeRef;
    }

    void setQName(QName qName) {
        this.qName = qName;
    }

    public Map<QName, Object> getAspects() {
        Map<QName, Object> nodeAspects = new HashMap<>();
        Set<QName> aspects = getNodeService().getAspects(nodeRef);
        for (QName aspectName : aspects) {
            nodeAspects.put(aspectName, newAspect(aspectName, nodeRef));
        }
        return Collections.unmodifiableMap(nodeAspects);
    }
    
    public <T> T getAspect(Class<T> aspectClass){
        Name name = aspectClass.getAnnotation(Name.class);
        QName aspectQName = QName.createQName(name.namespace(), name.localName());
        return (T)newAspect(aspectQName, nodeRef);
    }
    
    public boolean hasAspect(Class<? extends Object> aspectClass){
        Name name = aspectClass.getAnnotation(Name.class);
        QName aspectQName = QName.createQName(name.namespace(), name.localName());
        return hasAspect(aspectQName);
    }
    
    public boolean hasAspect(QName aspect){
        return getNodeService().getAspects(nodeRef).contains(aspect);
    }
    
    protected static ApplicationContext getApplicationContext() {
        return ApplicationContextHelper.getApplicationContext();
    }
    
    protected static ServiceRegistry getServiceRegistry() {
        return getApplicationContext().getBean(ServiceRegistry.class);
    }

    protected static NodeService getNodeService() {
        return getServiceRegistry().getNodeService();
        //return getApplicationContext().getBean("nodeService", NodeService.class);
    }

    protected <T> List<T> getChildAssociations(Class<T> typeToReturn, QName assocName) {
        List<T> toReturn = new ArrayList<>();
        List<ChildAssociationRef> refs = getNodeService().getChildAssocs(getNodeRef(), assocName, null);
        for (ChildAssociationRef ref : refs) {
            toReturn.add((T) newType(ref.getChildRef()));
        }
        return toReturn;
    }

    protected <T> List<T> getParentAssociations(Class<T> typeToReturn, QName assocName) {
        List<T> toReturn = new ArrayList<>();
        List<ChildAssociationRef> refs = getNodeService().getParentAssocs(getNodeRef(), assocName, null);
        for (ChildAssociationRef ref : refs) {
            toReturn.add((T) newType(ref.getParentRef()));
        }
        return toReturn;
    }

    protected <T> List<T> getTargetAssociations(Class<T> typeToReturn, QName assocName) {
        List<T> toReturn = new ArrayList<>();
        List<AssociationRef> refs = getNodeService().getTargetAssocs(getNodeRef(), getQName());
        for (AssociationRef ref : refs) {
            toReturn.add((T) newType(ref.getTargetRef()));
        }
        return toReturn;
    }

    protected <T> List<T> getSourceAssociations(Class<T> typeToReturn, QName assocName) {
        List<T> toReturn = new ArrayList<>();
        List<AssociationRef> refs = getNodeService().getSourceAssocs(getNodeRef(), getQName());
        for (AssociationRef ref : refs) {
            toReturn.add((T) newType(ref.getSourceRef()));
        }
        return toReturn;
    }

    protected Object newType(NodeRef ref) {
        QName nodeType = getNodeService().getType(ref);
        return getNode(nodeType, ref);
    }

    protected String getPackageName(QName qName) {
        return getPackage(qName.getNamespaceURI());
    }
    
    public static List<StoreRef> getStores(){
        return getNodeService().getStores();
    }

    public static NodeBase getNode(QName name, NodeRef ref) {
        try {
            String fqcn = getPackage(name.getNamespaceURI()) + "." + name.getLocalName();
            Class toBuild = Class.forName(fqcn);
            NodeBase newNode = (NodeBase) toBuild.newInstance();
            newNode.setNodeRef(ref);
            newNode.setQName(name);

            return newNode;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            throw new AlfrescoRuntimeException(COULD_NOT_FIND_CLASS, ex);
        }
    }
    
    public static <T extends NodeBase> T getNode(Class<T> type, NodeRef ref){
        Name name = type.getAnnotation(Name.class);
        QName typeQname = QName.createQName(name.namespace(), name.localName());
        return (T)getNode(typeQname, ref);
    }

    public static Object newAspect(QName name, NodeRef owningNodeRef) {
        try {
            String fqcn = getPackage(name.getNamespaceURI()) + "." + name.getLocalName()+ASPECT_POSTFIX;
            Class toBuild = Class.forName(fqcn);
            NodeBase newAspect = (NodeBase) toBuild.newInstance();
            newAspect.setNodeRef(owningNodeRef);
            newAspect.setQName(name);
            return newAspect;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            throw new AlfrescoRuntimeException(COULD_NOT_FIND_CLASS, ex);
        }
    }

    public static String getPackage(String namespaceURI) {
        List<String> packagePrefixes = Arrays.asList(NodeBase.class.getPackage().getName().split("."));
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

    public static <T> T getNode(NodeRef nodeRef, Class<T> type) {
        try {
            QName typeQName = getNodeService().getType(nodeRef);
            T node = (T) getNode(typeQName, nodeRef);
            return node;
        } catch (RuntimeException ex) {
            throw new AlfrescoRuntimeException(COULD_NOT_FIND_CLASS, ex);
        }
    }
    
    public static enum Store {
        USER_STORE("user", "alfrescoUserStore"), SYSTEM("system","system"), LIGHTWEIGHT_VERSION_STORE("workspace", "lightWeightVersionStore"), VERSION_2_STORE("workspace","version2Store"), 
        ARCHIVE("archive","SpacesStore"), WORKSPACE("workspace","SpacesStore");
        
        String protocol;
        String identifier;

        private Store(String protocol, String identifier) {
            this.protocol = protocol;
            this.identifier =  identifier;
        }
        
        
        
    }
}
