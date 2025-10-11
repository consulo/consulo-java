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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.impl.ig.psiutils.FormatUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.ConstantExpressionUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class MalformedFormatStringInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.malformedFormatStringDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        final Object value = infos[0];
        if (value instanceof Exception) {
            return InspectionGadgetsLocalize.malformedFormatStringProblemDescriptorMalformed().get();
        }
        final Validator[] validators = (Validator[]) value;
        final int argumentCount = ((Integer) infos[1]).intValue();
        if (validators.length < argumentCount) {
            return InspectionGadgetsLocalize.malformedFormatStringProblemDescriptorTooManyArguments().get();
        }
        if (validators.length > argumentCount) {
            return InspectionGadgetsLocalize.malformedFormatStringProblemDescriptorTooFewArguments().get();
        }
        return InspectionGadgetsLocalize.malformedFormatStringProblemDescriptorArgumentsDoNotMatchType().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new MalformedFormatStringVisitor();
    }

    private static class MalformedFormatStringVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!FormatUtils.isFormatCall(expression)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length == 0) {
                return;
            }
            final PsiExpression firstArgument = arguments[0];
            final PsiType type = firstArgument.getType();
            if (type == null) {
                return;
            }
            final int formatArgumentIndex;
            if ("java.util.Locale".equals(type.getCanonicalText()) && arguments.length > 1) {
                formatArgumentIndex = 1;
            }
            else {
                formatArgumentIndex = 0;
            }
            final PsiExpression formatArgument = arguments[formatArgumentIndex];
            if (!ExpressionUtils.hasStringType(formatArgument)) {
                return;
            }
            if (!PsiUtil.isConstantExpression(formatArgument)) {
                return;
            }
            final PsiType formatType = formatArgument.getType();
            if (formatType == null) {
                return;
            }
            final String value = (String) ConstantExpressionUtil.computeCastTo(formatArgument, formatType);
            if (value == null) {
                return;
            }
            final int argumentCount = arguments.length - (formatArgumentIndex + 1);
            final Validator[] validators;
            try {
                validators = FormatDecode.decode(value, argumentCount);
            }
            catch (Exception e) {
                registerError(formatArgument, e);
                return;
            }
            if (validators.length != argumentCount) {
                if (argumentCount == 1) {
                    final PsiExpression argument = arguments[formatArgumentIndex + 1];
                    final PsiType argumentType = argument.getType();
                    if (argumentType instanceof PsiArrayType) {
                        return;
                    }
                }
                registerError(formatArgument, validators, Integer.valueOf(argumentCount));
                return;
            }
            for (int i = 0; i < validators.length; i++) {
                final Validator validator = validators[i];
                final PsiType argumentType = arguments[i + formatArgumentIndex + 1].getType();
                if (argumentType == null) {
                    continue;
                }
                if (!validator.valid(argumentType)) {
                    registerError(formatArgument, validators, Integer.valueOf(argumentCount));
                    return;
                }
            }
        }
    }
}