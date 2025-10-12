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
package com.intellij.java.impl.codeInspection.wrongPackageStatement;

import com.intellij.java.language.psi.*;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public class AdjustPackageNameFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance(AdjustPackageNameFix.class);
  private final String myName;

  public AdjustPackageNameFix(String targetPackage) {
    myName = targetPackage;
  }

  @Override
  @Nonnull
  public LocalizeValue getName() {
    return JavaQuickFixLocalize.adjustPackageText(myName);
  }

  @Nonnull
  public LocalizeValue getFamilyName() {
    return JavaQuickFixLocalize.adjustPackageFamily();
  }

  @Override
  public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element == null) return;
    PsiFile myFile = element.getContainingFile();
    if (!FileModificationService.getInstance().prepareFileForWrite(myFile)) return;

    PsiDirectory directory = myFile.getContainingDirectory();
    if (directory == null) return;
    PsiJavaPackage myTargetPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (myTargetPackage == null) return;

    try {
      PsiElementFactory factory = JavaPsiFacade.getInstance(myFile.getProject()).getElementFactory();
      PsiPackageStatement myStatement = ((PsiJavaFile)myFile).getPackageStatement();

      if (myTargetPackage.getQualifiedName().length() == 0) {
        if (myStatement != null) {
          myStatement.delete();
        }
      }
      else {
        final PsiPackageStatement packageStatement = factory.createPackageStatement(myTargetPackage.getQualifiedName());
        if (myStatement != null) {
          myStatement.getPackageReference().replace(packageStatement.getPackageReference());
        }
        else {
          myFile.addAfter(packageStatement, null);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
