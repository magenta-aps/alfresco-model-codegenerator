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
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.ContextLoader;

/**
 *
 * @author martin
 */
public class NodeBase {

    //private ServiceRegistry serviceRegistry;

    private NodeFactory factory;
    
    private NodeRef nodeRef;

    private QName qName;

    public NodeRef getNodeRef() {
        return this.nodeRef;
    }

    public QName getQName() {
        return this.qName;
    }

    public NodeFactory getFactory() {
        return factory;
    }

    public void setFactory(NodeFactory factory) {
        this.factory = factory;
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
            nodeAspects.put(aspectName, factory.newAspect(aspectName, nodeRef));
        }
        return Collections.unmodifiableMap(nodeAspects);
    }
    
    public <T> T getAspect(Class<T> aspectClass){
        Name name = aspectClass.getAnnotation(Name.class);
        QName aspectQName = QName.createQName(name.namespace(), name.localName());
        return (T)factory.newAspect(aspectQName, nodeRef);
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
        return ContextLoader.getCurrentWebApplicationContext();
    }
    
    protected ServiceRegistry getServiceRegistry() {
        return factory.getServiceRegistry();
    }

    protected NodeService getNodeService() {
        return getServiceRegistry().getNodeService();
    }

    protected <T> List<T> getChildAssociations(Class<T> typeToReturn, QName assocName) {
        List<T> toReturn = new ArrayList<>();
        List<ChildAssociationRef> refs = getNodeService().getChildAssocs(getNodeRef(), assocName, null);
        for (ChildAssociationRef ref : refs) {
            toReturn.add((T) factory.newType(ref.getChildRef()));
        }
        return toReturn;
    }

    protected <T> List<T> getParentAssociations(Class<T> typeToReturn, QName assocName) {
        List<T> toReturn = new ArrayList<>();
        List<ChildAssociationRef> refs = getNodeService().getParentAssocs(getNodeRef(), assocName, null);
        for (ChildAssociationRef ref : refs) {
            toReturn.add((T) factory.newType(ref.getParentRef()));
        }
        return toReturn;
    }

    protected <T> List<T> getTargetAssociations(Class<T> typeToReturn, QName assocName) {
        List<T> toReturn = new ArrayList<>();
        List<AssociationRef> refs = getNodeService().getTargetAssocs(getNodeRef(), getQName());
        for (AssociationRef ref : refs) {
            toReturn.add((T) factory.newType(ref.getTargetRef()));
        }
        return toReturn;
    }

    protected <T> List<T> getSourceAssociations(Class<T> typeToReturn, QName assocName) {
        List<T> toReturn = new ArrayList<>();
        List<AssociationRef> refs = getNodeService().getSourceAssocs(getNodeRef(), getQName());
        for (AssociationRef ref : refs) {
            toReturn.add((T) factory.newType(ref.getSourceRef()));
        }
        return toReturn;
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
