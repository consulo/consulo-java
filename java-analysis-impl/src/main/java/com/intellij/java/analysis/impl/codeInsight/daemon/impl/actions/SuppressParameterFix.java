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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.actions;

import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.inspection.AbstractBatchSuppressByNoInspectionCommentFix;
import com.intellij.java.analysis.impl.codeInspection.JavaSuppressionUtil;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.PsiParameter;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author ven
 */
public class SuppressParameterFix extends AbstractBatchSuppressByNoInspectionCommentFix {
  public SuppressParameterFix(@Nonnull HighlightDisplayKey key) {
    this(key.getID());
  }

  public SuppressParameterFix(String ID) {
    super(ID, false);
  }

  @Override
  @Nonnull
  public String getText() {
    return "Suppress for parameter";
  }

  @Nullable
  @Override
  public PsiElement getContainer(PsiElement context) {
    PsiParameter psiParameter = PsiTreeUtil.getParentOfType(context, PsiParameter.class, false);
    return psiParameter != null && JavaSuppressionUtil.canHave15Suppressions(psiParameter) ? psiParameter : null;
  }

  @Override
  protected boolean replaceSuppressionComments(PsiElement container) {
    return false;
  }

  @Override
  protected void createSuppression(@jakarta.annotation.Nonnull Project project, @Nonnull PsiElement element,
                                   @jakarta.annotation.Nonnull PsiElement cont) throws IncorrectOperationException {
    PsiModifierListOwner container = (PsiModifierListOwner) cont;
    final PsiModifierList modifierList = container.getModifierList();
    if (modifierList != null) {
      JavaSuppressionUtil.addSuppressAnnotation(project, container, container, myID);
    }
  }
}
