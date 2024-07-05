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
package com.intellij.java.impl.refactoring.move.moveInstanceMethod;

import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiVariable;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.ui.UsageViewDescriptorAdapter;

/**
 * @author dsl
 */
public class MoveInstanceMethodViewDescriptor extends UsageViewDescriptorAdapter {
  private final PsiMethod myMethod;
  private final PsiVariable myTargetVariable;
  private final PsiClass myTargetClass;

  public MoveInstanceMethodViewDescriptor(
    PsiMethod method,
    PsiVariable targetVariable,
    PsiClass targetClass) {
    super();
    myMethod = method;
    myTargetVariable = targetVariable;
    myTargetClass = targetClass;
  }

  @Nonnull
  public PsiElement[] getElements() {
    return new PsiElement[] {myMethod, myTargetVariable, myTargetClass};
  }

  public String getProcessedElementsHeader() {
    return RefactoringLocalize.moveInstanceMethodElementsHeader().get();
  }
}
