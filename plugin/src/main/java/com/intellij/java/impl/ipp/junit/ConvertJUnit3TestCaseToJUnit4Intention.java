/*
 * Copyright 2009-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.junit;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConvertJUnit3TestCaseToJUnit4Intention", fileExtensions = "java", categories = {"Java", "JUnit"})
public class ConvertJUnit3TestCaseToJUnit4Intention extends Intention {

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.convertJUnit3TestCaseToJUnit4IntentionName();
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ConvertJUnit3TestCaseToJUnit4Predicate();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiClass)) {
            return;
        }
        PsiClass aClass = (PsiClass) parent;
        PsiReferenceList extendsList = aClass.getExtendsList();
        if (extendsList == null) {
            return;
        }
        PsiMethod[] methods = aClass.getMethods();
        for (PsiMethod method : methods) {
            @NonNls String name = method.getName();
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            PsiType returnType = method.getReturnType();
            if (!PsiType.VOID.equals(returnType)) {
                continue;
            }
            PsiModifierList modifierList = method.getModifierList();
            if (name.startsWith("test")) {
                addAnnotationIfNotPresent(modifierList, "org.junit.Test");
            }
            else if (name.equals("setUp")) {
                transformSetUpOrTearDownMethod(method);
                addAnnotationIfNotPresent(modifierList, "org.junit.Before");
            }
            else if (name.equals("tearDown")) {
                transformSetUpOrTearDownMethod(method);
                addAnnotationIfNotPresent(modifierList, "org.junit.After");
            }
            method.accept(new MethodCallModifier());
        }
        PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
        for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
            referenceElement.delete();
        }
    }

    private static void addAnnotationIfNotPresent(PsiModifierList modifierList, String qualifiedAnnotationName) {
        if (modifierList.findAnnotation(qualifiedAnnotationName) != null) {
            return;
        }
        PsiAnnotation annotation = modifierList.addAnnotation(qualifiedAnnotationName);
        Project project = modifierList.getProject();
        JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
        codeStyleManager.shortenClassReferences(annotation);
    }

    private static void transformSetUpOrTearDownMethod(PsiMethod method) {
        PsiModifierList modifierList = method.getModifierList();
        if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
            modifierList.setModifierProperty(PsiModifier.PROTECTED, false);
        }
        if (!modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
            modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
        }
        PsiAnnotation overrideAnnotation = modifierList.findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE);
        if (overrideAnnotation != null) {
            overrideAnnotation.delete();
        }
        method.accept(new SuperLifeCycleCallRemover(method.getName()));
    }

    private static class SuperLifeCycleCallRemover extends JavaRecursiveElementVisitor {

        @Nonnull
        private final String myLifeCycleMethodName;

        private SuperLifeCycleCallRemover(@Nonnull String lifeCycleMethodName) {
            myLifeCycleMethodName = lifeCycleMethodName;
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!myLifeCycleMethodName.equals(methodName)) {
                return;
            }
            PsiExpression target = methodExpression.getQualifierExpression();
            if (!(target instanceof PsiSuperExpression)) {
                return;
            }
            expression.delete();
        }
    }

    private static class MethodCallModifier extends JavaRecursiveElementVisitor {

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression.getQualifierExpression() != null) {
                return;
            }
            PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            String name = aClass.getQualifiedName();
            if (!"junit.framework.Assert".equals(name)) {
                return;
            }
            @NonNls String newExpressionText = "org.junit.Assert." + expression.getText();
            Project project = expression.getProject();
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiExpression newExpression = factory.createExpressionFromText(newExpressionText, expression);
            JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
            PsiElement replacedExpression = expression.replace(newExpression);
            codeStyleManager.shortenClassReferences(replacedExpression);
        }
    }
}
