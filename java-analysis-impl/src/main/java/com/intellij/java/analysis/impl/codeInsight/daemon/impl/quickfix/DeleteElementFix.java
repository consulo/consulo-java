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

import com.siyeh.ig.psiutils.CommentTracker;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class DeleteElementFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final LocalizeValue myText;

  public DeleteElementFix(@Nonnull PsiElement element) {
    super(element);
    myText = LocalizeValue.empty();
  }

  public DeleteElementFix(@Nonnull PsiElement element, @Nonnull LocalizeValue text) {
    super(element);
    myText = text;
  }

  @Nonnull
  @Override
  public LocalizeValue getText() {
    return myText.orIfEmpty(JavaQuickFixLocalize.deleteElementFixText());
  }

  @Override
  public void invoke(@Nonnull Project project, @Nonnull PsiFile file, @Nullable Editor editor, @Nonnull PsiElement startElement, @Nonnull PsiElement endElement) {
    if (FileModificationService.getInstance().preparePsiElementForWrite(file)) {
      WriteAction.run(() -> new CommentTracker().deleteAndRestoreComments(startElement));
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}