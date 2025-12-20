/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.fixes;

import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierList;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.psiutils.FinalUtils;
import jakarta.annotation.Nullable;

public class MakeFieldStaticFinalFix extends InspectionGadgetsFix {

  private final String fieldName;

  private MakeFieldStaticFinalFix(String fieldName) {
    this.fieldName = fieldName;
  }

  @Nonnull
  public static InspectionGadgetsFix buildFixUnconditional(
    @Nonnull PsiField field) {
    return new MakeFieldStaticFinalFix(field.getName());
  }

  @Nullable
  public static InspectionGadgetsFix buildFix(PsiField field) {
    PsiExpression initializer = field.getInitializer();
    if (initializer == null) {
      return null;
    }
    if (!FinalUtils.canBeFinal(field)) {
      return null;
    }
    return new MakeFieldStaticFinalFix(field.getName());
  }

  @Nonnull
  public LocalizeValue getName() {
    return InspectionGadgetsLocalize.makeStaticFinalQuickfix(fieldName);
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor)
    throws IncorrectOperationException {
    PsiElement element = descriptor.getPsiElement();
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiField)) {
      return;
    }
    PsiField field = (PsiField)parent;
    PsiModifierList modifierList = field.getModifierList();
    if (modifierList == null) {
      return;
    }
    modifierList.setModifierProperty(PsiModifier.FINAL, true);
    modifierList.setModifierProperty(PsiModifier.STATIC, true);
  }
}