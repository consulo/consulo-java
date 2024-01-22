/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.classlayout;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.RemoveModifierFix;
import com.intellij.java.impl.ig.psiutils.SerializationUtils;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class PublicConstructorInNonPublicClassInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("public.constructor.in.non.public.class.display.name");
  }

  @jakarta.annotation.Nonnull
  public String buildErrorString(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[0];
    return InspectionGadgetsBundle.message("public.constructor.in.non.public.class.problem.descriptor",
      method.getName());
  }

  public BaseInspectionVisitor buildVisitor() {
    return new PublicConstructorInNonPublicClassVisitor();
  }

  @jakarta.annotation.Nonnull
  public InspectionGadgetsFix[] buildFixes(Object... infos) {
    final List<InspectionGadgetsFix> fixes = new ArrayList();
    final PsiMethod constructor = (PsiMethod)infos[0];
    final PsiClass aClass = constructor.getContainingClass();
    if (aClass != null && aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      fixes.add(new SetConstructorModifierFix(PsiModifier.PRIVATE));
    }
    fixes.add(new RemoveModifierFix(PsiModifier.PUBLIC));
    return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
  }

  private static class SetConstructorModifierFix extends InspectionGadgetsFix {

    @PsiModifier.ModifierConstant private final String modifier;

    SetConstructorModifierFix(@PsiModifier.ModifierConstant String modifier) {
      this.modifier = modifier;
    }

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "public.constructor.in.non.public.class.quickfix",
        modifier
      );
    }

    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiModifierList modifierList = (PsiModifierList)element.getParent();
      modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
      modifierList.setModifierProperty(modifier, true);
    }
  }

  private static class PublicConstructorInNonPublicClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      //no call to super, so we don't drill into anonymous classes
      if (!method.isConstructor()) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.hasModifierProperty(PsiModifier.PUBLIC) ||
        containingClass.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      if (SerializationUtils.isExternalizable(containingClass)) {
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() == 0) {
          return;
        }
      }
      registerModifierError(PsiModifier.PUBLIC, method, method);
    }
  }
}