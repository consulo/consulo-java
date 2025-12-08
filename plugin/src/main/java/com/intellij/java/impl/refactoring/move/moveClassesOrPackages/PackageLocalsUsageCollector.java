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
package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import com.intellij.java.impl.refactoring.PackageWrapper;
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class PackageLocalsUsageCollector extends JavaRecursiveElementWalkingVisitor {
    private final Map<PsiElement, Set<PsiElement>> myReported = new HashMap<>();
    private final PsiElement[] myElementsToMove;
    @Nonnull
    private final MultiMap<PsiElement, LocalizeValue> myConflicts;
    private final PackageWrapper myTargetPackage;

    public PackageLocalsUsageCollector(
        PsiElement[] elementsToMove,
        PackageWrapper targetPackage,
        @Nonnull MultiMap<PsiElement, LocalizeValue> conflicts
    ) {
        myElementsToMove = elementsToMove;
        myConflicts = conflicts;
        myTargetPackage = targetPackage;
    }

    @Override
    @RequiredReadAction
    public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        visitReferenceElement(expression);
    }

    @Override
    @RequiredReadAction
    public void visitReferenceElement(@Nonnull PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        PsiElement resolved = reference.resolve();
        visitResolvedReference(resolved, reference);
    }

    private void visitResolvedReference(PsiElement resolved, PsiJavaCodeReferenceElement reference) {
        if (resolved instanceof PsiModifierListOwner modifierListOwner) {
            PsiModifierList modifierList = modifierListOwner.getModifierList();
            if (PsiModifier.PACKAGE_LOCAL.equals(VisibilityUtil.getVisibilityModifier(modifierList))) {
                PsiFile aFile = modifierListOwner.getContainingFile();
                if (aFile != null && !isInsideMoved(modifierListOwner)) {
                    PsiDirectory containingDirectory = aFile.getContainingDirectory();
                    if (containingDirectory != null) {
                        PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(containingDirectory);
                        if (aPackage != null && !myTargetPackage.equalToPackage(aPackage)) {
                            Set<PsiElement> reportedRefs = myReported.get(modifierListOwner);
                            if (reportedRefs == null) {
                                reportedRefs = new HashSet<>();
                                myReported.put(modifierListOwner, reportedRefs);
                            }
                            PsiElement container = ConflictsUtil.getContainer(reference);
                            if (!reportedRefs.contains(container)) {
                                LocalizeValue message = RefactoringLocalize.zeroUsesAPackageLocal1(
                                    RefactoringUIUtil.getDescription(container, true),
                                    RefactoringUIUtil.getDescription(modifierListOwner, true)
                                );
                                myConflicts.putValue(modifierListOwner, message.capitalize());
                                reportedRefs.add(container);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isInsideMoved(PsiElement place) {
        for (PsiElement element : myElementsToMove) {
            if (element instanceof PsiClass && PsiTreeUtil.isAncestor(element, place, false)) {
                return true;
            }
        }
        return false;
    }
}