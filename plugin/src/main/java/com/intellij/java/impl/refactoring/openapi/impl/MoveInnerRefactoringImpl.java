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
import consulo.language.psi.PsiElement;
import com.intellij.java.impl.refactoring.MoveInnerRefactoring;
import consulo.language.editor.refactoring.RefactoringImpl;
import com.intellij.java.impl.refactoring.move.moveInner.MoveInnerProcessor;

/**
 * @author dsl
 */
public class MoveInnerRefactoringImpl extends RefactoringImpl<MoveInnerProcessor> implements MoveInnerRefactoring {
  public MoveInnerRefactoringImpl(Project project,
                                  PsiClass innerClass,
                                  String name,
                                  boolean passOuterClass,
                                  String parameterName,
                                  final PsiElement targetContainer) {
    super(new MoveInnerProcessor(project, innerClass, name, passOuterClass, parameterName, targetContainer));
  }

  public PsiClass getInnerClass() {
    return myProcessor.getInnerClass();
  }

  public String getNewClassName() {
    return myProcessor.getNewClassName();
  }

  public boolean shouldPassParameter() {
    return myProcessor.shouldPassParameter();
  }

  public String getParameterName() {
    return myProcessor.getParameterName();
  }

  public void setSearchInComments(boolean value) {
    myProcessor.setSearchInComments(value);
  }

  public void setSearchInNonJavaFiles(boolean value) {
    myProcessor.setSearchInNonJavaFiles(value);
  }

  public boolean isSearchInComments() {
    return myProcessor.isSearchInComments();
  }

  public boolean isSearchInNonJavaFiles() {
    return myProcessor.isSearchInNonJavaFiles();
  }
}
