/*
 * Copyright 2006-2012 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ReflectionForUnavailableAnnotationInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.reflectionForUnavailableAnnotationDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.reflectionForUnavailableAnnotationProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ReflectionForUnavailableAnnotationVisitor();
    }

    private static class ReflectionForUnavailableAnnotationVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            @NonNls String methodName = methodExpression.getReferenceName();
            if (!"isAnnotationPresent".equals(methodName) && !"getAnnotation".equals(methodName)) {
                return;
            }
            PsiExpressionList argumentList = expression.getArgumentList();
            PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 1) {
                return;
            }
            PsiExpression arg = args[0];
            if (arg == null) {
                return;
            }
            if (!(arg instanceof PsiClassObjectAccessExpression)) {
                return;
            }
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, "java.lang.reflect.AnnotatedElement")) {
                return;
            }
            PsiClassObjectAccessExpression classObjectAccessExpression = (PsiClassObjectAccessExpression) arg;
            PsiTypeElement operand = classObjectAccessExpression.getOperand();

            PsiClassType annotationClassType = (PsiClassType) operand.getType();
            PsiClass annotationClass = annotationClassType.resolve();
            if (annotationClass == null) {
                return;
            }
            PsiModifierList modifierList = annotationClass.getModifierList();
            if (modifierList == null) {
                return;
            }
            PsiAnnotation retentionAnnotation = modifierList.findAnnotation(CommonClassNames.JAVA_LANG_ANNOTATION_RETENTION);
            if (retentionAnnotation == null) {
                registerError(arg);
                return;
            }
            PsiAnnotationParameterList parameters = retentionAnnotation.getParameterList();
            PsiNameValuePair[] attributes = parameters.getAttributes();
            for (PsiNameValuePair attribute : attributes) {
                @NonNls String name = attribute.getName();
                if (name != null && !"value".equals(name)) {
                    continue;
                }
                PsiAnnotationMemberValue value = attribute.getValue();
                if (value == null) {
                    continue;
                }
                @NonNls String text = value.getText();
                if (!text.contains("RUNTIME")) {
                    registerError(arg);
                    return;
                }
            }
        }
    }
}