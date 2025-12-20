/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.j2me;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class PrivateMemberAccessBetweenOuterAndInnerClassInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.privateMemberAccessBetweenOuterAndInnerClassesDisplayName();
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected String buildErrorString(Object... infos) {
        PsiClass aClass = (PsiClass) infos[0];
        return InspectionGadgetsLocalize.privateMemberAccessBetweenOuterAndInnerClassesProblemDescriptor(aClass.getName()).get();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        PsiClass aClass = (PsiClass) infos[0];
        String className = aClass.getName();
        if (infos.length == 1) {
            return new MakePackagePrivateFix(className, true);
        }
        PsiMember member = (PsiMember) infos[1];
        @NonNls String memberName;
        if (member instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) member;
            if (method.isConstructor()) {
            }

            memberName = member.getName() + "()";
        }
        else {
            memberName = member.getName();
        }
        @NonNls String elementName = className + '.' + memberName;
        return new MakePackagePrivateFix(elementName, false);
    }

    private static class MakePackagePrivateFix extends InspectionGadgetsFix {

        private final String elementName;
        private final boolean constructor;

        private MakePackagePrivateFix(String elementName, boolean constructor) {
            this.elementName = elementName;
            this.constructor = constructor;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return constructor
                ? InspectionGadgetsLocalize.privateMemberAccessBetweenOuterAndInnerClassesMakeConstructorPackageLocalQuickfix(elementName)
                : InspectionGadgetsLocalize.privateMemberAccessBetweenOuterAndInnerClassesMakeLocalQuickfix(elementName);
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            if (constructor) {
                makeConstructorPackageLocal(project, element);
            }
            else {
                makeMemberPackageLocal(element);
            }
        }

        private static void makeMemberPackageLocal(PsiElement element) {
            PsiElement parent = element.getParent();
            PsiReferenceExpression reference =
                (PsiReferenceExpression) parent;
            PsiModifierListOwner member =
                (PsiModifierListOwner) reference.resolve();
            if (member == null) {
                return;
            }
            PsiModifierList modifiers = member.getModifierList();
            if (modifiers == null) {
                return;
            }
            modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
            modifiers.setModifierProperty(PsiModifier.PROTECTED, false);
            modifiers.setModifierProperty(PsiModifier.PRIVATE, false);
        }

        private static void makeConstructorPackageLocal(
            Project project,
            PsiElement element
        ) {
            PsiNewExpression newExpression =
                PsiTreeUtil.getParentOfType(
                    element,
                    PsiNewExpression.class
                );
            if (newExpression == null) {
                return;
            }
            PsiMethod constructor =
                newExpression.resolveConstructor();
            if (constructor != null) {
                PsiModifierList modifierList =
                    constructor.getModifierList();
                modifierList.setModifierProperty(
                    PsiModifier.PRIVATE,
                    false
                );
                return;
            }
            PsiJavaCodeReferenceElement referenceElement =
                (PsiJavaCodeReferenceElement) element;
            PsiElement target = referenceElement.resolve();
            if (!(target instanceof PsiClass)) {
                return;
            }
            PsiClass aClass = (PsiClass) target;
            PsiElementFactory elementFactory =
                JavaPsiFacade.getElementFactory(project);
            PsiMethod newConstructor = elementFactory.createConstructor();
            PsiModifierList modifierList =
                newConstructor.getModifierList();
            modifierList.setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
            aClass.add(newConstructor);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new PrivateMemberAccessFromInnerClassVisior();
    }

    private static class PrivateMemberAccessFromInnerClassVisior
        extends BaseInspectionVisitor {

        @Override
        public void visitNewExpression(PsiNewExpression expression) {
     /* if (JspPsiUtil.isInJspFile(expression)) {
        return;
      }     */
            super.visitNewExpression(expression);
            PsiClass containingClass =
                getContainingContextClass(expression);
            if (containingClass == null) {
                return;
            }
            PsiMethod constructor = expression.resolveConstructor();
            if (constructor == null) {
                PsiJavaCodeReferenceElement classReference =
                    expression.getClassOrAnonymousClassReference();
                if (classReference == null) {
                    return;
                }
                PsiElement target = classReference.resolve();
                if (!(target instanceof PsiClass)) {
                    return;
                }
                PsiClass aClass = (PsiClass) target;
                if (!aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
                    return;
                }
                if (aClass.equals(containingClass)) {
                    return;
                }
                registerNewExpressionError(expression, aClass);
            }
            else {
                if (!constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
                    return;
                }
                PsiClass aClass = constructor.getContainingClass();
                if (containingClass.equals(aClass)) {
                    return;
                }
                registerNewExpressionError(expression, aClass);
            }
        }

        @Override
        public void visitReferenceExpression(
            @Nonnull PsiReferenceExpression expression
        ) {
    /*  if (JspPsiUtil.isInJspFile(expression)) {
        // disable for jsp files IDEADEV-12957
        return;
      }  */
            super.visitReferenceExpression(expression);
            if (expression.getQualifierExpression() == null) {
                return;
            }
            PsiElement referenceNameElement =
                expression.getReferenceNameElement();
            if (referenceNameElement == null) {
                return;
            }
            PsiElement containingClass =
                getContainingContextClass(expression);
            if (containingClass == null) {
                return;
            }
            PsiElement element = expression.resolve();
            if (!(element instanceof PsiMethod || element instanceof PsiField)) {
                return;
            }
            PsiMember member = (PsiMember) element;
            if (!member.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            PsiClass memberClass =
                ClassUtils.getContainingClass(member);
            if (memberClass == null) {
                return;
            }
            if (memberClass.equals(containingClass)) {
                return;
            }
            registerError(referenceNameElement, memberClass, member);
        }

        @Nullable
        private static PsiClass getContainingContextClass(PsiElement element) {
            PsiClass aClass =
                ClassUtils.getContainingClass(element);
            if (aClass instanceof PsiAnonymousClass) {
                PsiAnonymousClass anonymousClass =
                    (PsiAnonymousClass) aClass;
                PsiExpressionList args = anonymousClass.getArgumentList();
                if (args != null &&
                    PsiTreeUtil.isAncestor(args, element, true)) {
                    return ClassUtils.getContainingClass(aClass);
                }
            }
            return aClass;
        }
    }
}
