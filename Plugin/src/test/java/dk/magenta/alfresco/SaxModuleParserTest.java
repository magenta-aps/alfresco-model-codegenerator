/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import junit.framework.TestCase;
import org.xml.sax.Attributes;
import org.xml.sax.SAXParseException;

/**
 *
 * @author martin
 */
public class SaxModuleParserTest extends TestCase {
    
    public SaxModuleParserTest(String testName) {
        super(testName);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

//    public void testStartDocument() throws Exception {
//        fail("The test case is a prototype.");
//    }
//
//    public void testEndDocument() throws Exception {
//        fail("The test case is a prototype.");
//    }
//
//    public void testStartElement() throws Exception {
//        fail("The test case is a prototype.");
//    }
//
//    public void testEndElement() throws Exception {
//        fail("The test case is a prototype.");
//    }
//
//    public void testStartPrefixMapping() throws Exception {
//        fail("The test case is a prototype.");
//    }
//
//    public void testEndPrefixMapping() throws Exception {
//        fail("The test case is a prototype.");
//    }
//
//    public void testCharacters() throws Exception {
//        fail("The test case is a prototype.");
//    }
//
//    public void testFatalError() throws Exception {
//        fail("The test case is a prototype.");
//    }
//
//    public void testError() throws Exception {
//        fail("The test case is a prototype.");
//    }
//
//    public void testWarning() throws Exception {
//        fail("The test case is a prototype.");
//    }
//
//    public void testUnparsedEntityDecl() throws Exception {
//        fail("The test case is a prototype.");
//    }

    public void testGetPackages() {
        SaxModuleParser parser = new SaxModuleParser(null, Collections.EMPTY_LIST, null, null);
        List<String> result = parser.getPackages("http://www.test.dk/min/pakke");
        assertEquals(Arrays.asList(new String[]{"dk", "test", "min", "pakke"}), result);
        
    }
    
}
