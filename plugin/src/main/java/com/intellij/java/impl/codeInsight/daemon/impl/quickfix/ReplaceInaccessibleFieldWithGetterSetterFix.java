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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

public class ReplaceInaccessibleFieldWithGetterSetterFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final String myMethodName;
    private final boolean myIsSetter;

    public ReplaceInaccessibleFieldWithGetterSetterFix(PsiElement element, PsiMethod getter, boolean isSetter) {
        super(element);
        myMethodName = getter.getName();
        myIsSetter = isSetter;
    }

    @Override
    public void invoke(
        Project project,
        PsiFile file,
        @Nullable Editor editor,
        PsiElement startElement,
        PsiElement endElement
    ) {
        PsiReferenceExpression place = (PsiReferenceExpression) startElement;
        if (!FileModificationService.getInstance().preparePsiElementForWrite(place)) {
            return;
        }
        String qualifier = null;
        PsiExpression qualifierExpression = place.getQualifierExpression();
        if (qualifierExpression != null) {
            qualifier = qualifierExpression.getText();
        }
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        PsiMethodCallExpression callExpression;
        String call = (qualifier != null ? qualifier + "." : "") + myMethodName;
        if (!myIsSetter) {
            callExpression = (PsiMethodCallExpression) elementFactory.createExpressionFromText(call + "()", null);
            callExpression = (PsiMethodCallExpression) CodeStyleManager.getInstance(project).reformat(callExpression);
            place.replace(callExpression);
        }
        else {
            PsiElement parent = PsiTreeUtil.skipParentsOfType(place, PsiParenthesizedExpression.class);
            if (parent instanceof PsiAssignmentExpression) {
                PsiExpression rExpression = ((PsiAssignmentExpression) parent).getRExpression();
                String argList = rExpression != null ? rExpression.getText() : "";
                callExpression = (PsiMethodCallExpression) elementFactory.createExpressionFromText(call + "(" + argList + ")", null);
                callExpression = (PsiMethodCallExpression) CodeStyleManager.getInstance(project).reformat(callExpression);
                parent.replace(callExpression);
            }
        }
    }

    @Override
    public LocalizeValue getText() {
        return LocalizeValue.localizeTODO(myIsSetter ? "Replace with setter" : "Replace with getter");
    }
}
