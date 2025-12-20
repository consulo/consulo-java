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
package com.intellij.java.impl.ig.internationalization;

import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationFix;
import com.intellij.java.impl.ig.DelegatingFix;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class StringToUpperWithoutLocaleInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "StringToUpperCaseOrToLowerCaseWithoutLocale";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.stringTouppercaseTolowercaseWithoutLocaleDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.stringTouppercaseTolowercaseWithoutLocaleProblemDescriptor().get();
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        PsiReferenceExpression methodExpression = (PsiReferenceExpression) infos[0];
        PsiModifierListOwner annotatableQualifier = NonNlsUtils.getAnnotatableQualifier(methodExpression);
        if (annotatableQualifier == null) {
            return null;
        }
        return new DelegatingFix(new AddAnnotationFix(AnnotationUtil.NON_NLS, annotatableQualifier));
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new StringToUpperWithoutLocaleVisitor();
    }

    private static class StringToUpperWithoutLocaleVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.TO_UPPER_CASE.equals(methodName) &&
                !HardcodedMethodConstants.TO_LOWER_CASE.equals(methodName)) {
                return;
            }
            if (NonNlsUtils.isNonNlsAnnotatedUse(expression)) {
                return;
            }
            PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() == 1) {
                return;
            }
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            String className = containingClass.getQualifiedName();
            if (!CommonClassNames.JAVA_LANG_STRING.equals(className)) {
                return;
            }
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (NonNlsUtils.isNonNlsAnnotated(qualifier)) {
                return;
            }
            registerMethodCallError(expression, methodExpression);
        }
    }
}