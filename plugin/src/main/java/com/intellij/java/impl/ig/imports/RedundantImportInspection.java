/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
import com.intellij.java.impl.ig.psiutils.ImportUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class RedundantImportInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.redundantImportDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.redundantImportProblemDescriptor().get();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new DeleteImportFix();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new RedundantImportVisitor();
    }

    private static class RedundantImportVisitor extends BaseInspectionVisitor {

        @Override
        public void visitFile(PsiFile file) {
            super.visitFile(file);
            if (!(file instanceof PsiJavaFile)) {
                return;
            }
            PsiJavaFile javaFile = (PsiJavaFile) file;
     /* if (JspPsiUtil.isInJspFile(file)) {
        return;
      }*/
            PsiImportList importList = javaFile.getImportList();
            if (importList == null) {
                return;
            }
            checkNonStaticImports(importList, javaFile);
            checkStaticImports(importList, javaFile);
        }

        private void checkStaticImports(PsiImportList importList, PsiJavaFile javaFile) {
            PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
            Set<String> onDemandStaticImports = new HashSet();
            Set<String> singleMemberStaticImports = new HashSet();
            for (PsiImportStaticStatement importStaticStatement : importStaticStatements) {
                PsiClass targetClass = importStaticStatement.resolveTargetClass();
                if (targetClass == null) {
                    continue;
                }
                String qualifiedName = targetClass.getQualifiedName();
                String referenceName = importStaticStatement.getReferenceName();
                if (referenceName == null) {
                    if (onDemandStaticImports.contains(qualifiedName)) {
                        registerError(importStaticStatement);
                        continue;
                    }
                    onDemandStaticImports.add(qualifiedName);
                }
                else {
                    String qualifiedReferenceName = qualifiedName + '.' + referenceName;
                    if (singleMemberStaticImports.contains(qualifiedReferenceName)) {
                        registerError(importStaticStatement);
                        continue;
                    }
                    if (onDemandStaticImports.contains(qualifiedName)) {
                        if (!ImportUtils.hasOnDemandImportConflict(qualifiedReferenceName, javaFile)) {
                            registerError(importStaticStatement);
                        }
                    }
                    singleMemberStaticImports.add(qualifiedReferenceName);
                }
            }
        }

        private void checkNonStaticImports(PsiImportList importList, PsiJavaFile javaFile) {
            PsiImportStatement[] importStatements = importList.getImportStatements();
            Set<String> onDemandImports = new HashSet();
            Set<String> singleClassImports = new HashSet();
            for (PsiImportStatement importStatement : importStatements) {
                String qualifiedName = importStatement.getQualifiedName();
                if (qualifiedName == null) {
                    continue;
                }
                if (importStatement.isOnDemand()) {
                    if (onDemandImports.contains(qualifiedName)) {
                        registerError(importStatement);
                    }
                    onDemandImports.add(qualifiedName);
                }
                else {
                    if (singleClassImports.contains(qualifiedName)) {
                        registerError(importStatement);
                        continue;
                    }
                    PsiElement element = importStatement.resolve();
                    if (!(element instanceof PsiClass)) {
                        continue;
                    }
                    PsiElement context = element.getContext();
                    if (context == null) {
                        continue;
                    }
                    String contextName;
                    if (context instanceof PsiJavaFile) {
                        PsiJavaFile file = (PsiJavaFile) context;
                        contextName = file.getPackageName();
                    }
                    else if (context instanceof PsiClass) {
                        PsiClass aClass = (PsiClass) context;
                        contextName = aClass.getQualifiedName();
                    }
                    else {
                        continue;
                    }
                    if (onDemandImports.contains(contextName) &&
                        !ImportUtils.hasOnDemandImportConflict(qualifiedName, javaFile) &&
                        !ImportUtils.hasDefaultImportConflict(qualifiedName, javaFile)) {
                        registerError(importStatement);
                    }
                    singleClassImports.add(qualifiedName);
                }
            }
        }
    }
}