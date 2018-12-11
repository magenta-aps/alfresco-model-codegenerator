/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco.generated;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import junit.framework.AssertionFailedError;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.web.scripts.BaseWebScriptTest;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.StoreRef;

/**
 *
 * @author martin
 */
public class Tests extends BaseWebScriptTest {

    private final ServiceRegistry serviceRegistry = (ServiceRegistry) getServer().getApplicationContext().getBean("ServiceRegistry");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        AuthenticationUtil.setAdminUserAsFullyAuthenticatedUser();
//        TestUtils.wipeData(serviceRegistry);
//        TestUtils.setupSimpleFlow(serviceRegistry);
    }

    @Override
    protected void tearDown() throws Exception {
//        TestUtils.wipeData(serviceRegistry);
    }
    
    public void testWorkspaces() throws Exception{
        
        List<StoreRef> generatedRefsNodeBase = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork< List<StoreRef>>()
         {
            public  List<StoreRef> doWork() throws Exception
            {
               return NodeBase.getStores();
               
            }
         }, AuthenticationUtil.getSystemUserName());
        
        List<StoreRef> baseRefs = serviceRegistry.getNodeService().getStores();
        containsSameElements(baseRefs, generatedRefsNodeBase);

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