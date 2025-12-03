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
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.impl.localize.JavaErrorLocalize;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author anna
 * @since 2005-11-14
 */
@ExtensionImpl
public class WrongPackageStatementInspection extends BaseJavaLocalInspectionTool<Object> {
    @Override
    @Nullable
    @RequiredReadAction
    public ProblemDescriptor[] checkFile(@Nonnull PsiFile file, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
        if (file instanceof PsiJavaFile javaFile) {
            PsiDirectory directory = javaFile.getContainingDirectory();
            if (directory == null) {
                return null;
            }
            PsiJavaPackage dirPackage = JavaDirectoryService.getInstance().getPackage(directory);
            if (dirPackage == null) {
                return null;
            }
            PsiPackageStatement packageStatement = javaFile.getPackageStatement();

            // highlight the first class in the file only
            PsiClass[] classes = javaFile.getClasses();
            if (classes.length == 0 && packageStatement == null || classes.length == 1 && classes[0] instanceof PsiSyntheticClass) {
                return null;
            }

            String packageName = dirPackage.getQualifiedName();
            if (!Comparing.strEqual(packageName, "", true) && packageStatement == null) {

                return new ProblemDescriptor[]{
                    manager.newProblemDescriptor(JavaErrorLocalize.missingPackageStatement(packageName))
                        .range(classes[0].getNameIdentifier())
                        .onTheFly(isOnTheFly)
                        .withFix(new AdjustPackageNameFix(packageName))
                        .create()
                };
            }
            if (packageStatement != null) {
                PsiJavaCodeReferenceElement packageReference = packageStatement.getPackageReference();
                PsiJavaPackage classPackage = (PsiJavaPackage) packageReference.resolve();
                List<LocalQuickFix> availableFixes = new ArrayList<>();
                if (classPackage == null || !Comparing.equal(dirPackage.getQualifiedName(), packageReference.getQualifiedName(), true)) {
                    availableFixes.add(new AdjustPackageNameFix(packageName));
                    MoveToPackageFix moveToPackageFix =
                        new MoveToPackageFix(classPackage != null ? classPackage.getQualifiedName() : packageReference.getQualifiedName());
                    if (moveToPackageFix.isAvailable(file)) {
                        availableFixes.add(moveToPackageFix);
                    }
                }
                if (!availableFixes.isEmpty()) {
                    ProblemDescriptor descriptor = manager.newProblemDescriptor(
                            JavaErrorLocalize.packageNameFilePathMismatch(packageReference.getQualifiedName(), dirPackage.getQualifiedName())
                        )
                        .range(packageStatement.getPackageReference())
                        .onTheFly(isOnTheFly)
                        .withFixes(availableFixes)
                        .create();
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

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.wrongPackageStatement();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return "WrongPackageStatement";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }
}
