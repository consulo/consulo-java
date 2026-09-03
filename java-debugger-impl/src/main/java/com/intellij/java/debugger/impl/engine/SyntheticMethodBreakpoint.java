// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.debugger.impl.breakpoints.properties.JavaMethodBreakpointProperties;
import com.intellij.java.debugger.impl.ui.breakpoints.SyntheticBreakpoint;
import com.intellij.java.debugger.impl.ui.breakpoints.WildcardMethodBreakpoint;
import consulo.internal.com.sun.jdi.Method;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SyntheticMethodBreakpoint extends WildcardMethodBreakpoint implements SyntheticBreakpoint {
    private final JavaMethodBreakpointProperties myProperties = new JavaMethodBreakpointProperties();
    private final @Nullable String mySignature;
    private @Nullable String mySuspendPolicy;

    public SyntheticMethodBreakpoint(String className, String methodName, @Nullable String signature, Project project) {
        super(project, null);
        myProperties.EMULATED = true;
        myProperties.WATCH_EXIT = false;
        myProperties.myClassPattern = className;
        myProperties.myMethodName = methodName;
        mySignature = signature;
    }

    @Override
    public List<Method> matchingMethods(List<Method> methods, DebugProcessImpl debugProcess) {
        String methodName = getMethodName();
        return methods.stream()
            .filter(m -> Objects.equals(methodName, m.name()) && (mySignature == null || Objects.equals(mySignature, m.signature())))
            .limit(1)
            .collect(Collectors.toList());
    }

    @Override
    protected JavaMethodBreakpointProperties getProperties() {
        return myProperties;
    }

    @Override
    public boolean isCountFilterEnabled() {
        return false;
    }

    @Override
    public boolean isClassFiltersEnabled() {
        return false;
    }

    @Override
    public boolean isConditionEnabled() {
        return false;
    }

    @Override
    public @Nullable String getSuspendPolicy() {
        return mySuspendPolicy;
    }

    @Override
    public void setSuspendPolicy(String policy) {
        mySuspendPolicy = policy;
    }

    @Override
    protected void fireBreakpointChanged() {
    }

    @Override
    protected boolean isLogEnabled() {
        return false;
    }

    @Override
    protected boolean isLogExpressionEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void setEnabled(boolean enabled) {
    }
}
