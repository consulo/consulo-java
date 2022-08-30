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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.util.IncorrectOperationException;

import javax.annotation.Nonnull;

/**
 * @author ven
 */
public class AddAnnotationFix extends AddAnnotationPsiFix implements IntentionAction
{
  public AddAnnotationFix(@Nonnull String fqn, @Nonnull PsiModifierListOwner modifierListOwner, @Nonnull String... annotationsToRemove) {
    this(fqn, modifierListOwner, PsiNameValuePair.EMPTY_ARRAY, annotationsToRemove);
  }

  public AddAnnotationFix(@Nonnull String fqn,
                          @Nonnull PsiModifierListOwner modifierListOwner,
                          @Nonnull PsiNameValuePair[] values,
                          @Nonnull String... annotationsToRemove) {
    super(fqn, modifierListOwner, values, annotationsToRemove);
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return isAvailable();
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    applyFix();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
