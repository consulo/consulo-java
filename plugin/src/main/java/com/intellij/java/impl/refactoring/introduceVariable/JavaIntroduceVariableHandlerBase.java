// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.refactoring.introduceVariable;

import com.intellij.java.language.psi.PsiExpression;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public interface JavaIntroduceVariableHandlerBase extends RefactoringActionHandler {
  void invoke(@Nonnull Project project, Editor editor, PsiExpression expression);
}
