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

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.impl.codeInspection.MoveToPackageFix;
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.util.lang.Comparing;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 14-Nov-2005
 */
@ExtensionImpl
public class WrongPackageStatementInspection extends BaseJavaLocalInspectionTool<Object> {
  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(@Nonnull PsiFile file, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
    if (file instanceof PsiJavaFile) {
      PsiJavaFile javaFile = (PsiJavaFile)file;

      PsiDirectory directory = javaFile.getContainingDirectory();
      if (directory == null) return null;
      PsiJavaPackage dirPackage = JavaDirectoryService.getInstance().getPackage(directory);
      if (dirPackage == null) return null;
      PsiPackageStatement packageStatement = javaFile.getPackageStatement();

      // highlight the first class in the file only
      PsiClass[] classes = javaFile.getClasses();
      if (classes.length == 0 && packageStatement == null || classes.length == 1 && classes[0] instanceof PsiSyntheticClass) return null;

      String packageName = dirPackage.getQualifiedName();
      if (!Comparing.strEqual(packageName, "", true) && packageStatement == null) {
        String description = JavaErrorBundle.message("missing.package.statement", packageName);

        return new ProblemDescriptor[]{manager.createProblemDescriptor(classes[0].getNameIdentifier(), description,
                                                                       new AdjustPackageNameFix(packageName),
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly)};
      }
      if (packageStatement != null) {
        final PsiJavaCodeReferenceElement packageReference = packageStatement.getPackageReference();
        PsiJavaPackage classPackage = (PsiJavaPackage)packageReference.resolve();
        List<LocalQuickFix> availableFixes = new ArrayList<LocalQuickFix>();
        if (classPackage == null || !Comparing.equal(dirPackage.getQualifiedName(), packageReference.getQualifiedName(), true)) {
          availableFixes.add(new AdjustPackageNameFix(packageName));
          MoveToPackageFix moveToPackageFix = new MoveToPackageFix(classPackage != null ? classPackage.getQualifiedName() : packageReference.getQualifiedName());
          if (moveToPackageFix.isAvailable(file)) {
            availableFixes.add(moveToPackageFix);
          }
        }
        if (!availableFixes.isEmpty()){
          String description = JavaErrorBundle.message("package.name.file.path.mismatch",
                                                         packageReference.getQualifiedName(),
                                                         dirPackage.getQualifiedName());
          LocalQuickFix[] fixes = availableFixes.toArray(new LocalQuickFix[availableFixes.size()]);
          ProblemDescriptor descriptor =
            manager.createProblemDescriptor(packageStatement.getPackageReference(), description, isOnTheFly,
                                            fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          return new ProblemDescriptor[]{descriptor};

        }
      }
    }
    return null;
  }

  @Override
  @Nonnull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionsBundle.message("wrong.package.statement");
  }

  @Override
  @Nonnull
  @NonNls
  public String getShortName() {
    return "WrongPackageStatement";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }
}
