/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.impl.ig.junit;

import com.intellij.java.impl.ig.ui.UiUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.CheckBox;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.awt.table.ListTable;
import consulo.ui.ex.awt.table.ListWrappingTableModel;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@ExtensionImpl
public class TestMethodWithoutAssertionInspection extends BaseInspection {

    /**
     * @noinspection PublicField
     */
    @NonNls
    public String assertionMethods =
        "org.junit.Assert,assert.*|fail.*," +
            "junit.framework.Assert,assert.*|fail.*," +
            "org.mockito.Mockito,verify.*," +
            "org.junit.rules.ExpectedException,expect.*";

    private final List<String> methodNamePatterns = new ArrayList();
    private final List<String> classNames = new ArrayList();
    private Map<String, Pattern> patternCache = null;

    @SuppressWarnings({"PublicField"})
    public boolean assertKeywordIsAssertion = false;

    public TestMethodWithoutAssertionInspection() {
        parseString(assertionMethods, classNames, methodNamePatterns);
    }

    @Override
    @Nonnull
    public String getID() {
        return "JUnitTestMethodWithNoAssertions";
    }

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.testMethodWithoutAssertionDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.testMethodWithoutAssertionProblemDescriptor().get();
    }

    @Override
    public JComponent createOptionsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        ListTable table = new ListTable(new ListWrappingTableModel(
            Arrays.asList(classNames, methodNamePatterns),
            InspectionGadgetsLocalize.className().get(),
            InspectionGadgetsLocalize.methodNamePattern().get()
        ));
        JPanel tablePanel = UiUtils.createAddRemovePanel(table);
        CheckBox checkBox =
            new CheckBox(InspectionGadgetsLocalize.assertKeywordIsConsideredAnAssertion().get(), this, "assertKeywordIsAssertion");
        panel.add(tablePanel, BorderLayout.CENTER);
        panel.add(checkBox, BorderLayout.SOUTH);
        return panel;
    }

    @Override
    public void readSettings(@Nonnull Element element) throws InvalidDataException {
        super.readSettings(element);
        parseString(assertionMethods, classNames, methodNamePatterns);
    }

    @Override
    public void writeSettings(@Nonnull Element element) throws WriteExternalException {
        assertionMethods = formatString(classNames, methodNamePatterns);
        super.writeSettings(element);
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new TestMethodWithoutAssertionVisitor();
    }

    private class TestMethodWithoutAssertionVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            super.visitMethod(method);
            if (!TestUtils.isJUnitTestMethod(method)) {
                return;
            }
            if (hasExpectedExceptionAnnotation(method)) {
                return;
            }
            if (containsAssertion(method)) {
                return;
            }
            if (lastStatementIsCallToMethodWithAssertion(method)) {
                return;
            }
            registerMethodError(method);
        }

        private boolean lastStatementIsCallToMethodWithAssertion(PsiMethod method) {
            PsiCodeBlock body = method.getBody();
            if (body == null) {
                return false;
            }
            PsiStatement[] statements = body.getStatements();
            if (statements.length <= 0) {
                return false;
            }
            PsiStatement lastStatement = statements[0];
            if (!(lastStatement instanceof PsiExpressionStatement)) {
                return false;
            }
            PsiExpressionStatement expressionStatement = (PsiExpressionStatement) lastStatement;
            PsiExpression expression = expressionStatement.getExpression();
            if (!(expression instanceof PsiMethodCallExpression)) {
                return false;
            }
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) expression;
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
            if (qualifierExpression != null && !(qualifierExpression instanceof PsiThisExpression)) {
                return false;
            }
            PsiMethod targetMethod = methodCallExpression.resolveMethod();
            return containsAssertion(targetMethod);
        }

        private boolean containsAssertion(PsiElement element) {
            if (element == null) {
                return false;
            }
            ContainsAssertionVisitor visitor = new ContainsAssertionVisitor();
            element.accept(visitor);
            return visitor.containsAssertion();
        }

        private boolean hasExpectedExceptionAnnotation(PsiMethod method) {
            PsiModifierList modifierList = method.getModifierList();
            PsiAnnotation testAnnotation = modifierList.findAnnotation("org.junit.Test");
            if (testAnnotation == null) {
                return false;
            }
            PsiAnnotationParameterList parameterList = testAnnotation.getParameterList();
            PsiNameValuePair[] nameValuePairs = parameterList.getAttributes();
            for (PsiNameValuePair nameValuePair : nameValuePairs) {
                @NonNls String parameterName = nameValuePair.getName();
                if ("expected".equals(parameterName)) {
                    return true;
                }
            }
            return false;
        }
    }

    private class ContainsAssertionVisitor extends JavaRecursiveElementVisitor {

        private boolean containsAssertion = false;

        @Override
        public void visitElement(@Nonnull PsiElement element) {
            if (!containsAssertion) {
                super.visitElement(element);
            }
        }

        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression call) {
            if (containsAssertion) {
                return;
            }
            super.visitMethodCallExpression(call);
            PsiReferenceExpression methodExpression = call.getMethodExpression();
            @NonNls String methodName = methodExpression.getReferenceName();
            if (methodName == null) {
                return;
            }
            int methodNamesSize = methodNamePatterns.size();
            for (int i = 0; i < methodNamesSize; i++) {
                String pattern = methodNamePatterns.get(i);
                if (!methodNamesMatch(methodName, pattern)) {
                    continue;
                }
                PsiMethod method = call.resolveMethod();
                if (method == null || method.isConstructor()) {
                    continue;
                }
                PsiClass aClass = method.getContainingClass();
                if (!InheritanceUtil.isInheritor(aClass, classNames.get(i))) {
                    continue;
                }
                containsAssertion = true;
                break;
            }
        }

        @Override
        public void visitAssertStatement(PsiAssertStatement statement) {
            if (containsAssertion) {
                return;
            }
            super.visitAssertStatement(statement);
            if (!assertKeywordIsAssertion) {
                return;
            }
            containsAssertion = true;
        }

        public boolean containsAssertion() {
            return containsAssertion;
        }
    }

    private boolean methodNamesMatch(String methodName, String methodNamePattern) {
        Pattern pattern;
        if (patternCache != null) {
            pattern = patternCache.get(methodNamePattern);
        }
        else {
            patternCache = new HashMap(methodNamePatterns.size());
            pattern = null;
        }
        if (pattern == null) {
            try {
                pattern = Pattern.compile(methodNamePattern);
                patternCache.put(methodNamePattern, pattern);
            }
            catch (PatternSyntaxException ignore) {
                return false;
            }
            catch (NullPointerException ignore) {
                return false;
            }
        }
        if (pattern == null) {
            return false;
        }
        Matcher matcher = pattern.matcher(methodName);
        return matcher.matches();
    }
}