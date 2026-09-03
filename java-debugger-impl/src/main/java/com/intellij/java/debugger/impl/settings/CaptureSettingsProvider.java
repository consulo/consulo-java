// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.settings;

import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.JVMNameUtil;
import com.intellij.java.language.psi.PsiAnnotationMemberValue;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import consulo.application.util.registry.Registry;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Properties;

public final class CaptureSettingsProvider {
    private static final Logger LOG = Logger.getInstance(CaptureSettingsProvider.class);

    private static final KeyProvider THIS_KEY = new StringKeyProvider("this");
    private static final String ANY = "*";

    public static Properties getPointsProperties(@Nullable Project project) {
        Properties res = new Properties();
        if (Registry.is("debugger.capture.points.agent.annotations", true)) {
            int idx = 0;
            for (CaptureSettingsProvider.AgentPoint point : getAnnotationPoints(project)) {
                res.setProperty(
                    (point.isCapture() ? "capture" : "insert") + idx++,
                    point.myClassName + AgentPoint.SEPARATOR +
                        point.myMethodName + AgentPoint.SEPARATOR +
                        point.myMethodDesc + AgentPoint.SEPARATOR +
                        point.myKey.asString()
                );
            }
        }
        return res;
    }

    private static List<AgentPoint> getAnnotationPoints(@Nullable Project project) {
        return CaptureConfigurable.processCaptureAnnotations(project, (capture, e, annotation) -> {
            PsiMethod method;
            KeyProvider keyProvider;
            if (e instanceof PsiMethod) {
                method = (PsiMethod) e;
                keyProvider = THIS_KEY;
            }
            else if (e instanceof PsiParameter psiParameter) {
                method = (PsiMethod) psiParameter.getDeclarationScope();
                keyProvider = param(method.getParameterList().getParameterIndex(psiParameter));
            }
            else {
                return null;
            }
            String classVMName = JVMNameUtil.getClassVMName(method.getContainingClass());
            if (classVMName == null) {
                LOG.warn("Unable to find VM class name for annotated method: " + method.getName());
                return null;
            }
            String className = classVMName.replaceAll("\\.", "/");
            String methodName = JVMNameUtil.getJVMMethodName(method);
            String methodDesc = ANY;
            try {
                methodDesc = JVMNameUtil.getJVMSignature(method).getName(null);
            }
            catch (EvaluateException ex) {
                LOG.error(ex);
            }

            PsiAnnotationMemberValue keyExpressionValue = annotation.findAttributeValue("keyExpression");
            if (keyExpressionValue != null && !"\"\"".equals(keyExpressionValue.getText())) {
                keyProvider = new FieldKeyProvider(className, StringUtil.unquoteString(keyExpressionValue.getText())); //treat as a field
            }
            return capture ?
                new AgentCapturePoint(className, methodName, methodDesc, keyProvider) :
                new AgentInsertPoint(className, methodName, methodDesc, keyProvider);
        });
    }

    private abstract static class AgentPoint {
        public final String myClassName;
        public final String myMethodName;
        public final String myMethodDesc;
        public final KeyProvider myKey;

        private static final String SEPARATOR = " ";

        AgentPoint(String className, String methodName, String methodDesc, KeyProvider key) {
            assert !className.contains(".") : "Classname should not contain . here";
            myClassName = className;
            myMethodName = methodName;
            myMethodDesc = methodDesc;
            myKey = key;
        }

        public abstract boolean isCapture();

        @Override
        public String toString() {
            return myClassName + "." + myMethodName + " " + myKey.asString();
        }
    }

    private static class AgentCapturePoint extends AgentPoint {
        AgentCapturePoint(String className, String methodName, String methodDesc, KeyProvider key) {
            super(className, methodName, methodDesc, key);
        }

        @Override
        public boolean isCapture() {
            return true;
        }
    }

    private static class AgentInsertPoint extends AgentPoint {
        AgentInsertPoint(String className, String methodName, String methodDesc, KeyProvider key) {
            super(className, methodName, methodDesc, key);
        }

        @Override
        public boolean isCapture() {
            return false;
        }
    }

    private interface KeyProvider {
        String asString();
    }

    private static KeyProvider param(int idx) {
        return new StringKeyProvider(Integer.toString(idx));
    }

    private static class StringKeyProvider implements KeyProvider {
        private final String myValue;

        StringKeyProvider(String value) {
            myValue = value;
        }

        @Override
        public String asString() {
            return myValue;
        }
    }

    private static class FieldKeyProvider implements KeyProvider {
        private final String myClassName;
        private final String myFieldName;

        FieldKeyProvider(String className, String fieldName) {
            myClassName = className;
            myFieldName = fieldName;
        }

        @Override
        public String asString() {
            return myClassName + AgentPoint.SEPARATOR + myFieldName;
        }
    }
}
