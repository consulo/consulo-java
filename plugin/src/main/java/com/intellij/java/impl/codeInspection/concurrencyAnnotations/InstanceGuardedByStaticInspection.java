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
package com.intellij.java.impl.codeInspection.concurrencyAnnotations;

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.analysis.impl.codeInspection.concurrencyAnnotations.JCiPUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class InstanceGuardedByStaticInspection extends BaseJavaLocalInspectionTool {

  @Override
  @Nonnull
  public LocalizeValue getGroupDisplayName() {
    return InspectionLocalize.groupNamesConcurrencyAnnotationIssues();
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return LocalizeValue.localizeTODO("Instance member guarded by static field");
  }

  @Override
  @Nonnull
  public String getShortName() {
    return "InstanceGuardedByStatic";
  }

  @Override
  @Nonnull
  public PsiElementVisitor buildVisitorImpl(@Nonnull ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            Object state) {
    return new Visitor(holder);
  }

  private static class Visitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    public Visitor(ProblemsHolder holder) {
      myHolder = holder;
    }


    @Override
    public void visitDocTag(PsiDocTag psiDocTag) {
      super.visitDocTag(psiDocTag);
      if (!JCiPUtil.isGuardedByTag(psiDocTag)) {
        return;
      }
      final PsiMember member = PsiTreeUtil.getParentOfType(psiDocTag, PsiMember.class);
      if (member == null) {
        return;
      }
      if (member.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final String guardValue = JCiPUtil.getGuardValue(psiDocTag);

      final PsiClass containingClass = PsiTreeUtil.getParentOfType(psiDocTag, PsiClass.class);
      if (containingClass == null) {
        return;
      }
      final PsiField guardField = containingClass.findFieldByName(guardValue, true);
      if (guardField == null) {
        return;
      }
      if (!guardField.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      myHolder.registerProblem(psiDocTag, "Instance member guarded by static \"" + guardValue + "\" #loc");
    }

    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
      super.visitAnnotation(annotation);
      if (!JCiPUtil.isGuardedByAnnotation(annotation)) {
        return;
      }
      final PsiMember member = PsiTreeUtil.getParentOfType(annotation, PsiMember.class);
      if (member == null) {
        return;
      }
      if (member.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final String guardValue = JCiPUtil.getGuardValue(annotation);
      if (guardValue == null) {
        return;
      }

      final PsiAnnotationMemberValue guardRef = annotation.findAttributeValue("value");
      if (guardRef == null) {
        return;
      }
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(annotation, PsiClass.class);
      if (containingClass == null) {
        return;
      }
      final PsiField guardField = containingClass.findFieldByName(guardValue, true);
      if (guardField == null) {
        return;
      }
      if (!guardField.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      myHolder.registerProblem(guardRef, "Instance member guarded by static #ref #loc");
    }
  }
}
