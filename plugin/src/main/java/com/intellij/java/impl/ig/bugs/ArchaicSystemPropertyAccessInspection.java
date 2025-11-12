/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ArchaicSystemPropertyAccessInspection extends BaseInspection {
    @Nonnull
    @Override
    public String getID() {
        return "UseOfArchaicSystemPropertyAccessors";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.archaicSystemPropertyAccessorsDisplayName();
    }

    @Nonnull
    @Override
    public String buildErrorString(Object... infos) {
        PsiMethodCallExpression call = (PsiMethodCallExpression) infos[0];
        if (isIntegerGetInteger(call)) {
            return InspectionGadgetsLocalize.archaicSystemPropertyAccessorsProblemDescriptorInteger().get();
        }
        else if (isLongGetLong(call)) {
            return InspectionGadgetsLocalize.archaicSystemPropertyAccessorsProblemDescriptorLong().get();
        }
        else {
            return InspectionGadgetsLocalize.archaicSystemPropertyAccessorsProblemDescriptorBoolean().get();
        }
    }

    @Override
    @Nonnull
    protected InspectionGadgetsFix[] buildFixes(Object... infos) {
        return new InspectionGadgetsFix[]{new ReplaceWithParseMethodFix(),
            new ReplaceWithStandardPropertyAccessFix()};
    }

    private static class ReplaceWithParseMethodFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.archaicSystemPropertyAccessorsReplaceParseQuickfix();
        }

        @Override
        @RequiredWriteAction
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiIdentifier location = (PsiIdentifier) descriptor.getPsiElement();
            PsiElement parent = location.getParent();
            assert parent != null;
            PsiMethodCallExpression call = (PsiMethodCallExpression) parent.getParent();
            assert call != null;
            PsiExpressionList argList = call.getArgumentList();
            PsiExpression[] args = argList.getExpressions();
            String argText = args[0].getText();
            String parseMethodCall;
            if (isIntegerGetInteger(call)) {
                parseMethodCall = "Integer.valueOf(" + argText + ')';
            }
            else if (isLongGetLong(call)) {
                parseMethodCall = "Long.valueOf(" + argText + ')';
            }
            else {
                parseMethodCall = "Boolean.valueOf(" + argText + ')';
            }
            replaceExpression(call, parseMethodCall);
        }
    }

    private static class ReplaceWithStandardPropertyAccessFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.archaicSystemPropertyAccessorsReplaceStandardQuickfix();
        }

        @Override
        @RequiredWriteAction
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiIdentifier location = (PsiIdentifier) descriptor.getPsiElement();
            PsiElement parent = location.getParent();
            assert parent != null;
            PsiMethodCallExpression call = (PsiMethodCallExpression) parent.getParent();
            assert call != null;
            PsiExpressionList argList = call.getArgumentList();
            PsiExpression[] args = argList.getExpressions();
            String argText = args[0].getText();
            String parseMethodCall;
            if (isIntegerGetInteger(call)) {
                parseMethodCall = "Integer.parseInt(System.getProperty(" + argText + "))";
            }
            else if (isLongGetLong(call)) {
                parseMethodCall = "Long.parseLong(System.getProperty(" + argText + "))";
            }
            else if (!PsiUtil.isLanguageLevel5OrHigher(call)) {
                parseMethodCall = "Boolean.valueOf(System.getProperty(" + argText + ")).booleanValue()";
            }
            else {
                parseMethodCall = "Boolean.parseBoolean(System.getProperty(" + argText + "))";
            }
            replaceExpression(call, parseMethodCall);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ArchaicSystemPropertyAccessVisitor();
    }

    private static class ArchaicSystemPropertyAccessVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(
            @Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (isIntegerGetInteger(expression) ||
                isLongGetLong(expression) ||
                isBooleanGetBoolean(expression)) {
                registerMethodCallError(expression, expression);
            }
        }
    }

    static boolean isIntegerGetInteger(PsiMethodCallExpression expression) {
        return isCallTo(expression, CommonClassNames.JAVA_LANG_INTEGER, "getInteger");
    }

    static boolean isLongGetLong(PsiMethodCallExpression expression) {
        return isCallTo(expression, CommonClassNames.JAVA_LANG_LONG, "getLong");
    }

    static boolean isBooleanGetBoolean(PsiMethodCallExpression expression) {
        return isCallTo(expression, CommonClassNames.JAVA_LANG_BOOLEAN, "getBoolean");
    }

    private static boolean isCallTo(PsiMethodCallExpression expression, String className, String methodName) {
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        String expressionMethodName = methodExpression.getReferenceName();
        if (!methodName.equals(expressionMethodName)) {
            return false;
        }
        PsiMethod method = expression.resolveMethod();
        if (method == null) {
            return false;
        }
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
            return false;
        }
        String expressionClassName = aClass.getQualifiedName();
        return expressionClassName != null && className.equals(expressionClassName);
    }
}