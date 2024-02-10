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
package com.intellij.java.impl.codeInsight.navigation;

import com.intellij.java.impl.ide.util.MethodCellRenderer;
import com.intellij.java.language.impl.psi.impl.FindSuperElementsHelper;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.fileEditor.FileEditorManager;
import consulo.ide.impl.idea.codeInsight.navigation.actions.GotoSuperAction;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.action.GotoSuperActionHander;
import consulo.language.editor.ui.PopupNavigationUtil;
import consulo.language.editor.ui.PsiElementListNavigator;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.popup.JBPopup;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

public abstract class BaseJavaGotoSuperHandler implements GotoSuperActionHander {
  @RequiredUIAccess
  @Override
  public void invoke(@Nonnull final Project project, @Nonnull final Editor editor, @Nonnull final PsiFile file) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(GotoSuperAction.FEATURE_ID);

    int offset = editor.getCaretModel().getOffset();
    PsiElement[] superElements = findSuperElements(file, offset);
    if (superElements == null || superElements.length == 0) return;
    if (superElements.length == 1) {
      PsiElement superElement = superElements[0].getNavigationElement();
      final PsiFile containingFile = superElement.getContainingFile();
      if (containingFile == null) return;
      final VirtualFile virtualFile = containingFile.getVirtualFile();
      if (virtualFile == null) return;
      OpenFileDescriptor descriptor =
        OpenFileDescriptorFactory.getInstance(project).builder(virtualFile).offset(superElement.getTextOffset()).build();
      FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }
    else {
      if (superElements[0] instanceof PsiMethod) {
        boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature((PsiMethod[])superElements);
        PsiElementListNavigator.openTargets(editor, (PsiMethod[])superElements,
                                            CodeInsightBundle.message("goto.super.method.chooser.title"),
                                            CodeInsightBundle.message("goto.super.method.findUsages.title",
                                                                      ((PsiMethod)superElements[0]).getName()),
                                            new MethodCellRenderer(showMethodNames));
      }
      else {
        JBPopup popup = PopupNavigationUtil.getPsiElementPopup(superElements, CodeInsightBundle.message("goto.super.class.chooser.title"));
        EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
      }
    }
  }

  @Nullable
  private PsiElement[] findSuperElements(PsiFile file, int offset) {
    PsiNameIdentifierOwner parent = getElement(file, offset);
    if (parent == null) return null;

    return FindSuperElementsHelper.findSuperElements(parent);
  }

  protected PsiNameIdentifierOwner getElement(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;

    PsiNameIdentifierOwner parent = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiClass.class);
    if (parent == null)
      return null;
    return parent;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean isValidFor(Editor editor, PsiFile psiFile) {
    return true;
  }
}
