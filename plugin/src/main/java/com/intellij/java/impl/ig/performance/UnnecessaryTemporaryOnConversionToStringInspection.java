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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class UnnecessaryTemporaryOnConversionToStringInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.unnecessaryTemporaryOnConversionToStringDisplayName();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        String replacementString = calculateReplacementExpression((PsiMethodCallExpression) infos[0]);
        return InspectionGadgetsLocalize.unnecessaryTemporaryOnConversionFromStringProblemDescriptor(replacementString).get();
    }

    @Nullable
    static String calculateReplacementExpression(PsiMethodCallExpression expression) {
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (!(qualifier instanceof PsiNewExpression)) {
            return null;
        }
        PsiNewExpression newExpression = (PsiNewExpression) qualifier;
        PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList == null) {
            return null;
        }
        PsiExpression[] expressions = argumentList.getExpressions();
        if (expressions.length < 1) {
            return null;
        }
        PsiType type = newExpression.getType();
        if (type == null) {
            return null;
        }
        PsiExpression argument = expressions[0];
        String argumentText = argument.getText();
        String qualifierType = type.getPresentableText();
        return qualifierType + ".toString(" + argumentText + ')';
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        String replacement = calculateReplacementExpression((PsiMethodCallExpression) infos[0]);
        LocalizeValue name = InspectionGadgetsLocalize.unnecessaryTemporaryOnConversionFromStringFixName(replacement);
        return new UnnecessaryTemporaryObjectFix(name);
    }

    private static class UnnecessaryTemporaryObjectFix extends InspectionGadgetsFix {
        @Nonnull
        private final LocalizeValue myName;

        private UnnecessaryTemporaryObjectFix(@Nonnull LocalizeValue name) {
            myName = name;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return myName;
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiMethodCallExpression expression = (PsiMethodCallExpression) descriptor.getPsiElement();
            String newExpression = calculateReplacementExpression(expression);
            if (newExpression == null) {
                return;
            }
            replaceExpression(expression, newExpression);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryTemporaryObjectVisitor();
    }

    private static class UnnecessaryTemporaryObjectVisitor extends BaseInspectionVisitor {
        /**
         * @noinspection StaticCollection
         */
        private static final Set<String> s_basicTypes = new HashSet<String>(8);

        static {
            s_basicTypes.add(CommonClassNames.JAVA_LANG_BOOLEAN);
            s_basicTypes.add(CommonClassNames.JAVA_LANG_BYTE);
            s_basicTypes.add(CommonClassNames.JAVA_LANG_CHARACTER);
            s_basicTypes.add(CommonClassNames.JAVA_LANG_DOUBLE);
            s_basicTypes.add(CommonClassNames.JAVA_LANG_FLOAT);
            s_basicTypes.add(CommonClassNames.JAVA_LANG_INTEGER);
            s_basicTypes.add(CommonClassNames.JAVA_LANG_LONG);
            s_basicTypes.add(CommonClassNames.JAVA_LANG_SHORT);
        }

        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.TO_STRING.equals(methodName)) {
                return;
            }
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiNewExpression)) {
                return;
            }
            PsiNewExpression newExpression = (PsiNewExpression) qualifier;
            PsiExpressionList argumentList = newExpression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length < 1) {
                return;
            }
            PsiExpression argument = arguments[0];
            PsiType argumentType = argument.getType();
            if (argumentType != null && argumentType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
                return;
            }
            PsiType type = qualifier.getType();
            if (type == null) {
                return;
            }
            String typeName = type.getCanonicalText();
            if (!s_basicTypes.contains(typeName)) {
                return;
            }
            registerError(expression, expression);
        }
    }
}