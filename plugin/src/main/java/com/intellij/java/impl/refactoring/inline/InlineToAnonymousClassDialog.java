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
package com.intellij.java.impl.refactoring.inline;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.language.psi.PsiCall;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.application.HelpManager;
import consulo.language.editor.refactoring.inline.InlineOptionsWithSearchSettingsDialog;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;

import javax.annotation.Nonnull;

/**
 * @author yole
 */
public class InlineToAnonymousClassDialog extends InlineOptionsWithSearchSettingsDialog {
  private final PsiClass myClass;
  private final PsiCall myCallToInline;

  protected InlineToAnonymousClassDialog(Project project, PsiClass psiClass, final PsiCall callToInline, boolean isInvokeOnReference) {
    super(project, true, psiClass);
    myClass = psiClass;
    myCallToInline = callToInline;
    myInvokedOnReference = isInvokeOnReference;
    setTitle(RefactoringLocalize.inlineToAnonymousRefactoring());
    init();
  }

  @Nonnull
  @Override
  protected LocalizeValue getNameLabelText() {
    String className = PsiFormatUtil.formatClass(myClass, PsiFormatUtil.SHOW_NAME);
    return RefactoringLocalize.inlineToAnonymousNameLabel(className);
  }

  @Nonnull
  @Override
  protected LocalizeValue getBorderTitle() {
    return RefactoringLocalize.inlineToAnonymousBorderTitle();
  }

  @Nonnull
  @Override
  protected LocalizeValue getInlineAllText() {
    return RefactoringLocalize.allReferencesAndRemoveTheClass();
  }

  @Nonnull
  @Override
  protected LocalizeValue getInlineThisText() {
    return RefactoringLocalize.thisReferenceOnlyAndKeepTheClass();
  }

  @Override
  protected boolean isInlineThis() {
    return false;
  }

  @Override
  protected boolean isSearchInCommentsAndStrings() {
    return JavaRefactoringSettings.getInstance().INLINE_CLASS_SEARCH_IN_COMMENTS;
  }

  @Override
  protected boolean isSearchForTextOccurrences() {
    return JavaRefactoringSettings.getInstance().INLINE_CLASS_SEARCH_IN_NON_JAVA;
  }

  @Override
  protected void doAction() {
    super.doAction();
    invokeRefactoring(new InlineToAnonymousClassProcessor(getProject(), myClass, myCallToInline, isInlineThisOnly(),
        isSearchInCommentsAndStrings(), isSearchForTextOccurrences()));
  }

  @Override
  protected void saveSearchInCommentsAndStrings(boolean searchInComments) {
    JavaRefactoringSettings.getInstance().INLINE_CLASS_SEARCH_IN_COMMENTS = searchInComments;
  }

  @Override
  protected void saveSearchInTextOccurrences(boolean searchInTextOccurrences) {
    JavaRefactoringSettings.getInstance().INLINE_CLASS_SEARCH_IN_NON_JAVA = searchInTextOccurrences;
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INLINE_CLASS);
  }
}
