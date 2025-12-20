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

/*
 * User: anna
 * Date: 08-Jul-2007
 */
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.language.codeInsight.ExternalAnnotationsManager;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListPopup;
import consulo.ui.ex.popup.PopupStep;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.DeannotateIntentionAction", categories = {"Java", "Control Flow"}, fileExtensions = "java")
public class DeannotateIntentionAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(DeannotateIntentionAction.class);
  private String myAnnotationName = null;

  @Override
  @Nonnull
  public LocalizeValue getText() {
    return LocalizeValue.join(CodeInsightLocalize.deannotateIntentionActionText(), LocalizeValue.of((myAnnotationName != null ? " " + myAnnotationName : "")));
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    PsiModifierListOwner listOwner = getContainer(editor, file);
    if (listOwner != null) {
      ExternalAnnotationsManager externalAnnotationsManager = ExternalAnnotationsManager.getInstance(project);
      PsiAnnotation[] annotations = externalAnnotationsManager.findExternalAnnotations(listOwner);
      if (annotations != null && annotations.length > 0) {
        if (annotations.length == 1) {
          myAnnotationName = annotations[0].getQualifiedName();
        }
        List<PsiFile> files = externalAnnotationsManager.findExternalAnnotationsFiles(listOwner);
        if (files == null || files.isEmpty()) return false;
        VirtualFile virtualFile = files.get(0).getVirtualFile();
        return virtualFile != null && (virtualFile.isWritable() || virtualFile.isInLocalFileSystem());
      }
    }
    return false;
  }

  @Nullable
  public static PsiModifierListOwner getContainer(Editor editor, PsiFile file) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(element, PsiParameter.class, false);
    if (listOwner == null) {
      PsiIdentifier psiIdentifier = PsiTreeUtil.getParentOfType(element, PsiIdentifier.class, false);
      if (psiIdentifier != null && psiIdentifier.getParent() instanceof PsiModifierListOwner modifierListOwner) {
        listOwner = modifierListOwner;
      } else {
        PsiExpression expression = PsiTreeUtil.getParentOfType(element, PsiExpression.class);
        if (expression != null) {
          while (expression.getParent() instanceof PsiExpression parentExpression) { //get top level expression
            expression = parentExpression;
            if (expression instanceof PsiAssignmentExpression) break;
          }
          if (expression instanceof PsiMethodCallExpression methodCallExpression) {
            PsiMethod psiMethod = methodCallExpression.resolveMethod();
            if (psiMethod != null) {
              return psiMethod;
            }
          }
          PsiElement parent = expression.getParent();
          if (parent instanceof PsiExpressionList expressionList) {  //try to find corresponding formal parameter
            int idx = -1;
            PsiExpression[] args = expressionList.getExpressions();
            for (int i = 0; i < args.length; i++) {
              PsiExpression arg = args[i];
              if (PsiTreeUtil.isAncestor(arg, expression, false)) {
                idx = i;
                break;
              }
            }

            if (idx > -1) {
              PsiElement grParent = parent.getParent();
              if (grParent instanceof PsiCall call) {
                PsiMethod method = call.resolveMethod();
                if (method != null) {
                  PsiParameter[] parameters = method.getParameterList().getParameters();
                  if (parameters.length > idx) {
                    return parameters[idx];
                  }
                }
              }
            }
          }
        }
      }
    }
    return listOwner;
  }

  @Override
  public void invoke(@Nonnull final Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiModifierListOwner listOwner = getContainer(editor, file);
    LOG.assertTrue(listOwner != null); 
    final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    final PsiAnnotation[] externalAnnotations = annotationsManager.findExternalAnnotations(listOwner);
    LOG.assertTrue(externalAnnotations != null && externalAnnotations.length > 0);
    if (externalAnnotations.length == 1) {
      deannotate(externalAnnotations[0], project, file, annotationsManager, listOwner);
      return;
    }
    ListPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<PsiAnnotation>(
      CodeInsightLocalize.deannotateIntentionChooserTitle().get(),
      externalAnnotations
    ) {
      @Override
      public PopupStep onChosen(PsiAnnotation selectedValue, boolean finalChoice) {
        deannotate(selectedValue, project, file, annotationsManager, listOwner);
        return PopupStep.FINAL_CHOICE;
      }

      @Override
      @Nonnull
      public String getTextFor(PsiAnnotation value) {
        String qualifiedName = value.getQualifiedName();
        LOG.assertTrue(qualifiedName != null);
        return qualifiedName;
      }
    });

    EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
  }

  private void deannotate(
    final PsiAnnotation annotation,
    final Project project,
    final PsiFile file,
    final ExternalAnnotationsManager annotationsManager,
    final PsiModifierListOwner listOwner
  ) {
    new WriteCommandAction(project, getText().get()) {
      @Override
      protected void run(Result result) throws Throwable {
        VirtualFile virtualFile = file.getVirtualFile();
        String qualifiedName = annotation.getQualifiedName();
        LOG.assertTrue(qualifiedName != null);
        if (annotationsManager.deannotate(listOwner, qualifiedName) && virtualFile != null && virtualFile.isInLocalFileSystem()) {
          LanguageUndoUtil.markPsiFileForUndo(file);
        }
      }
    }.execute();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}