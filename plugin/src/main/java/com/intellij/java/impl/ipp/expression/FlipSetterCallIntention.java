/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.java.impl.ipp.expression;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementEditorPredicate;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.PsiSelectionSearcher;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.FlipSetterCallIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class FlipSetterCallIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.flipSetterCallIntentionName();
    }

    protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        Project project = element.getProject();
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor != null) {
            List<PsiMethodCallExpression> methodCalls =
                PsiSelectionSearcher.searchElementsInSelection(editor, project, PsiMethodCallExpression.class, false);
            if (methodCalls.size() > 0) {
                for (PsiMethodCallExpression call : methodCalls) {
                    flipCall(call);
                }
                editor.getSelectionModel().removeSelection();
                return;
            }
        }
        if (element instanceof PsiMethodCallExpression) {
            flipCall((PsiMethodCallExpression) element);
        }
    }

    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new SetterCallPredicate();
    }

    private static void flipCall(PsiMethodCallExpression call) {
        PsiExpression qualifierExpression1 = call.getMethodExpression().getQualifierExpression();
        if (qualifierExpression1 == null) {
            return;
        }
        PsiExpression[] arguments = call.getArgumentList().getExpressions();
        if (arguments.length != 1) {
            return;
        }
        PsiExpression argument = arguments[0];
        if (!(argument instanceof PsiMethodCallExpression)) {
            return;
        }
        PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) argument;
        PsiExpression qualifierExpression2 = methodCallExpression.getMethodExpression().getQualifierExpression();
        if (qualifierExpression2 == null) {
            return;
        }
        PsiMethod setter = call.resolveMethod();
        PsiMethod getter = methodCallExpression.resolveMethod();
        PsiMethod get = PropertyUtil.getReversePropertyMethod(setter);
        PsiMethod set = PropertyUtil.getReversePropertyMethod(getter);
        if (get == null || set == null) {
            return;
        }
        String text =
            qualifierExpression2.getText() + "" + set.getName() + "(" + qualifierExpression1.getText() + "." + get.getName() + "())";
        PsiExpression newExpression = JavaPsiFacade.getElementFactory(call.getProject()).createExpressionFromText(text, call);
        call.replace(newExpression);
    }

    private static boolean isSetGetMethodCall(PsiElement element) {
        if (!(element instanceof PsiMethodCallExpression)) {
            return false;
        }
        PsiMethodCallExpression call1 = (PsiMethodCallExpression) element;
        PsiExpression[] arguments = call1.getArgumentList().getExpressions();
        if (arguments.length != 1) {
            return false;
        }
        PsiExpression argument = arguments[0];
        if (!(argument instanceof PsiMethodCallExpression)) {
            return false;
        }
        PsiMethodCallExpression call2 = (PsiMethodCallExpression) argument;
        PsiMethod setter = call1.resolveMethod();
        PsiMethod getter = call2.resolveMethod();
        PsiMethod get = PropertyUtil.getReversePropertyMethod(setter);
        PsiMethod set = PropertyUtil.getReversePropertyMethod(getter);
        if (setter == null || getter == null || get == null || set == null) {
            return false;
        }

        //check types compatibility
        PsiParameter[] parameters = setter.getParameterList().getParameters();
        if (parameters.length != 1) {
            return false;
        }
        PsiParameter parameter = parameters[0];
        return parameter.getType().equals(getter.getReturnType());
    }

    private static class SetterCallPredicate extends PsiElementEditorPredicate {
        @Override
        public boolean satisfiedBy(PsiElement element, @Nullable Editor editor) {
            if (editor != null && editor.getSelectionModel().hasSelection()) {
                List<PsiMethodCallExpression> list =
                    PsiSelectionSearcher.searchElementsInSelection(editor, element.getProject(), PsiMethodCallExpression.class, false);
                for (PsiMethodCallExpression methodCallExpression : list) {
                    if (isSetGetMethodCall(methodCallExpression)) {
                        return true;
                    }
                }
            }
            return isSetGetMethodCall(element);
        }
    }
}
