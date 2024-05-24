// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.refactoring.introduceField;

import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JavaIntroduceFieldHandlerBase extends RefactoringActionHandler {
  void invoke(@NotNull Project project, PsiElement element, @Nullable Editor editor);
}
