/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco.anchor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;

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
        return aspectClass.cast(factory.newAspect(aspectQName, nodeRef));
    }
    
    public boolean hasAspect(Class<? extends Object> aspectClass){
        Name name = aspectClass.getAnnotation(Name.class);
        QName aspectQName = QName.createQName(name.namespace(), name.localName());
        return hasAspect(aspectQName);
    }
    
    public boolean hasAspect(QName aspect){
        return getNodeService().getAspects(nodeRef).contains(aspect);
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
            toReturn.add((T) factory.getNode(ref.getChildRef()));
        }
        return toReturn;
    }

    protected <T> List<T> getParentAssociations(Class<T> typeToReturn, QName assocName) {
        List<T> toReturn = new ArrayList<>();
        List<ChildAssociationRef> refs = getNodeService().getParentAssocs(getNodeRef(), assocName, null);
        for (ChildAssociationRef ref : refs) {
            toReturn.add((T) factory.getNode(ref.getParentRef()));
        }
        return toReturn;
    }

    protected <T> List<T> getTargetAssociations(Class<T> typeToReturn, QName assocName) {
        List<T> toReturn = new ArrayList<>();
        List<AssociationRef> refs = getNodeService().getTargetAssocs(getNodeRef(), getQName());
        for (AssociationRef ref : refs) {
            toReturn.add((T) factory.getNode(ref.getTargetRef()));
        }
        return toReturn;
    }

    protected <T> List<T> getSourceAssociations(Class<T> typeToReturn, QName assocName) {
        List<T> toReturn = new ArrayList<>();
        List<AssociationRef> refs = getNodeService().getSourceAssocs(getNodeRef(), getQName());
        for (AssociationRef ref : refs) {
            toReturn.add((T) factory.getNode(ref.getSourceRef()));
        }
        return toReturn;
    }
    
    protected <T extends Serializable> void setProperty(QName propertyName, T value){
        getNodeService().setProperty(nodeRef, qName, value);
    }
    
    protected <C extends NodeBase> void createChild(QName associationName, Class<C> childClass, NodeFactory.AbstractNodeConfiguration propertiesFactory){
        factory.createChildNode(this, associationName, childClass, propertiesFactory);
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
    
    public static interface PropertiesFactory<E extends NodeFactory.AbstractNodeConfiguration>{
        public void createProperties(E configuration);   
    }
    
//    public static abstract class AbstractPropertiesFactory{
//        private Map<QName, Serializable> properties = new HashMap<>();
//       
//        
//        protected void setProperty(QName key, Serializable value){
//            properties.put(key, value);
//        }
//
//        public Map<QName, Serializable> getProperties() {
//            return Collections.unmodifiableMap(properties);
//        }
//
//    }
//    
//    public static interface PropertiesSetter{
//        public void set();
//    }
//    
//    public static class PropertiesSetterImpl implements PropertiesSetter{
//        private final NodeRef targetRef;
//        private final NodeService service;
//        private final Map<QName, Serializable> properties;
//
//        public PropertiesSetterImpl(NodeRef targetRef, NodeService service, Map<QName, Serializable> properties) {
//            this.targetRef = targetRef;
//            this.service = service;
//            this.properties = properties;
//        }
//        
//        
//        @Override
//        public void set(){
//            service.addProperties(targetRef, properties);   
//        }
//    }
//    
//    public static interface NodeCreator<T extends NodeBase, P extends NodeBase>{
//        public T create();
//    }
//    
//    public static class NodeCreatorImpl<C extends NodeBase, P extends NodeBase> implements NodeCreator<C, P>{
//        private final P parent;
//        private final NodeFactory factory;
//        private final Class<C> newNodeType;
//        private final QName assocType;
//        private final QName nodeName;
//        private final Map<QName, Serializable> properties;
//        
//        
//        public NodeCreatorImpl(Class<C> newTypeClass, QName assocType, String nodeName, P parent, NodeFactory factory, Map<QName, Serializable> properties){
//            this(newTypeClass, assocType, NodeFactory.getQName(NodeFactory.getNamespace(newTypeClass), nodeName), parent, factory, properties);
//        }
//        
//        public NodeCreatorImpl(Class<C> newTypeClass, QName assocType, String nodeNamespace, String nodeName, P parent, NodeFactory factory, Map<QName, Serializable> properties){
//            this(newTypeClass, assocType, NodeFactory.getQName(nodeNamespace, nodeName), parent, factory, properties);
//        }
//        
//        protected NodeCreatorImpl(Class<C> newNodeType, QName assocType, QName nodeName, P parent, NodeFactory factory, Map<QName, Serializable> properties) {
//            this.parent = parent;
//            this.newNodeType = newNodeType;
//            this.assocType = assocType;
//            this.nodeName = nodeName;
//            this.factory = factory;
//            this.properties = properties;
//        }
//        
//        @Override
//        public C create(){   
//            return factory.createChildNode(parent, assocType, nodeName, newNodeType, properties);
//        }
//    }
//    
//    public static abstract class NodeBuilder<C extends NodeBase, P extends NodeBase>{
//        private P parent;
//        private NodeFactory factory;
//        private Class<C> newNodeType;
//        private QName assocType;
//        private QName nodeName;
//        private final Map<QName, Serializable> properties;
//
//        public NodeBuilder(Map<QName, Serializable> properties) {
//            this.properties = properties;
//        }
//
//        public P getParent() {
//            return parent;
//        }
//
//        void setParent(P parent) {
//            this.parent = parent;
//        }
//
//        public NodeFactory getFactory() {
//            return factory;
//        }
//
//        void setFactory(NodeFactory factory) {
//            this.factory = factory;
//        }
//
//        public Class<C> getNewNodeType() {
//            return newNodeType;
//        }
//
//        void setNewNodeType(Class<C> newNodeType) {
//            this.newNodeType = newNodeType;
//        }
//
//        public QName getAssocType() {
//            return assocType;
//        }
//
//        void setAssocType(QName assocType) {
//            this.assocType = assocType;
//        }
//
//        public QName getNodeName() {
//            return nodeName;
//        }
//
//        void setNodeName(QName nodeName) {
//            this.nodeName = nodeName;
//        }
//        
//        
//        
//    }
}
