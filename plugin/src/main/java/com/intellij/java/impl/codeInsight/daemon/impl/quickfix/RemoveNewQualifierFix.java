/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiNewExpression;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * User: cdr
 * Date: Nov 29, 2002
 * Time: 3:02:17 PM
 */
public class RemoveNewQualifierFix implements SyntheticIntentionAction {
    private final PsiNewExpression expression;
    private final PsiClass aClass;

    public RemoveNewQualifierFix(PsiNewExpression expression, PsiClass aClass) {
        this.expression = expression;
        this.aClass = aClass;
    }

    @Override
    @Nonnull
    public LocalizeValue getText() {
        return JavaQuickFixLocalize.removeQualifierFix();
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return
            expression != null
                && expression.isValid()
                && (aClass == null || aClass.isValid())
                && expression.getManager().isInProject(expression);
    }

    @Override
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!FileModificationService.getInstance().prepareFileForWrite(expression.getContainingFile())) {
            return;
        }
        PsiJavaCodeReferenceElement classReference = expression.getClassReference();
        expression.getQualifier().delete();
        if (aClass != null && classReference != null) {
            classReference.bindToElement(aClass);
        }
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

}
