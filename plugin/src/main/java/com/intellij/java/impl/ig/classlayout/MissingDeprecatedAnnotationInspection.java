/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class MissingDeprecatedAnnotationInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.missingDeprecatedAnnotationDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.missingDeprecatedAnnotationProblemDescriptor().get();
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new MissingDeprecatedAnnotationFix();
  }

  private static class MissingDeprecatedAnnotationFix extends InspectionGadgetsFix {
    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.missingDeprecatedAnnotationAddQuickfix().get();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement identifier = descriptor.getPsiElement();
      final PsiModifierListOwner parent = (PsiModifierListOwner)identifier.getParent();
      if (parent == null) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiAnnotation annotation = factory.createAnnotationFromText("@java.lang.Deprecated", parent);
      final PsiModifierList modifierList = parent.getModifierList();
      if (modifierList == null) {
        return;
      }
      modifierList.addAfter(annotation, null);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MissingDeprecatedAnnotationVisitor();
  }

  private static class MissingDeprecatedAnnotationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      super.visitClass(aClass);
      if (!PsiUtil.isLanguageLevel5OrHigher(aClass)) {
        return;
      }
      if (!hasDeprecatedComment(aClass) || hasDeprecatedAnnotation(aClass)) {
        return;
      }
      registerClassError(aClass);
    }

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      if (!PsiUtil.isLanguageLevel5OrHigher(method)) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      if (!hasDeprecatedComment(method) || hasDeprecatedAnnotation(method)) {
        return;
      }
      registerMethodError(method);
    }

    @Override
    public void visitField(@Nonnull PsiField field) {
      if (!PsiUtil.isLanguageLevel5OrHigher(field)) {
        return;
      }
      if (!hasDeprecatedComment(field) || hasDeprecatedAnnotation(field)) {
        return;
      }
      registerFieldError(field);
    }

    private static boolean hasDeprecatedAnnotation(PsiModifierListOwner element) {
      final PsiModifierList modifierList = element.getModifierList();
      if (modifierList == null) {
        return false;
      }
      final PsiAnnotation annotation = modifierList.findAnnotation(CommonClassNames.JAVA_LANG_DEPRECATED);
      return annotation != null;
    }

    private static boolean hasDeprecatedComment(PsiDocCommentOwner element) {
      final PsiDocComment comment = element.getDocComment();
      if (comment == null) {
        return false;
      }
      final PsiDocTag deprecatedTag = comment.findTagByName("deprecated");
      return deprecatedTag != null;
    }
  }
}