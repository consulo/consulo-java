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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.fileEditor.FileEditorManager;
import consulo.fileEditor.history.IdeDocumentHistory;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

/**
 * @author Mike
 */
public class CreateClassFromUsageFix extends CreateClassFromUsageBaseFix implements SyntheticIntentionAction {

  public CreateClassFromUsageFix(PsiJavaCodeReferenceElement refElement, CreateClassKind kind) {
    super(kind, refElement);
  }

  @Override
  public LocalizeValue getText(String varName) {
    return JavaQuickFixLocalize.createClassFromUsageText(StringUtil.capitalize(myKind.getDescription()), varName);
  }

  @Override
  public void invoke(@Nonnull final Project project, Editor editor, PsiFile file) {
    final PsiJavaCodeReferenceElement element = getRefElement();
    assert element != null;
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
    String superClassName = getSuperClassName(element);
    final PsiClass aClass = CreateFromUsageUtils.createClass(element, myKind, superClassName);
    if (aClass == null) return;

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        @Override
        public void run() {
          PsiJavaCodeReferenceElement refElement = element;
          try {
            refElement = (PsiJavaCodeReferenceElement)refElement.bindToElement(aClass);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }

          IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

          OpenFileDescriptor descriptor = OpenFileDescriptorFactory.getInstance(refElement.getProject()).builder(aClass.getContainingFile().getVirtualFile()).offset(aClass.getTextOffset()).build();
          FileEditorManager.getInstance(aClass.getProject()).openTextEditor(descriptor, true);
        }
      }
    );
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
