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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.actions;

import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.SuppressionUtil;
import com.intellij.java.analysis.impl.codeInspection.JavaSuppressionUtil;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import com.intellij.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * User: anna
 * Date: May 13, 2005
 */
public class SuppressAllForClassFix extends SuppressFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.actions.AddNoInspectionAllForClassFix");

  public SuppressAllForClassFix() {
    super(SuppressionUtil.ALL);
  }

  @Override
  @Nullable
  public PsiDocCommentOwner getContainer(final PsiElement element) {
    PsiDocCommentOwner container = super.getContainer(element);
    if (container == null) {
      return null;
    }
    while (container != null) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(container, PsiClass.class);
      if (parentClass == null && container instanceof PsiClass) {
        return container;
      }
      container = parentClass;
    }
    return container;
  }

  @Override
  @Nonnull
  public String getText() {
    return InspectionsBundle.message("suppress.all.for.class");
  }

  @Override
  public void invoke(@Nonnull final Project project, @Nonnull final PsiElement element) throws IncorrectOperationException {
    final PsiDocCommentOwner container = getContainer(element);
    LOG.assertTrue(container != null);
    if (!FileModificationService.getInstance().preparePsiElementForWrite(container)) return;
    if (use15Suppressions(container)) {
      final PsiModifierList modifierList = container.getModifierList();
      if (modifierList != null) {
        final PsiAnnotation annotation = modifierList.findAnnotation(JavaSuppressionUtil.SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
        if (annotation != null) {
          annotation.replace(JavaPsiFacade.getInstance(project).getElementFactory().createAnnotationFromText("@" +
              JavaSuppressionUtil.SUPPRESS_INSPECTIONS_ANNOTATION_NAME + "(\"" +
              SuppressionUtil.ALL + "\")", container));
          return;
        }
      }
    } else {
      PsiDocComment docComment = container.getDocComment();
      if (docComment != null) {
        PsiDocTag noInspectionTag = docComment.findTagByName(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME);
        if (noInspectionTag != null) {
          String tagText = "@" + SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME + " " + SuppressionUtil.ALL;
          noInspectionTag.replace(JavaPsiFacade.getInstance(project).getElementFactory().createDocTagFromText(tagText));
          // todo suppress
          //DaemonCodeAnalyzer.getInstance(project).restart();
          return;
        }
      }
    }

    super.invoke(project, element);
  }
}
