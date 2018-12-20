/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco;

import com.squareup.javapoet.TypeSpec;

/**
 *
 * @author martin
 */
class TypeContainer {
    
    TypeSpec.Builder interfaceBuilder;
    TypeSpec.Builder classBuilder;
    private TypeSpec.Builder setterBuilder;
    private TypeSpec.Builder getterBuilder;

    public TypeContainer() {
    }

    public TypeContainer(TypeSpec.Builder interfaceBuilder, TypeSpec.Builder classBuilder, TypeSpec.Builder setterBuilder, TypeSpec.Builder getterBuilder) {
        this.interfaceBuilder = interfaceBuilder;
        this.classBuilder = classBuilder;
        this.setterBuilder = setterBuilder;
        this.getterBuilder = getterBuilder;
    }

    public TypeSpec.Builder getInterfaceBuilder() {
        return interfaceBuilder;
    }

    public void setInterfaceBuilder(TypeSpec.Builder interfaceBuilder) {
        this.interfaceBuilder = interfaceBuilder;
    }

    public TypeSpec.Builder getClassBuilder() {
        return classBuilder;
    }

    public void setClassBuilder(TypeSpec.Builder classBuilder) {
        this.classBuilder = classBuilder;
    }

    public TypeSpec.Builder getSetterBuilder() {
        return setterBuilder;
    }

    public void setSetterBuilder(TypeSpec.Builder setterBuilder) {
        this.setterBuilder = setterBuilder;
    }

    public TypeSpec.Builder getGetterBuilder() {
        return getterBuilder;
    }

    public void setGetterBuilder(TypeSpec.Builder getterBuilder) {
        this.getterBuilder = getterBuilder;
    }

    @Override
    public String toString() {
        return "TypeContainer{" + "interfaceBuilder=" + interfaceBuilder + ", classBuilder=" + classBuilder + ", setterBuilder=" + setterBuilder + ", getterBuilder=" + getterBuilder + '}';
    }
    
}
