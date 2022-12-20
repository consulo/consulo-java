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
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiFile;
import consulo.project.Project;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author cdr
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.MoveInitializerToConstructorAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class MoveInitializerToConstructorAction extends BaseMoveInitializerToMethodAction {
  @Override
  @Nonnull
  public String getFamilyName() {
    return getText();
  }

  @Override
  @Nonnull
  public String getText() {
    return CodeInsightBundle.message("intention.move.initializer.to.constructor");
  }

  @Nonnull
  @Override
  protected Collection<String> getUnsuitableModifiers() {
    return Arrays.asList(PsiModifier.STATIC);
  }

  @Nonnull
  @Override
  protected Collection<PsiMethod> getOrCreateMethods(@Nonnull Project project, @Nonnull Editor editor, PsiFile file, @Nonnull PsiClass aClass) {
    final Collection<PsiMethod> constructors = Arrays.asList(aClass.getConstructors());
    if (constructors.isEmpty()) {
      return createConstructor(project, editor, file, aClass);
    }

    return removeChainedConstructors(constructors);
  }

  @Nonnull
  private static Collection<PsiMethod> removeChainedConstructors(@Nonnull Collection<PsiMethod> constructors) {
    constructors.removeIf(constructor -> !JavaHighlightUtil.getChainedConstructors(constructor).isEmpty());
    return constructors;
  }

  @Nonnull
  private static Collection<PsiMethod> createConstructor(@Nonnull Project project,
                                                         @Nonnull Editor editor,
                                                         PsiFile file,
                                                         @Nonnull PsiClass aClass) {
    final IntentionAction addDefaultConstructorFix = QuickFixFactory.getInstance().createAddDefaultConstructorFix(aClass);
    final int offset = editor.getCaretModel().getOffset();
    addDefaultConstructorFix.invoke(project, editor, file);
    editor.getCaretModel().moveToOffset(offset); //restore caret
    return Arrays.asList(aClass.getConstructors());
  }
}