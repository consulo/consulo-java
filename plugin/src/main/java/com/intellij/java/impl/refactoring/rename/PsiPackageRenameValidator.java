/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.rename;

import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiNameHelper;
import consulo.language.file.FileTypeManager;
import consulo.project.Project;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.rename.RenameInputValidatorEx;
import consulo.language.util.ProcessingContext;

import javax.annotation.Nullable;

/**
 * User: anna
 * Date: 3/14/11
 */
public class PsiPackageRenameValidator implements RenameInputValidatorEx {
  @Nullable
  @Override
  public String getErrorMessage(String newName, Project project) {
    if (FileTypeManager.getInstance().isFileIgnored(newName)) {
      return "Trying to create a package with ignored name, result will not be visible";
    }
    if (newName.length() > 0) {
      if (!PsiNameHelper.getInstance(project).isQualifiedName(newName)) {
        return "Not a valid package name";
      }
      if (!PsiNameHelper.getInstance(project).isIdentifier(newName)) {
        return "Not a valid identifier name";
      }
    }
    return null;
  }

  @Override
  public ElementPattern<? extends PsiElement> getPattern() {
    return PlatformPatterns.psiElement(PsiJavaPackage.class);
  }

  @Override
  public boolean isInputValid(String newName, PsiElement element, ProcessingContext context) {
    return newName != null && newName.length() > 0;
  }
}
