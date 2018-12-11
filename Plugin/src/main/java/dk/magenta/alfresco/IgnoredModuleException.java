
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco;

import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;

/**
 *
 * @author martin
 */
public class IgnoredModuleException extends SAXParseException{

    public IgnoredModuleException(String message, Locator locator) {
        super(message, locator);
    }

    public IgnoredModuleException(String message, Locator locator, Exception e) {
        super(message, locator, e);
    }

    public IgnoredModuleException(String message, String publicId, String systemId, int lineNumber, int columnNumber) {
        super(message, publicId, systemId, lineNumber, columnNumber);
    }

    public IgnoredModuleException(String message, String publicId, String systemId, int lineNumber, int columnNumber, Exception e) {
        super(message, publicId, systemId, lineNumber, columnNumber, e);
    }
    
    
    
}
