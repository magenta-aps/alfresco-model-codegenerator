/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco;

import com.squareup.javapoet.ClassName;
import edu.emory.mathcs.backport.java.util.Arrays;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.maven.plugin.logging.Log;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.xml.sax.SAXException;

/**
 *
 * @author martin
 */
public class DOMModuleParserTest {
    DOMModuleParser parser;
    
    public DOMModuleParserTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
        Log log = EasyMock.createMock(Log.class);
        File file = new File("src/test/java/");
        parser = new DOMModuleParser(log, Arrays.asList(new String[]{"test.magenta.generated"}), file.getAbsolutePath(), ClassName.get("test.magenta.generated", "BaseClass"));
    }
    
    @After
    public void tearDown() {
    }

    @Test
    public void testMe() throws IOException, ParserConfigurationException, SAXException {
        try(InputStream system = getClass().getResourceAsStream("/systemModel.xml")){
            parser.execute(system);
        }
        try(InputStream system = getClass().getResourceAsStream("/contentModel.xml")){
            parser.execute(system);
        }
        
        for(String fqcn : parser.getParsedTypes().keySet()){
            System.out.println(parser.getParsedTypes().get(fqcn));
        }
        
    }
    
}
