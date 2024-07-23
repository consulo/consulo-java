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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierList;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class UnnecessaryEnumModifierInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.unnecessaryEnumModifierDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiElement parent = (PsiElement)infos[1];
    return parent instanceof PsiMethod
      ? InspectionGadgetsLocalize.unnecessaryEnumModifierProblemDescriptor().get()
      : InspectionGadgetsLocalize.unnecessaryEnumModifierProblemDescriptor1().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryEnumModifierVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryEnumModifierFix((PsiElement)infos[0]);
  }

  private static class UnnecessaryEnumModifierFix extends InspectionGadgetsFix {

    private final String m_name;

    private UnnecessaryEnumModifierFix(PsiElement modifier) {
      m_name = InspectionGadgetsLocalize.smthUnnecessaryRemoveQuickfix(modifier.getText()).get();
    }

    @Override
    @Nonnull
    public String getName() {
      return m_name;
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiModifierList modifierList;
      if (element instanceof PsiModifierList) {
        modifierList = (PsiModifierList)element;
      }
      else {
        modifierList = (PsiModifierList)element.getParent();
      }
      assert modifierList != null;
      if (modifierList.getParent() instanceof PsiClass) {
        modifierList.setModifierProperty(PsiModifier.STATIC, false);
      }
      else {
        modifierList.setModifierProperty(PsiModifier.PRIVATE, false);
      }
    }
  }

  private static class UnnecessaryEnumModifierVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      if (!aClass.isEnum() || !ClassUtils.isInnerClass(aClass) || !aClass.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiModifierList modifiers = aClass.getModifierList();
      if (modifiers == null) {
        return;
      }
      final PsiElement[] children = modifiers.getChildren();
      for (final PsiElement child : children) {
        final String text = child.getText();
        if (PsiModifier.STATIC.equals(text)) {
          registerError(child, child, aClass);
        }
      }
    }

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      if (!method.isConstructor() || !method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null || !aClass.isEnum()) {
        return;
      }
      final PsiModifierList modifiers = method.getModifierList();
      final PsiElement[] children = modifiers.getChildren();
      for (final PsiElement child : children) {
        final String text = child.getText();
        if (PsiModifier.PRIVATE.equals(text)) {
          registerError(child, child, method);
        }
      }
    }
  }
}