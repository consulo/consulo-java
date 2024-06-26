/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.execution.impl;

import com.intellij.java.execution.impl.ui.ConfigurationModuleSelector;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.ui.awt.BrowseModuleValueActionListener;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.editor.ui.awt.TextFieldCompletionProvider;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.lang.function.Condition;

import jakarta.annotation.Nonnull;

public abstract class MethodBrowser extends BrowseModuleValueActionListener {
  public MethodBrowser(final Project project) {
    super(project);
  }

  protected abstract String getClassName();

  protected abstract ConfigurationModuleSelector getModuleSelector();

  protected abstract Condition<PsiMethod> getFilter(PsiClass testClass);

  protected String showDialog() {
    final String className = getClassName();
    if (className.trim().length() == 0) {
      Messages.showMessageDialog(
        getField(),
        ExecutionLocalize.setClassNameMessage().get(),
        ExecutionLocalize.cannotBrowseMethodDialogTitle().get(),
        UIUtil.getInformationIcon()
      );
      return null;
    }
    final PsiClass testClass = getModuleSelector().findClass(className);
    if (testClass == null) {
      Messages.showMessageDialog(
        getField(),
        ExecutionLocalize.classDoesNotExistsErrorMessage(className).get(),
        ExecutionLocalize.cannotBrowseMethodDialogTitle().get(),
        UIUtil.getInformationIcon()
      );
      return null;
    }
    final MethodListDlg dlg = new MethodListDlg(testClass, getFilter(testClass), getField());
    if (dlg.showAndGet()) {
      final PsiMethod method = dlg.getSelected();
      if (method != null) {
        return method.getName();
      }
    }
    return null;
  }

  public void installCompletion(EditorTextField field) {
    new TextFieldCompletionProvider() {
      @Override
      public void addCompletionVariants(@Nonnull String text, int offset, @Nonnull String prefix, @Nonnull CompletionResultSet result) {
        final String className = getClassName();
        if (className.trim().length() == 0) {
          return;
        }
        final PsiClass testClass = getModuleSelector().findClass(className);
        if (testClass == null) {
          return;
        }
        final Condition<PsiMethod> filter = getFilter(testClass);
        for (PsiMethod psiMethod : testClass.getAllMethods()) {
          if (filter.value(psiMethod)) {
            result.addElement(LookupElementBuilder.create(psiMethod.getName()));
          }
        }
      }
    }.apply(field);
  }
}
