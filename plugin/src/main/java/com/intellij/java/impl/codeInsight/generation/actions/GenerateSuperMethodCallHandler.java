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
package com.intellij.java.impl.codeInsight.generation.actions;

import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.language.psi.HierarchicalMethodSignature;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiStatement;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.editor.util.LanguageEditorUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author cdr
 */
public class GenerateSuperMethodCallHandler implements CodeInsightActionHandler {
    private static final Logger LOG = Logger.getInstance(GenerateSuperMethodCallHandler.class);

    @Override
    @RequiredWriteAction
    public void invoke(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
        if (!LanguageEditorUtil.checkModificationAllowed(editor)) {
            return;
        }
        PsiMethod method = canInsertSuper(project, editor, file);
        try {
            PsiMethod template = (PsiMethod) method.copy();

            OverrideImplementUtil.setupMethodBody(template, method, method.getContainingClass());
            PsiStatement superCall = template.getBody().getStatements()[0];
            PsiCodeBlock body = method.getBody();
            PsiElement toGo;
            if (body.getLBrace() == null) {
                toGo = body.addBefore(superCall, null);
            }
            else {
                toGo = body.addAfter(superCall, body.getLBrace());
            }
            toGo = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(toGo);
            editor.getCaretModel().moveToOffset(toGo.getTextOffset());
            editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    @RequiredReadAction
    public static PsiMethod canInsertSuper(Project project, Editor editor, PsiFile file) {
        PsiDocumentManager.getInstance(project).commitAllDocuments();

        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        if (element == null) {
            return null;
        }
        PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
        if (codeBlock != null && codeBlock.getParent() instanceof PsiMethod method) {
            for (HierarchicalMethodSignature superSignature : method.getHierarchicalMethodSignature().getSuperSignatures()) {
                if (!superSignature.getMethod().isAbstract()) {
                    return method;
                }
            }
        }
        return null;
    }
}
