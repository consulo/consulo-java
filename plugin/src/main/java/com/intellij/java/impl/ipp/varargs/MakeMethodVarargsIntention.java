/*
 * Copyright 2006 Bas Leijdekkers
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
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.MakeMethodVarargsIntention", fileExtensions = "java", categories = {"Java", "Declaration"})
public class MakeMethodVarargsIntention extends Intention {

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.makeMethodVarargsIntentionName();
    }

    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new MakeMethodVarargsPredicate();
    }

    protected void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        makeMethodVarargs(element);
        makeMethodCallsVarargs(element);
    }

    private static void makeMethodVarargs(PsiElement element)
        throws IncorrectOperationException {
        PsiParameterList parameterList = (PsiParameterList) element;
        PsiParameter[] parameters = parameterList.getParameters();
        PsiParameter lastParameter = parameters[parameters.length - 1];
        PsiType type = lastParameter.getType();
        PsiType componentType = type.getDeepComponentType();
        String text = componentType.getCanonicalText();
        PsiManager manager = element.getManager();
        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        PsiParameter newParameter =
            factory.createParameterFromText(text + "... " +
                lastParameter.getName(), element);
        lastParameter.replace(newParameter);
    }

    private static void makeMethodCallsVarargs(PsiElement element)
        throws IncorrectOperationException {
        PsiMethod method = (PsiMethod) element.getParent();
        Query<PsiReference> query =
            ReferencesSearch.search(method, method.getUseScope(), false);
        for (PsiReference reference : query) {
            PsiElement referenceElement = reference.getElement();
            if (!(referenceElement instanceof PsiReferenceExpression)) {
                continue;
            }
            PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) referenceElement;
            PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) referenceExpression.getParent();
            PsiExpressionList argumentList =
                methodCallExpression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length == 0) {
                continue;
            }
            PsiExpression lastArgument = arguments[arguments.length - 1];
            if (!(lastArgument instanceof PsiNewExpression)) {
                continue;
            }
            PsiNewExpression newExpression =
                (PsiNewExpression) lastArgument;
            PsiArrayInitializerExpression arrayInitializerExpression =
                newExpression.getArrayInitializer();
            if (arrayInitializerExpression == null) {
                continue;
            }
            PsiExpression[] initializers =
                arrayInitializerExpression.getInitializers();
            for (PsiExpression initializer : initializers) {
                argumentList.add(initializer);
            }
            lastArgument.delete();
        }
    }
}
