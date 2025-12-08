/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.ig.fixes;

import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierList;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.AccessToken;
import consulo.application.WriteAction;
import consulo.application.util.query.Query;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;

/**
 * @author Bas Leijdekkers
 */
public class MakeClassFinalFix extends InspectionGadgetsFix {
    private final String className;

    @RequiredReadAction
    public MakeClassFinalFix(PsiClass aClass) {
        className = aClass.getName();
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return InspectionGadgetsLocalize.makeClassFinalFixName(className);
    }

    @Override
    @RequiredWriteAction
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
        PsiElement element = descriptor.getPsiElement();
        PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (containingClass == null) {
            return;
        }
        PsiModifierList modifierList = containingClass.getModifierList();
        if (modifierList == null) {
            return;
        }
        if (!isOnTheFly()) {
            if (ClassInheritorsSearch.search(containingClass).findFirst() != null) {
                return;
            }
            modifierList.setModifierProperty(PsiModifier.FINAL, true);
            modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
            return;
        }
        MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();
        Query<PsiClass> search = ClassInheritorsSearch.search(containingClass);
        search.forEach(aClass -> {
            conflicts.putValue(
                containingClass,
                InspectionGadgetsLocalize.zeroWillNoLongerBeOverridableBy1(
                    RefactoringUIUtil.getDescription(containingClass, false),
                    RefactoringUIUtil.getDescription(aClass, false)
                )
            );
            return true;
        });
        boolean conflictsDialogOK;
        if (!conflicts.isEmpty()) {
            ConflictsDialog conflictsDialog = new ConflictsDialog(
                element.getProject(),
                conflicts,
                () -> {
                    AccessToken token = WriteAction.start();
                    try {
                        modifierList.setModifierProperty(PsiModifier.FINAL, true);
                        modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
                    }
                    finally {
                        token.finish();
                    }
                }
            );
            conflictsDialog.show();
            conflictsDialogOK = conflictsDialog.isOK();
        }
        else {
            conflictsDialogOK = true;
        }
        if (conflictsDialogOK) {
            modifierList.setModifierProperty(PsiModifier.FINAL, true);
            modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
        }
    }
}
