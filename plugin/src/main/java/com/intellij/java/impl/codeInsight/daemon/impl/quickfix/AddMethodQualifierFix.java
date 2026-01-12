/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListPopup;
import consulo.ui.ex.popup.PopupStep;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class AddMethodQualifierFix implements SyntheticIntentionAction {
  private final SmartPsiElementPointer<PsiMethodCallExpression> myMethodCall;
  private List<PsiVariable> myCandidates = null;

  public AddMethodQualifierFix(PsiMethodCallExpression methodCallExpression) {
    myMethodCall = SmartPointerManager.getInstance(methodCallExpression.getProject()).createSmartPsiElementPointer(methodCallExpression);
  }

  @Nonnull
  @Override
  @RequiredReadAction
  public LocalizeValue getText() {
    List<PsiVariable> candidates = getOrFindCandidates();
    if (candidates.isEmpty()) {
      return JavaQuickFixLocalize.addMethodQualifierFixFamily();
    }
    LocalizeValue text = JavaQuickFixLocalize.addMethodQualifierFixText(candidates.size() > 1 ? "" : candidates.get(0).getName());
    if (candidates.size() > 1) {
      text = text.map(s -> s + "...");
    }
    return text;
  }

  @Override
  @RequiredReadAction
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    PsiMethodCallExpression element = myMethodCall.getElement();
    if (element == null || !element.isValid()) {
      return false;
    }
    return getOrFindCandidates().size() != 0;
  }

  @RequiredReadAction
  private synchronized List<PsiVariable> getOrFindCandidates() {
    if (myCandidates == null) {
      findCandidates();
    }
    return myCandidates;
  }

  @RequiredReadAction
  private void findCandidates() {
    myCandidates = new ArrayList<>();
    PsiMethodCallExpression methodCallElement = myMethodCall.getElement();
    String methodName = methodCallElement.getMethodExpression().getReferenceName();
    if (methodName == null) {
      return;
    }

    for (PsiVariable var : CreateFromUsageUtils.guessMatchingVariables(methodCallElement)) {
      if (var.getName() == null) {
        continue;
      }
      PsiType type = var.getType();
      if (!(type instanceof PsiClassType)) {
        continue;
      }
      PsiClass resolvedClass = ((PsiClassType)type).resolve();
      if (resolvedClass == null) {
        continue;
      }
      if (resolvedClass.findMethodsByName(methodName, true).length > 0) {
        myCandidates.add(var);
      }
    }
  }

  @TestOnly
  @RequiredReadAction
  public List<PsiVariable> getCandidates() {
    return getOrFindCandidates();
  }

  @Override
  @RequiredWriteAction
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(file)) {
      return;
    }
    List<PsiVariable> candidates = getOrFindCandidates();
    if (candidates.size() == 1) {
      qualify(candidates.get(0), editor);
    }
    else {
      chooseAndQualify(editor);
    }
  }

  private void chooseAndQualify(final Editor editor) {
    BaseListPopupStep<PsiVariable> step =
      new BaseListPopupStep<PsiVariable>(JavaQuickFixLocalize.addQualifier().get(), myCandidates) {
        @Override
        public PopupStep onChosen(final PsiVariable selectedValue, boolean finalChoice) {
          if (selectedValue != null && finalChoice) {
            WriteCommandAction.runWriteCommandAction(selectedValue.getProject(), new Runnable() {
              @Override
              public void run() {
                qualify(selectedValue, editor);
              }
            });
          }
          return FINAL_CHOICE;
        }

        @Nonnull
        @Override
        public String getTextFor(PsiVariable value) {
          return value.getName();
        }

        @Override
        public Image getIconFor(PsiVariable aValue) {
          return IconDescriptorUpdaters.getIcon(aValue, 0);
        }
      };

    ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
    EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
  }

  @RequiredWriteAction
  private void qualify(PsiVariable qualifier, Editor editor) {
    String qualifierPresentableText = qualifier.getName();
    PsiMethodCallExpression oldExpression = myMethodCall.getElement();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(qualifier.getProject());
    PsiExpression expression =
      elementFactory.createExpressionFromText(qualifierPresentableText + "." + oldExpression.getMethodExpression()
                                                                                            .getReferenceName() + "()", null);
    PsiElement replacedExpression = oldExpression.replace(expression);
    editor.getCaretModel().moveToOffset(replacedExpression.getTextOffset() + replacedExpression.getTextLength());
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}