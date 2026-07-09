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
package com.intellij.java.impl.vcsUtil;

import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.util.TextRange;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.TargetElementUtilExtender;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.versionControlSystem.VcsBundle;
import consulo.versionControlSystem.action.VcsContext;
import consulo.versionControlSystem.history.VcsSelection;
import consulo.versionControlSystem.history.VcsSelectionProvider;
import consulo.versionControlSystem.localize.VcsLocalize;
import consulo.virtualFileSystem.VirtualFile;

import org.jspecify.annotations.Nullable;
import java.util.Set;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaVcsSelectionProvider implements VcsSelectionProvider {
  @Nullable
  @RequiredUIAccess
  public VcsSelection getSelection(VcsContext context) {
    Editor editor = context.getEditor();
    if (editor == null) return null;
    PsiElement psiElement = TargetElementUtil.findTargetElement(editor, Set.of(TargetElementUtilExtender.ELEMENT_NAME_ACCEPTED));
    if (psiElement == null) {
      return null;
    }
    if (!psiElement.isValid()) {
      return null;
    }
    if (psiElement instanceof PsiCompiledElement) {
      return null;
    }

    LocalizeValue actionName;

    if (psiElement instanceof PsiClass) {
      actionName = VcsLocalize.actionNameShowHistoryForClass();
    } else if (psiElement instanceof PsiField) {
      actionName = VcsLocalize.actionNameShowHistoryForField();
    } else if (psiElement instanceof PsiMethod) {
      actionName = VcsLocalize.actionNameShowHistoryForMethod();
    } else if (psiElement instanceof PsiCodeBlock) {
      actionName = VcsLocalize.actionNameShowHistoryForCodeBlock();
    } else if (psiElement instanceof PsiStatement) {
      actionName = VcsLocalize.actionNameShowHistoryForStatement();
    } else {
      return null;
    }

    TextRange textRange = psiElement.getTextRange();
    if (textRange == TextRange.EMPTY_RANGE) {
      return null;
    }

    VirtualFile virtualFile = psiElement.getContainingFile().getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    if (!virtualFile.isValid()) {
      return null;
    }

    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    return new VcsSelection(document, textRange, actionName);
  }
}
