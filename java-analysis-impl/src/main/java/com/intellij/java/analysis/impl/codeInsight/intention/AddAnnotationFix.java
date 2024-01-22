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

package com.intellij.java.analysis.impl.codeInsight.intention;

import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.PsiNameValuePair;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import jakarta.annotation.Nonnull;

/**
 * @author ven
 */
public class AddAnnotationFix extends AddAnnotationPsiFix implements SyntheticIntentionAction
{
  public AddAnnotationFix(@Nonnull String fqn, @Nonnull PsiModifierListOwner modifierListOwner, @Nonnull String... annotationsToRemove) {
    this(fqn, modifierListOwner, PsiNameValuePair.EMPTY_ARRAY, annotationsToRemove);
  }

  public AddAnnotationFix(@jakarta.annotation.Nonnull String fqn,
                          @Nonnull PsiModifierListOwner modifierListOwner,
                          @Nonnull PsiNameValuePair[] values,
                          @jakarta.annotation.Nonnull String... annotationsToRemove) {
    super(fqn, modifierListOwner, values, annotationsToRemove);
  }

  @Override
  public boolean isAvailable(@jakarta.annotation.Nonnull Project project, Editor editor, PsiFile file) {
    return isAvailable();
  }

  @Override
  public void invoke(@jakarta.annotation.Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    applyFix();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
