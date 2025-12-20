/*
 * Copyright 2007-2010 Bas Leijdekkers
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
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import java.util.*;

@ExtensionImpl
public class ComparatorMethodParameterNotUsedInspection
    extends BaseInspection {

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.comparatorMethodParameterNotUsedDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.comparatorMethodParameterNotUsedProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new CompareMethodDoesNotUseParameterVisitor();
    }

    private static class CompareMethodDoesNotUseParameterVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            if (!MethodUtils.methodMatches(
                method,
                CommonClassNames.JAVA_UTIL_COMPARATOR,
                PsiType.INT,
                "compare",
                PsiType.NULL,
                PsiType.NULL
            )) {
                return;
            }
            PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            PsiParameterList parameterList = method.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            ParameterAccessVisitor visitor =
                new ParameterAccessVisitor(parameters);
            body.accept(visitor);
            Collection<PsiParameter> unusedParameters =
                visitor.getUnusedParameters();
            for (PsiParameter unusedParameter : unusedParameters) {
                registerVariableError(unusedParameter);
            }
        }

        private static class ParameterAccessVisitor
            extends JavaRecursiveElementVisitor {

            private final Set<PsiParameter> parameters;

            ParameterAccessVisitor(@Nonnull PsiParameter[] parameters) {
                this.parameters = new HashSet(Arrays.asList(parameters));
            }

            @Override
            public void visitReferenceExpression(
                PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                if (parameters.isEmpty()) {
                    return;
                }
                if (expression.getQualifierExpression() != null) {
                    // optimization
                    // references to parameters are never qualified
                    return;
                }
                PsiElement target = expression.resolve();
                if (!(target instanceof PsiParameter)) {
                    return;
                }
                PsiParameter parameter = (PsiParameter) target;
                parameters.remove(parameter);
            }

            public Collection<PsiParameter> getUnusedParameters() {
                return Collections.unmodifiableSet(parameters);
            }
        }
    }
}