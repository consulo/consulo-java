/*
 * Copyright 2006-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.varargs;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConvertVarargParameterToArrayIntention", fileExtensions = "java", categories = {"Java", "Declaration"})
public class ConvertVarargParameterToArrayIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.convertVarargParameterToArrayIntentionName();
    }

    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new ConvertVarargParameterToArrayPredicate();
    }

    protected void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        final PsiParameterList parameterList = (PsiParameterList) element;
        convertVarargsToArray(parameterList);
    }

    private static void convertVarargsToArray(PsiParameterList parameterList)
        throws IncorrectOperationException {
        final PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length == 0) {
            return;
        }
        final PsiParameter lastParameter = parameters[parameters.length - 1];
        if (lastParameter == null || !lastParameter.isVarArgs()) {
            return;
        }
        final PsiEllipsisType type =
            (PsiEllipsisType) lastParameter.getType();
        final PsiMethod method = (PsiMethod) parameterList.getParent();
        final Query<PsiReference> query = ReferencesSearch.search(method);
        final PsiType componentType = type.getComponentType();
        final String typeText = componentType.getCanonicalText();
        final int parameterIndex =
            parameterList.getParameterIndex(lastParameter);
        for (PsiReference reference : query) {
            final PsiElement referenceElement = reference.getElement();
            if (!(referenceElement instanceof PsiReferenceExpression)) {
                continue;
            }
            final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) referenceElement;
            final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) referenceExpression.getParent();
            modifyCall(methodCallExpression, typeText, parameterIndex);
        }
        final PsiType arrayType = type.toArrayType();
        final Project project = lastParameter.getProject();
        final PsiElementFactory factory =
            JavaPsiFacade.getElementFactory(project);
        final PsiTypeElement newTypeElement =
            factory.createTypeElement(arrayType);
        final PsiTypeElement typeElement =
            lastParameter.getTypeElement();
        typeElement.replace(newTypeElement);
    }

    public static void modifyCall(PsiMethodCallExpression methodCallExpression,
                                  String arrayTypeText,
                                  int indexOfFirstVarargArgument)
        throws IncorrectOperationException {
        final PsiExpressionList argumentList =
            methodCallExpression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        @NonNls final StringBuilder builder = new StringBuilder("new ");
        builder.append(arrayTypeText);
        builder.append("[]{");
        if (arguments.length > indexOfFirstVarargArgument) {
            final PsiExpression firstArgument =
                arguments[indexOfFirstVarargArgument];
            final String firstArgumentText = firstArgument.getText();
            builder.append(firstArgumentText);
            for (int i = indexOfFirstVarargArgument + 1;
                 i < arguments.length; i++) {
                builder.append(',');
                builder.append(arguments[i].getText());
            }
        }
        builder.append('}');
        final Project project = methodCallExpression.getProject();
        final PsiElementFactory factory =
            JavaPsiFacade.getElementFactory(project);
        final PsiExpression arrayExpression =
            factory.createExpressionFromText(builder.toString(),
                methodCallExpression);
        if (arguments.length > indexOfFirstVarargArgument) {
            final PsiExpression firstArgument =
                arguments[indexOfFirstVarargArgument];
            argumentList.deleteChildRange(firstArgument,
                arguments[arguments.length - 1]);
            argumentList.add(arrayExpression);
        }
        else {
            argumentList.add(arrayExpression);
        }
        final JavaCodeStyleManager javaCodeStyleManager =
            JavaCodeStyleManager.getInstance(project);
        javaCodeStyleManager.shortenClassReferences(argumentList);
        final CodeStyleManager codeStyleManager =
            CodeStyleManager.getInstance(project);
        codeStyleManager.reformat(argumentList);
    }
}