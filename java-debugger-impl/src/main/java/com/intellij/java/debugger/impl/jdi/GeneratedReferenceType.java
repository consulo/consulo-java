// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.jdi;

import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import consulo.internal.com.sun.jdi.AbsentInformationException;
import consulo.internal.com.sun.jdi.ClassLoaderReference;
import consulo.internal.com.sun.jdi.ClassObjectReference;
import consulo.internal.com.sun.jdi.Field;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.Method;
import consulo.internal.com.sun.jdi.ModuleReference;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.Value;
import consulo.internal.com.sun.jdi.VirtualMachine;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GeneratedReferenceType implements ReferenceType {
    private final VirtualMachine myVm;
    private final String myName;

    public GeneratedReferenceType(VirtualMachine vm, String name) {
        myVm = vm;
        myName = name;
    }

    @Override
    public String name() {
        return myName;
    }

    @Override
    public @Nullable String genericSignature() {
        return null;
    }

    @Override
    public @Nullable ClassLoaderReference classLoader() {
        return null;
    }

    @Override
    public @Nullable ModuleReference module() {
        return null;
    }

    @Override
    public String sourceName() throws AbsentInformationException {
        throw new AbsentInformationException();
    }

    @Override
    public List<String> sourceNames(String stratum) throws AbsentInformationException {
        throw new AbsentInformationException();
    }

    @Override
    public List<String> sourcePaths(String stratum) throws AbsentInformationException {
        throw new AbsentInformationException();
    }

    @Override
    public String sourceDebugExtension() throws AbsentInformationException {
        throw new AbsentInformationException();
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public boolean isAbstract() {
        return false;
    }

    @Override
    public boolean isFinal() {
        return false;
    }

    @Override
    public boolean isPrepared() {
        return false;
    }

    @Override
    public boolean isVerified() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return false;
    }

    @Override
    public boolean failedToInitialize() {
        return false;
    }

    @Override
    public List<Field> fields() {
        return Collections.emptyList();
    }

    @Override
    public List<Field> visibleFields() {
        return Collections.emptyList();
    }

    @Override
    public List<Field> allFields() {
        return Collections.emptyList();
    }

    @Override
    public @Nullable Field fieldByName(String fieldName) {
        return null;
    }

    @Override
    public List<Method> methods() {
        return Collections.emptyList();
    }

    @Override
    public List<Method> visibleMethods() {
        return Collections.emptyList();
    }

    @Override
    public List<Method> allMethods() {
        return Collections.emptyList();
    }

    @Override
    public List<Method> methodsByName(String name) {
        return Collections.emptyList();
    }

    @Override
    public List<Method> methodsByName(String name, String signature) {
        return Collections.emptyList();
    }

    @Override
    public List<ReferenceType> nestedTypes() {
        return Collections.emptyList();
    }

    @Override
    public @Nullable Value getValue(Field field) {
        return null;
    }

    @Override
    public Map<Field, Value> getValues(List<? extends Field> fields) {
        return Collections.emptyMap();
    }

    @Override
    public @Nullable ClassObjectReference classObject() {
        return null;
    }

    @Override
    public List<Location> allLineLocations() throws AbsentInformationException {
        return Collections.emptyList();
    }

    @Override
    public List<Location> allLineLocations(String stratum, String sourceName) throws AbsentInformationException {
        return Collections.emptyList();
    }

    @Override
    public List<Location> locationsOfLine(int lineNumber) throws AbsentInformationException {
        return Collections.emptyList();
    }

    @Override
    public List<Location> locationsOfLine(String stratum, String sourceName, int lineNumber) throws AbsentInformationException {
        return Collections.emptyList();
    }

    @Override
    public List<String> availableStrata() {
        return List.of(defaultStratum());
    }

    @Override
    public String defaultStratum() {
        return DebugProcess.JAVA_STRATUM;
    }

    @Override
    public List<ObjectReference> instances(long maxInstances) {
        return Collections.emptyList();
    }

    @Override
    public int majorVersion() {
        return 0;
    }

    @Override
    public int minorVersion() {
        return 0;
    }

    @Override
    public int constantPoolCount() {
        return 0;
    }

    @Override
    public byte[] constantPool() {
        return new byte[0];
    }

    @Override
    public int modifiers() {
        return 0;
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @Override
    public boolean isPackagePrivate() {
        return false;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return false;
    }

    @Override
    public String signature() {
        return DebuggerUtilsEx.typeNameToSignature(myName);
    }

    @Override
    public VirtualMachine virtualMachine() {
        return myVm;
    }

    @Override
    public int compareTo(ReferenceType o) {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        GeneratedReferenceType type = (GeneratedReferenceType) other;
        return myName.equals(type.myName) && myVm.equals(type.myVm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myName);
    }
}
