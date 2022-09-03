/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.inspection.AbstractBatchSuppressByNoInspectionCommentFix;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.SuppressionUtil;
import com.intellij.java.analysis.impl.codeInspection.JavaSuppressionUtil;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.undoRedo.util.UndoUtil;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import com.intellij.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author ven
 */
public class SuppressFix extends AbstractBatchSuppressByNoInspectionCommentFix {

  public SuppressFix(@Nonnull HighlightDisplayKey key) {
    this(key.getID());
  }

  public SuppressFix(@Nonnull String ID) {
    super(ID, false);
  }

  @Override
  @Nonnull
  public String getText() {
    String myText = super.getText();
    return StringUtil.isEmpty(myText) ? "Suppress for member" : myText;
  }

  @Override
  @Nullable
  public PsiDocCommentOwner getContainer(final PsiElement context) {
    if (context == null || !context.getManager().isInProject(context)) {
      return null;
    }
    final PsiFile containingFile = context.getContainingFile();
    if (containingFile == null) {
      // for PsiDirectory
      return null;
    }
    if (!containingFile.getLanguage().isKindOf(JavaLanguage.INSTANCE) || context instanceof PsiFile) {
      return null;
    }
    PsiElement container = context;
    while (container instanceof PsiAnonymousClass || !(container instanceof PsiDocCommentOwner) || container instanceof PsiTypeParameter) {
      container = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class);
      if (container == null) {
        return null;
      }
    }
    return (PsiDocCommentOwner) container;
  }

  @Override
  public boolean isAvailable(@Nonnull final Project project, @Nonnull final PsiElement context) {
    PsiDocCommentOwner container = getContainer(context);
    boolean isValid = container != null && !(container instanceof PsiMethod && container instanceof SyntheticElement);
    if (!isValid) {
      return false;
    }
    setText(container instanceof PsiClass ? InspectionsBundle.message("suppress.inspection.class") : container instanceof PsiMethod ?
        InspectionsBundle.message("suppress.inspection.method") : InspectionsBundle.message("suppress.inspection.field"));
    return true;
  }

  @Override
  public void invoke(@Nonnull final Project project, @Nonnull final PsiElement element) throws IncorrectOperationException {
    if (doSuppress(project, getContainer(element))) {
      return;
    }
    // todo suppress
    //DaemonCodeAnalyzer.getInstance(project).restart();
    UndoUtil.markPsiFileForUndo(element.getContainingFile());
  }

  private boolean doSuppress(@Nonnull Project project, PsiDocCommentOwner container) {
    assert container != null;
    if (!FileModificationService.getInstance().preparePsiElementForWrite(container)) {
      return true;
    }
    if (use15Suppressions(container)) {
      final PsiModifierList modifierList = container.getModifierList();
      if (modifierList != null) {
        JavaSuppressionUtil.addSuppressAnnotation(project, container, container, myID);
      }
    } else {
      PsiDocComment docComment = container.getDocComment();
      PsiManager manager = PsiManager.getInstance(project);
      if (docComment == null) {
        String commentText = "/** @" + SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME + " " + myID + "*/";
        docComment = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createDocCommentFromText(commentText);
        PsiElement firstChild = container.getFirstChild();
        container.addBefore(docComment, firstChild);
      } else {
        PsiDocTag noInspectionTag = docComment.findTagByName(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME);
        if (noInspectionTag != null) {
          String tagText = noInspectionTag.getText() + ", " + myID;
          noInspectionTag.replace(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createDocTagFromText(tagText));
        } else {
          String tagText = "@" + SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME + " " + myID;
          docComment.add(JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createDocTagFromText(tagText));
        }
      }
    }
    return false;
  }

  protected boolean use15Suppressions(@Nonnull PsiDocCommentOwner container) {
    return JavaSuppressionUtil.canHave15Suppressions(container) && !JavaSuppressionUtil.alreadyHas14Suppressions(container);
  }
}
