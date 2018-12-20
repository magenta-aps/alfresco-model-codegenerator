/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.magenta.alfresco;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author martin
 */
class MethodContainer {
    
    private List<MethodSpec.Builder> interfaceMethods = new ArrayList<>();
    private List<MethodSpec.Builder> classMethods = new ArrayList<>();
    private List<FieldSpec.Builder> interfaceFields = new ArrayList<>();
    private List<FieldSpec.Builder> classFields = new ArrayList<>();

    public MethodContainer() {
    }

    public void addInterfaceMethod(MethodSpec.Builder method) {
        interfaceMethods.add(method);
    }

    public void addClassMethod(MethodSpec.Builder method) {
        classMethods.add(method);
    }

    public void addInterfaceField(FieldSpec.Builder method) {
        interfaceFields.add(method);
    }

    public void addClassField(FieldSpec.Builder method) {
        classFields.add(method);
    }

    public List<MethodSpec.Builder> getInterfaceMethods() {
        return interfaceMethods;
    }

    public List<MethodSpec.Builder> getClassMethods() {
        return classMethods;
    }

    public List<FieldSpec.Builder> getInterfaceFields() {
        return interfaceFields;
    }

    public List<FieldSpec.Builder> getClassFields() {
        return classFields;
    }
    
}
