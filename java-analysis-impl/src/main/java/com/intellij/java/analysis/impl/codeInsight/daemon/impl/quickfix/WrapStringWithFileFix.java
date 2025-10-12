/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

public class WrapStringWithFileFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
    public final static MyMethodArgumentFixerFactory REGISTAR = new MyMethodArgumentFixerFactory();

    @Nullable
    private final PsiType myType;

    public WrapStringWithFileFix(@Nullable PsiType type, @Nonnull PsiExpression expression) {
        super(expression);
        myType = type;
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return JavaQuickFixLocalize.wrapWithJavaIoFileText();
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, @Nonnull PsiFile file, @Nonnull PsiElement startElement, @Nonnull PsiElement endElement) {
        return myType != null && myType.isValid() && myType.equalsToText(CommonClassNames.JAVA_IO_FILE) && startElement.isValid() && startElement.getManager().isInProject(startElement) &&
            isStringType(startElement);
    }

    @Override
    public void invoke(@Nonnull Project project, @Nonnull PsiFile file, @Nullable Editor editor, @Nonnull PsiElement startElement, @Nonnull PsiElement endElement) {
        startElement.replace(getModifiedExpression(startElement));
    }

    private static boolean isStringType(@Nonnull PsiElement expression) {
        if (!(expression instanceof PsiExpression)) {
            return false;
        }
        final PsiType type = ((PsiExpression) expression).getType();
        if (type == null) {
            return false;
        }
        return type.equalsToText(CommonClassNames.JAVA_LANG_STRING);
    }

    private static PsiElement getModifiedExpression(@Nonnull PsiElement expression) {
        return JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(PsiKeyword.NEW + " " + CommonClassNames.JAVA_IO_FILE + "(" + expression.getText() + ")", expression);
    }

    private static class MyMethodArgumentFix extends MethodArgumentFix implements HighPriorityAction {

        protected MyMethodArgumentFix(@Nonnull PsiExpressionList list, int i, @Nonnull PsiType toType, @Nonnull ArgumentFixerActionFactory fixerActionFactory) {
            super(list, i, toType, fixerActionFactory);
        }

        @Nls
        @Nonnull
        @Override
        public LocalizeValue getText() {
            if (myArgList.getExpressions().length == 1) {
                return JavaQuickFixLocalize.wrapWithJavaIoFileParameterSingleText();
            }
            else {
                return JavaQuickFixLocalize.wrapWithJavaIoFileParameterMultipleText(myIndex + 1);
            }
        }

        @Override
        public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
            return PsiUtil.isLanguageLevel8OrHigher(file) && super.isAvailable(project, editor, file);
        }
    }

    public static class MyMethodArgumentFixerFactory extends ArgumentFixerActionFactory {
        @Nullable
        @Override
        protected PsiExpression getModifiedArgument(final PsiExpression expression, final PsiType toType) throws IncorrectOperationException {
            return isStringType(expression) && toType.equalsToText(CommonClassNames.JAVA_IO_FILE) ? (PsiExpression) getModifiedExpression(expression) : null;
        }

        @Override
        public boolean areTypesConvertible(@Nonnull final PsiType exprType, @Nonnull final PsiType parameterType, @Nonnull final PsiElement context) {
            return parameterType.isConvertibleFrom(exprType) || (parameterType.equalsToText(CommonClassNames.JAVA_IO_FILE) && exprType.equalsToText(CommonClassNames.JAVA_LANG_STRING));
        }

        @Override
        public MethodArgumentFix createFix(final PsiExpressionList list, final int i, final PsiType toType) {
            return new MyMethodArgumentFix(list, i, toType, this);
        }
    }
}
