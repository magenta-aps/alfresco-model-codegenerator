/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco;

import java.util.Objects;

/**
 *
 * @author martin
 */
public class TypePostfix {
    private String namespace;
    private String localName;
    private String prefix;

    public TypePostfix() {
    }

    public TypePostfix(String namespace, String localName, String prefix) {
        this.namespace = namespace;
        this.localName = localName;
        this.prefix = prefix;
    }

    
    public String getNameSpace() {
        return namespace;
    }

    public void setNameSpace(String nameSpace) {
        this.namespace = nameSpace;
    }

    public String getLocalName() {
        return localName;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    
    

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Objects.hashCode(this.namespace);
        hash = 53 * hash + Objects.hashCode(this.localName);
        hash = 53 * hash + Objects.hashCode(this.prefix);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TypePostfix other = (TypePostfix) obj;
        if (!Objects.equals(this.namespace, other.namespace)) {
            return false;
        }
        if (!Objects.equals(this.localName, other.localName)) {
            return false;
        }
        if (!Objects.equals(this.prefix, other.prefix)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "TypePostfix{" + "namespace=" + namespace + ", localName=" + localName + ", prefix=" + prefix + '}';
    }
    
    
    
    
    
}
