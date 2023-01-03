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
package com.intellij.java.impl.refactoring.openapi.impl;

import consulo.project.Project;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiVariable;
import com.intellij.java.impl.refactoring.MoveInstanceMethodRefactoring;
import consulo.language.editor.refactoring.RefactoringImpl;
import com.intellij.java.impl.refactoring.move.moveInstanceMethod.MoveInstanceMethodHandler;
import com.intellij.java.impl.refactoring.move.moveInstanceMethod.MoveInstanceMethodProcessor;

/**
 * @author ven
 */
public class MoveInstanceMethodRefactoringImpl extends RefactoringImpl<MoveInstanceMethodProcessor> implements MoveInstanceMethodRefactoring {
  MoveInstanceMethodRefactoringImpl(Project project, PsiMethod method, PsiVariable targetVariable) {
    super(new MoveInstanceMethodProcessor(project, method, targetVariable, null, MoveInstanceMethodHandler.suggestParameterNames (method, targetVariable)));
  }

  public PsiMethod getMethod() {
    return myProcessor.getMethod();
  }

  public PsiVariable getTargetVariable() {
    return myProcessor.getTargetVariable();
  }

  public PsiClass getTargetClass() {
    return myProcessor.getTargetClass();
  }
}
