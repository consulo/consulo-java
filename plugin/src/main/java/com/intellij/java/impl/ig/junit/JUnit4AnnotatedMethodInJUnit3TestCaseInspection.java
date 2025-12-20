/*
 * Copyright 2008-2012 Bas Leijdekkers
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

import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class JUnit4AnnotatedMethodInJUnit3TestCaseInspection extends BaseInspection {
    private static final String IGNORE = "org.junit.Ignore";

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.junit4TestMethodInClassExtendingJunit3TestcaseDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        if (AnnotationUtil.isAnnotated((PsiMethod) infos[1], IGNORE, false)) {
            return InspectionGadgetsLocalize.ignoreTestMethodInClassExtendingJunit3TestcaseProblemDescriptor().get();
        }
        return InspectionGadgetsLocalize.junit4TestMethodInClassExtendingJunit3TestcaseProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    protected InspectionGadgetsFix[] buildFixes(Object... infos) {
        List<InspectionGadgetsFix> fixes = new ArrayList(3);
        PsiMethod method = (PsiMethod) infos[1];
        if (AnnotationUtil.isAnnotated(method, IGNORE, false)) {
            fixes.add(new RemoveIgnoreAndRename(method));
        }
        if (TestUtils.isJUnit4TestMethod(method)) {
            fixes.add(new RemoveTestAnnotationFix());
        }
        PsiClass aClass = (PsiClass) infos[0];
        String className = aClass.getName();
        fixes.add(new ConvertToJUnit4Fix(className));
        return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
    }

    private static void deleteAnnotation(ProblemDescriptor descriptor, String qualifiedName) {
        PsiElement element = descriptor.getPsiElement();
        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiModifierListOwner)) {
            return;
        }
        PsiModifierListOwner method = (PsiModifierListOwner) parent;
        PsiModifierList modifierList = method.getModifierList();
        if (modifierList == null) {
            return;
        }
        PsiAnnotation annotation = modifierList.findAnnotation(qualifiedName);
        if (annotation == null) {
            return;
        }
        annotation.delete();
    }

    private static class RemoveIgnoreAndRename extends RenameFix {

        public RemoveIgnoreAndRename(@NonNls PsiMethod method) {
            super("_" + method.getName());
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.ignoreTestMethodInClassExtendingJunit3TestcaseQuickfix(getTargetName());
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) {
            deleteAnnotation(descriptor, IGNORE);
            super.doFix(project, descriptor);
        }
    }

    private static class ConvertToJUnit4Fix extends InspectionGadgetsFix {
        private final String className;

        ConvertToJUnit4Fix(String className) {
            this.className = className;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.convertJunit3TestClassQuickfix(className);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiElement parent = element.getParent();
            if (!(parent instanceof PsiMember)) {
                return;
            }
            PsiMember member = (PsiMember) parent;
            PsiClass containingClass = member.getContainingClass();
            if (containingClass == null) {
                return;
            }
            PsiReferenceList extendsList = containingClass.getExtendsList();
            if (extendsList == null) {
                return;
            }
            PsiMethod[] methods = containingClass.getMethods();
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

    private static class RemoveTestAnnotationFix extends InspectionGadgetsFix {

        @Override
        @Nonnull
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.removeJunit4TestAnnotationQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) {
            deleteAnnotation(descriptor, "org.junit.Test");
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new Junit4AnnotatedMethodInJunit3TestCaseVisitor();
    }

    private static class Junit4AnnotatedMethodInJunit3TestCaseVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!TestUtils.isJUnitTestClass(containingClass)) {
                return;
            }
            if (AnnotationUtil.isAnnotated(method, IGNORE, false) && method.getName().startsWith("test")) {
                registerMethodError(method, containingClass, method);
            }
            else if (TestUtils.isJUnit4TestMethod(method)) {
                registerMethodError(method, containingClass, method);
            }
        }
    }
}
