/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco.generated;

import dk.magenta.alfresco.generated.org.alfresco.model.content._1_0.Folder;
import dk.magenta.alfresco.generated.org.alfresco.model.system._1_0.Aspect_rootClass;
import dk.magenta.alfresco.generated.org.alfresco.model.system._1_0.Base;
import dk.magenta.alfresco.generated.org.alfresco.model.system._1_0.Store_root;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import junit.framework.AssertionFailedError;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.web.scripts.BaseWebScriptTest;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;

/**
 *
 * @author martin
 */
public class Tests extends BaseWebScriptTest {

    public static final String DICTIONARY_PATH = "/app:company_home/app:dictionary";
    
    private final ServiceRegistry serviceRegistry = (ServiceRegistry) getServer().getApplicationContext().getBean("ServiceRegistry", ServiceRegistry.class);
    private NodeFactory factory;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        AuthenticationUtil.setAdminUserAsFullyAuthenticatedUser();
        factory = new NodeFactory();
        factory.setServiceRegistry(serviceRegistry);
//        TestUtils.wipeData(serviceRegistry);
//        TestUtils.setupSimpleFlow(serviceRegistry);
    }

    @Override
    protected void tearDown() throws Exception {
//        TestUtils.wipeData(serviceRegistry);
    }
    
    public void testWorkspaces() throws Exception{
        AuthenticationUtil.setAdminUserAsFullyAuthenticatedUser();
        List<StoreRef> baseRefs = serviceRegistry.getNodeService().getStores();
        List<StoreRef> generatedRefsNodeBase = factory.getStores();
        containsSameElements(baseRefs, generatedRefsNodeBase);

    }
    
    public void testGetCompanyHome(){
        StoreRef workspaceRef = new StoreRef("workspace", "SpacesStore");
        NodeRef rootNodeRef = serviceRegistry.getNodeService().getRootNode(workspaceRef);
        Base rootNode = factory.getNode(rootNodeRef, Base.class);
        assertEquals(Store_root.class, rootNode.getClass());
        assertEquals(Store_root.class.getAnnotation(Name.class).namespace(), rootNode.getQName().getNamespaceURI());
        assertEquals(Store_root.class.getAnnotation(Name.class).localName(), rootNode.getQName().getLocalName());
        assertEquals(workspaceRef.getProtocol(), rootNode.getReferenceableAspect().getStore_protocol());
        assertEquals(workspaceRef.getIdentifier(), rootNode.getReferenceableAspect().getStore_identifier());
        Store_root castRootNode = (Store_root)rootNode;
        assertEquals(Aspect_rootClass.class, castRootNode.getAspect_rootAspect().getClass());
        
        List<NodeRef> companyHome = serviceRegistry.getSearchService().selectNodes(rootNodeRef, DICTIONARY_PATH, null, serviceRegistry.getNamespaceService(), true);
        assertEquals(1, companyHome.size());
        Folder folder = factory.getNode(companyHome.get(0), Folder.class);
        assertEquals("Data Dictionary", folder.getName());
    }
    
    
    
    public void testContainsSameElement(){
        //Completely equal
        List<Integer> c1 = new ArrayList<>(Arrays.asList(new Integer[]{1,2,3,3,4,4,5,6,7,8,9}));
        List<Integer> c2 = new ArrayList<>(Arrays.asList(new Integer[]{1,2,3,3,4,4,5,6,7,8,9}));
        
        //Contains different elements
        List<Integer> c3 = new ArrayList<>(Arrays.asList(new Integer[]{1,2,3,3,3,4,5,6,7,8,9}));
        List<Integer> c4 = new ArrayList<>(Arrays.asList(new Integer[]{1,2,3,3,4,4,5,6,7,8,9}));
        
        //Contains same elements but in different order
        List<Integer> c5 = new ArrayList<>(Arrays.asList(new Integer[]{1,3,2,3,4,4,6,5,7,8,9}));
        List<Integer> c6 = new ArrayList<>(Arrays.asList(new Integer[]{1,2,3,3,4,4,5,6,7,8,9}));
        
        containsSameElements(c1, c2);
        containsSameElements(c5, c6);
        
        try{
            containsSameElements(c3, c4);
            fail("This call should have failed as the lists does not contain the same elements");
        }catch(AssertionFailedError e){
            
        }
        
    }
    
    public static <T> void containsSameElements(Collection<? extends T> c1, Collection<? extends T> c2){
        c1 = new ArrayList<>(c1);
        c2 = new ArrayList<>(c2);
        assertEquals(c1.size(), c2.size());
        //assertTrue(c1.containsAll(c2) && c2.containsAll(c1));
        while(c1.size() > 0){
            T object = c1.iterator().next();
            c1.remove(object);
            c2.remove(object);
            assertEquals(c1.size(), c2.size());
        }
        
    }
}