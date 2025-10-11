/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.imports;

import com.intellij.java.impl.ig.fixes.DeleteImportFix;
import com.intellij.java.language.psi.PsiImportList;
import com.intellij.java.language.psi.PsiImportStatement;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiJavaFile;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class SamePackageImportInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.importFromSamePackageDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.importFromSamePackageProblemDescriptor().get();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new DeleteImportFix();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new SamePackageImportVisitor();
    }

    private static class SamePackageImportVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitImportList(PsiImportList importList) {
            final PsiElement parent = importList.getParent();
            if (!(parent instanceof PsiJavaFile)) {
                return;
            }
     /* if (JspPsiUtil.isInJspFile(importList)) {
        return;
      } */
            final PsiJavaFile javaFile = (PsiJavaFile) parent;
            final String packageName = javaFile.getPackageName();
            final PsiImportStatement[] importStatements =
                importList.getImportStatements();
            for (final PsiImportStatement importStatement : importStatements) {
                final PsiJavaCodeReferenceElement reference =
                    importStatement.getImportReference();
                if (reference == null) {
                    continue;
                }
                final String text = importStatement.getQualifiedName();
                if (importStatement.isOnDemand()) {
                    if (packageName.equals(text)) {
                        registerError(importStatement);
                    }
                }
                else {
                    if (text == null) {
                        return;
                    }
                    final int classNameIndex = text.lastIndexOf((int) '.');
                    final String parentName;
                    if (classNameIndex < 0) {
                        parentName = "";
                    }
                    else {
                        parentName = text.substring(0, classNameIndex);
                    }
                    if (packageName.equals(parentName)) {
                        registerError(importStatement);
                    }
                }
            }
        }
    }
}
