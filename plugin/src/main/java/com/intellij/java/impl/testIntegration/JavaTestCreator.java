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
package com.intellij.java.impl.testIntegration;

import com.intellij.java.impl.testIntegration.createTest.CreateTestAction;
import com.intellij.java.language.JavaLanguage;
import consulo.codeEditor.Editor;
import consulo.language.Language;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.editor.testIntegration.TestCreator;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

import javax.annotation.Nonnull;

public class JavaTestCreator implements TestCreator {
  private static final Logger LOG = Logger.getInstance(JavaTestCreator.class);


  @Override
  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    CreateTestAction action = new CreateTestAction();
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());

    return action.isAvailableForElement(element);
  }

  public void createTest(Project project, Editor editor, PsiFile file) {
    try {
      CreateTestAction action = new CreateTestAction();
      PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      if (action.isAvailableForElement(element)) {
        action.invoke(project, editor, file.getContainingFile());
      }
    } catch (IncorrectOperationException e) {
      LOG.warn(e);
    }
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
