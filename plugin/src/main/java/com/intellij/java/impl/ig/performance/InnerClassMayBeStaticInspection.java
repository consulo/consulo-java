/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.content.scope.SearchScope;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.Collection;

@ExtensionImpl
public class InnerClassMayBeStaticInspection extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.innerClassMayBeStaticDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.innerClassMayBeStaticProblemDescriptor().get();
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new InnerClassMayBeStaticFix();
  }

  private static class InnerClassMayBeStaticFix extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.makeStaticQuickfix();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      PsiJavaToken classNameToken = (PsiJavaToken)descriptor.getPsiElement();
      PsiClass innerClass = (PsiClass)classNameToken.getParent();
      assert innerClass != null;
      SearchScope useScope = innerClass.getUseScope();
      Query<PsiReference> query = ReferencesSearch.search(innerClass, useScope);
      Collection<PsiReference> references = query.findAll();
      for (PsiReference reference : references) {
        PsiElement element = reference.getElement();
        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiNewExpression)) {
          continue;
        }
        PsiNewExpression newExpression = (PsiNewExpression)parent;
        PsiExpression qualifier = newExpression.getQualifier();
        if (qualifier == null) {
          continue;
        }
        qualifier.delete();
      }
      PsiModifierList modifiers = innerClass.getModifierList();
      if (modifiers == null) {
        return;
      }
      modifiers.setModifierProperty(PsiModifier.STATIC, true);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InnerClassMayBeStaticVisitor();
  }

  private static class InnerClassMayBeStaticVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (aClass.getContainingClass() != null &&
          !aClass.hasModifierProperty(PsiModifier.STATIC)) {
        // inner class cannot have static declarations
        return;
      }
      if (aClass instanceof PsiAnonymousClass) {
        return;
      }
      PsiClass[] innerClasses = aClass.getInnerClasses();
      for (PsiClass innerClass : innerClasses) {
        if (innerClass.hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        InnerClassReferenceVisitor visitor =
          new InnerClassReferenceVisitor(innerClass);
        innerClass.accept(visitor);
        if (!visitor.canInnerClassBeStatic()) {
          continue;
        }
        registerClassError(innerClass);
      }
    }
  }
}