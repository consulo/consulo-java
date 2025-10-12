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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class MethodThrowsFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private static final Logger LOG = Logger.getInstance(MethodThrowsFix.class);

    private final String myThrowsCanonicalText;
    private final boolean myShouldThrow;
    private final String myMethodName;

    public MethodThrowsFix(PsiMethod method, PsiClassType exceptionType, boolean shouldThrow, boolean showContainingClass) {
        super(method);
        myThrowsCanonicalText = exceptionType.getCanonicalText();
        myShouldThrow = shouldThrow;
        myMethodName = PsiFormatUtil.formatMethod(method,
            PsiSubstitutor.EMPTY,
            PsiFormatUtilBase.SHOW_NAME | (showContainingClass ? PsiFormatUtilBase.SHOW_CONTAINING_CLASS
                : 0),
            0);
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        if (myShouldThrow) {
            return JavaQuickFixLocalize.fixThrowsListAddException(myThrowsCanonicalText, myMethodName);
        }
        else {
            return JavaQuickFixLocalize.fixThrowsListRemoveException(myThrowsCanonicalText, myMethodName);
        }
    }

    @Override
    public boolean isAvailable(@Nonnull Project project,
                               @Nonnull PsiFile file,
                               @Nonnull PsiElement startElement,
                               @Nonnull PsiElement endElement) {
        final PsiMethod myMethod = (PsiMethod) startElement;
        return myMethod.isValid()
            && myMethod.getManager().isInProject(myMethod);
    }

    @Override
    public void invoke(@Nonnull Project project,
                       @Nonnull PsiFile file,
                       @Nullable Editor editor,
                       @Nonnull PsiElement startElement,
                       @Nonnull PsiElement endElement) {
        final PsiMethod myMethod = (PsiMethod) startElement;
        if (!FileModificationService.getInstance().prepareFileForWrite(myMethod.getContainingFile())) {
            return;
        }
        PsiJavaCodeReferenceElement[] referenceElements = myMethod.getThrowsList().getReferenceElements();
        try {
            boolean alreadyThrows = false;
            for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
                if (referenceElement.getCanonicalText().equals(myThrowsCanonicalText)) {
                    alreadyThrows = true;
                    if (!myShouldThrow) {
                        referenceElement.delete();
                        break;
                    }
                }
            }
            if (myShouldThrow && !alreadyThrows) {
                final PsiElementFactory factory = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory();
                final PsiClassType type = (PsiClassType) factory.createTypeFromText(myThrowsCanonicalText, myMethod);
                PsiJavaCodeReferenceElement ref = factory.createReferenceElementByType(type);
                ref = (PsiJavaCodeReferenceElement) JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref);
                myMethod.getThrowsList().add(ref);
            }
            LanguageUndoUtil.markPsiFileForUndo(file);
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }
}
