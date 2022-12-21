/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.intention.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.path.FileReference;
import consulo.language.psi.path.FileReferenceSet;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import javax.annotation.Nonnull;

/**
 * @author spleaner
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConvertAbsolutePathToRelativeIntentionAction", categories = {"Java", "Other"}, fileExtensions = "java")
public class ConvertAbsolutePathToRelativeIntentionAction extends BaseIntentionAction {

  protected boolean isConvertToRelative() {
    return true;
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    if (element == null || element instanceof PsiWhiteSpace) {
      return false;
    }

    final PsiReference reference = file.findReferenceAt(offset);
    final FileReference fileReference = reference == null ? null : FileReference.findFileReference(reference);

    if (fileReference != null) {
      final FileReferenceSet set = fileReference.getFileReferenceSet();
      final FileReference lastReference = set.getLastReference();
      return set.couldBeConvertedTo(isConvertToRelative()) && lastReference != null &&
          (!isConvertToRelative() && !set.isAbsolutePathReference() || isConvertToRelative() && set.isAbsolutePathReference()) &&
          lastReference.resolve() != null;
    }

    return false;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
      return;
    }

    final PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
    final FileReference fileReference = reference == null ? null : FileReference.findFileReference(reference);
    if (fileReference != null) {
      final FileReference lastReference = fileReference.getFileReferenceSet().getLastReference();
      if (lastReference != null) {
        lastReference.bindToElement(lastReference.resolve(), !isConvertToRelative());
      }
    }
  }

  @Nonnull
  @Override
  public String getText() {
    return "Convert " + (isConvertToRelative() ? "absolute" : "relative") + " path to " + (isConvertToRelative() ? "relative" : "absolute");
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
