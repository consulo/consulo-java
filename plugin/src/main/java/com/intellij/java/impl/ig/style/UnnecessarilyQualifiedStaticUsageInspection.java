/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.document.util.TextRange;
import consulo.language.editor.inspection.CleanupLocalInspectionTool;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import javax.swing.*;

public abstract class UnnecessarilyQualifiedStaticUsageInspection extends BaseInspection implements CleanupLocalInspectionTool {
    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreStaticFieldAccesses = false;

    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreStaticMethodCalls = false;

    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreStaticAccessFromStaticContext = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.unnecessarilyQualifiedStaticUsageDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        PsiJavaCodeReferenceElement element = (PsiJavaCodeReferenceElement) infos[0];
        PsiElement parent = element.getParent();
        return parent instanceof PsiMethodCallExpression
            ? InspectionGadgetsLocalize.unnecessarilyQualifiedStaticUsageProblemDescriptor(element.getText()).get()
            : InspectionGadgetsLocalize.unnecessarilyQualifiedStaticUsageProblemDescriptor1(element.getText()).get();
    }

    @Override
    public JComponent createOptionsPanel() {
        MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.unnecessarilyQualifiedStaticUsageIgnoreFieldOption().get(),
            "m_ignoreStaticFieldAccesses"
        );
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.unnecessarilyQualifiedStaticUsageIgnoreMethodOption().get(),
            "m_ignoreStaticMethodCalls"
        );
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.onlyReportQualifiedStaticUsagesOption().get(),
            "m_ignoreStaticAccessFromStaticContext"
        );
        return optionsPanel;
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessarilyQualifiedStaticUsageFix();
    }

    private static class UnnecessarilyQualifiedStaticUsageFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.unnecessaryQualifierForThisRemoveQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            element.delete();
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessarilyQualifiedStaticUsageVisitor();
    }

    private class UnnecessarilyQualifiedStaticUsageVisitor extends BaseInspectionVisitor {
        @Override
        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);
            PsiElement qualifier = reference.getQualifier();
            if (qualifier == null) {
                return;
            }
            if (!isUnnecessarilyQualifiedAccess(
                reference,
                m_ignoreStaticAccessFromStaticContext,
                m_ignoreStaticFieldAccesses,
                m_ignoreStaticMethodCalls
            )) {
                return;
            }
            registerError(qualifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL, reference);
        }

        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
            visitReferenceElement(expression);
        }
    }

    public static boolean isUnnecessarilyQualifiedAccess(
        @Nonnull PsiJavaCodeReferenceElement referenceElement,
        boolean ignoreStaticAccessFromStaticContext,
        boolean ignoreStaticFieldAccesses,
        boolean ignoreStaticMethodCalls
    ) {
        if (referenceElement instanceof PsiMethodReferenceExpression) {
            return false;
        }
        PsiElement parent = referenceElement.getParent();
        if (parent instanceof PsiImportStatementBase) {
            return false;
        }
        PsiElement qualifierElement = referenceElement.getQualifier();
        if (!(qualifierElement instanceof PsiJavaCodeReferenceElement)) {
            return false;
        }
        PsiJavaCodeReferenceElement qualifier = (PsiJavaCodeReferenceElement) qualifierElement;
        if (isGenericReference(referenceElement, qualifier)) {
            return false;
        }
        PsiElement target = referenceElement.resolve();
        if ((!(target instanceof PsiField) || ignoreStaticFieldAccesses) && (!(target instanceof PsiMethod) || ignoreStaticMethodCalls)) {
            return false;
        }
        if (ignoreStaticAccessFromStaticContext) {
            PsiMember containingMember = PsiTreeUtil.getParentOfType(referenceElement, PsiMember.class);
            if (containingMember != null && !containingMember.hasModifierProperty(PsiModifier.STATIC)) {
                return false;
            }
        }
        String referenceName = referenceElement.getReferenceName();
        if (referenceName == null) {
            return false;
        }
        PsiElement resolvedQualifier = qualifier.resolve();
        if (!(resolvedQualifier instanceof PsiClass)) {
            return false;
        }
        PsiClass containingClass = PsiTreeUtil.getParentOfType(referenceElement, PsiClass.class);
        PsiClass qualifyingClass = (PsiClass) resolvedQualifier;
        if (containingClass == null || !PsiTreeUtil.isAncestor(qualifyingClass, containingClass, false)) {
            return false;
        }
        Project project = referenceElement.getProject();
        JavaPsiFacade manager = JavaPsiFacade.getInstance(project);
        PsiResolveHelper resolveHelper = manager.getResolveHelper();
        PsiMember member = (PsiMember) target;
        PsiClass memberClass;
        if (target instanceof PsiField) {
            PsiVariable variable = resolveHelper.resolveReferencedVariable(referenceName, referenceElement);
            if (variable == null || !variable.equals(member)) {
                return false;
            }
            TextRange referenceElementTextRange = referenceElement.getTextRange();
            if (referenceElementTextRange == null) {
                return false;
            }
            TextRange variableTextRange = variable.getTextRange();
            if (variableTextRange == null) {
                return false;
            }
            //illegal forward ref
            if (referenceElementTextRange.getStartOffset() < variableTextRange.getEndOffset()) {
                return false;
            }
            PsiMember memberVariable = (PsiMember) variable;
            memberClass = memberVariable.getContainingClass();
        }
        else if (target instanceof PsiClass) {
            PsiClass aClass = resolveHelper.resolveReferencedClass(referenceName, referenceElement);
            if (aClass == null || !aClass.equals(member)) {
                return false;
            }
            memberClass = aClass.getContainingClass();
        }
        else {
            return isMethodAccessibleWithoutQualifier(referenceElement, qualifyingClass);
        }
        return resolvedQualifier.equals(memberClass);
    }

    private static boolean isMethodAccessibleWithoutQualifier(PsiJavaCodeReferenceElement referenceElement, PsiClass qualifyingClass) {
        String referenceName = referenceElement.getReferenceName();
        if (referenceName == null) {
            return false;
        }
        PsiClass containingClass = ClassUtils.getContainingClass(referenceElement);
        while (containingClass != null) {
            PsiMethod[] methods = containingClass.findMethodsByName(referenceName, true);
            for (PsiMethod method : methods) {
                String name = method.getName();
                if (referenceName.equals(name)) {
                    return containingClass.equals(qualifyingClass);
                }
            }
            containingClass = ClassUtils.getContainingClass(containingClass);
        }
        return false;
    }

    static boolean isGenericReference(PsiJavaCodeReferenceElement referenceElement, PsiJavaCodeReferenceElement qualifierElement) {
        PsiReferenceParameterList qualifierParameterList = qualifierElement.getParameterList();
        if (qualifierParameterList != null) {
            PsiTypeElement[] typeParameterElements = qualifierParameterList.getTypeParameterElements();
            if (typeParameterElements.length > 0) {
                return true;
            }
        }
        PsiReferenceParameterList parameterList = referenceElement.getParameterList();
        if (parameterList != null) {
            PsiTypeElement[] typeParameterElements = parameterList.getTypeParameterElements();
            if (typeParameterElements.length > 0) {
                return true;
            }
        }
        return false;
    }
}