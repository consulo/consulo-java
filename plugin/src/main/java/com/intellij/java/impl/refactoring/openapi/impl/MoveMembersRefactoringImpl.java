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
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.impl.refactoring.MoveMembersRefactoring;
import consulo.language.editor.refactoring.RefactoringImpl;
import com.intellij.java.impl.refactoring.move.moveMembers.MoveMembersOptions;
import com.intellij.java.impl.refactoring.move.moveMembers.MoveMembersProcessor;

import java.util.List;

/**
 * @author dsl
 */
public class MoveMembersRefactoringImpl extends RefactoringImpl<MoveMembersProcessor> implements MoveMembersRefactoring {
  MoveMembersRefactoringImpl(Project project, final PsiMember[] elements, final String targetClassQualifiedName, final String newVisibility, final boolean makeEnumConstants) {
    super(new MoveMembersProcessor(project, new MoveMembersOptions() {
      public PsiMember[] getSelectedMembers() {
        return elements;
      }

      public String getTargetClassName() {
        return targetClassQualifiedName;
      }

      public String getMemberVisibility() {
        return newVisibility;
      }

      public boolean makeEnumConstant() {
        return makeEnumConstants;
      }

    }));
  }

  public List<PsiElement> getMembers() {
    return myProcessor.getMembers();
  }

  public PsiClass getTargetClass() {
    return myProcessor.getTargetClass();
  }
}
