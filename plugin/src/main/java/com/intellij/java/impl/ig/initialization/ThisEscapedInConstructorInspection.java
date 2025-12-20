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
package com.intellij.java.impl.ig.initialization;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class ThisEscapedInConstructorInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "ThisEscapedInObjectConstruction";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.thisReferenceEscapedInConstructionDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.thisReferenceEscapedInConstructionProblemDescriptor().get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ThisExposedInConstructorInspectionVisitor();
    }

    private static class ThisExposedInConstructorInspectionVisitor extends BaseInspectionVisitor {

        @Override
        public void visitThisExpression(PsiThisExpression expression) {
            super.visitThisExpression(expression);
            if (!isInInitializer(expression)) {
                return;
            }
            PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
            PsiClass containingClass = ClassUtils.getContainingClass(expression);
            if (qualifier != null) {
                PsiElement element = qualifier.resolve();
                if (!(element instanceof PsiClass)) {
                    return;
                }
                PsiClass aClass = (PsiClass) element;
                if (!aClass.equals(containingClass)) {
                    return;
                }
            }
            PsiElement parent = expression.getParent();
            if (parent instanceof PsiAssignmentExpression) {
                PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) parent;
                if (!thisEscapesToField(expression, assignmentExpression)) {
                    return;
                }
                registerError(expression);
            }
            else if (parent instanceof PsiExpressionList) {
                PsiElement grandParent = parent.getParent();
                if (grandParent instanceof PsiNewExpression) {
                    PsiNewExpression newExpression = (PsiNewExpression) grandParent;
                    if (!thisEscapesToConstructor(expression, newExpression)) {
                        return;
                    }
                    registerError(expression);
                }
                else if (grandParent instanceof PsiMethodCallExpression) {
                    PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) grandParent;
                    if (!thisEscapesToMethod(expression, methodCallExpression)) {
                        return;
                    }
                    registerError(expression);
                }
            }
        }

        private static boolean thisEscapesToMethod(PsiThisExpression expression, PsiMethodCallExpression methodCallExpression) {
            PsiMethod method = methodCallExpression.resolveMethod();
            if (method == null) {
                return false;
            }
            PsiClass containingClass = ClassUtils.getContainingClass(expression);
            if (containingClass == null) {
                return false;
            }
            PsiClass methodClass = method.getContainingClass();
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                return true;
            }
            return methodClass != null && !containingClass.isInheritor(methodClass, true);
        }

        private static boolean thisEscapesToConstructor(PsiThisExpression expression, PsiNewExpression newExpression) {
            PsiClass containingClass = ClassUtils.getContainingClass(expression);
            PsiJavaCodeReferenceElement referenceElement = newExpression.getClassReference();
            if (referenceElement == null) {
                return false;
            }
            PsiElement element = referenceElement.resolve();
            if (!(element instanceof PsiClass)) {
                return false;
            }
            PsiClass constructorClass = (PsiClass) element;
            return !PsiTreeUtil.isAncestor(containingClass, constructorClass, false) ||
                constructorClass.hasModifierProperty(PsiModifier.STATIC);
        }

        private static boolean thisEscapesToField(PsiThisExpression expression, PsiAssignmentExpression assignmentExpression) {
            PsiExpression rhs = assignmentExpression.getRExpression();
            if (!expression.equals(rhs)) {
                return false;
            }
            PsiExpression lExpression = assignmentExpression.getLExpression();
            if (!(lExpression instanceof PsiReferenceExpression)) {
                return false;
            }
            PsiReferenceExpression leftExpression = (PsiReferenceExpression) lExpression;
            PsiElement element = leftExpression.resolve();
            if (!(element instanceof PsiField)) {
                return false;
            }
            PsiField field = (PsiField) element;
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                return true;
            }
            PsiClass assignmentClass = ClassUtils.getContainingClass(assignmentExpression);
            PsiClass fieldClass = field.getContainingClass();
            return !(assignmentClass == null || fieldClass == null || assignmentClass.isInheritor(fieldClass, true) ||
                PsiTreeUtil.isAncestor(assignmentClass, fieldClass, false));
        }

        /**
         * @return true if CallExpression is in a constructor, instance
         * initializer, or field initializer. Otherwise false
         */
        private static boolean isInInitializer(PsiElement call) {
            PsiMethod method = PsiTreeUtil.getParentOfType(call, PsiMethod.class, true, PsiClass.class);
            if (method != null) {
                return method.isConstructor();
            }
            PsiField field = PsiTreeUtil.getParentOfType(call, PsiField.class, true, PsiClass.class);
            if (field != null) {
                return true;
            }
            PsiClassInitializer classInitializer = PsiTreeUtil.getParentOfType(call, PsiClassInitializer.class, true, PsiClass.class);
            return classInitializer != null && !classInitializer.hasModifierProperty(PsiModifier.STATIC);
        }
    }
}