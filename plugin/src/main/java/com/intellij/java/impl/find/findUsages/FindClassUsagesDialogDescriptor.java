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
package com.intellij.java.impl.find.findUsages;

import com.intellij.java.analysis.impl.find.findUsages.JavaClassFindUsagesOptions;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifier;
import consulo.find.FindUsagesHandler;
import consulo.find.FindUsagesOptions;
import consulo.find.localize.FindLocalize;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.StateRestoringCheckBoxWrapper;
import org.jspecify.annotations.Nullable;

public class FindClassUsagesDialogDescriptor extends JavaFindUsagesDialogDescriptor<JavaClassFindUsagesOptions> {
    private @Nullable StateRestoringCheckBoxWrapper myCbUsages;
    private @Nullable StateRestoringCheckBoxWrapper myCbMethodsUsages;
    private @Nullable StateRestoringCheckBoxWrapper myCbFieldsUsages;
    private @Nullable StateRestoringCheckBoxWrapper myCbImplementingClasses;
    private @Nullable StateRestoringCheckBoxWrapper myCbDerivedInterfaces;
    private @Nullable StateRestoringCheckBoxWrapper myCbDerivedClasses;

    @RequiredUIAccess
    public FindClassUsagesDialogDescriptor(
        PsiElement element,
        Project project,
        FindUsagesOptions findUsagesOptions,
        boolean toShowInNewTab,
        boolean mustOpenInNewTab,
        boolean isSingleFile,
        FindUsagesHandler handler
    ) {
        super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
    }

    @Override
    protected @Nullable Component getPreferredFocusedControl() {
        return myCbUsages == null ? null : myCbUsages.getComponent();
    }

    @Override
    public void calcFindUsagesOptions(JavaClassFindUsagesOptions options) {
        super.calcFindUsagesOptions(options);

        if (isToChange(myCbUsages)) {
            options.isUsages = isSelected(myCbUsages);
        }
        if (isToChange(myCbMethodsUsages)) {
            options.isMethodsUsages = isSelected(myCbMethodsUsages);
        }
        if (isToChange(myCbFieldsUsages)) {
            options.isFieldsUsages = isSelected(myCbFieldsUsages);
        }
        if (isToChange(myCbDerivedClasses)) {
            options.isDerivedClasses = isSelected(myCbDerivedClasses);
        }
        if (isToChange(myCbImplementingClasses)) {
            options.isImplementingClasses = isSelected(myCbImplementingClasses);
        }
        if (isToChange(myCbDerivedInterfaces)) {
            options.isDerivedInterfaces = isSelected(myCbDerivedInterfaces);
        }
        options.isSkipImportStatements = false;
        options.isCheckDeepInheritance = true;
        options.isIncludeInherited = false;
    }

    @Override
    @RequiredUIAccess
    protected void addFindWhatOptions() {
        JavaClassFindUsagesOptions options = getFindUsagesOptions();
        myCbUsages = addFindWhatCheckbox(FindLocalize.findWhatUsagesCheckbox(), options.isUsages, true);

        PsiClass psiClass = (PsiClass) getPsiElement();
        myCbMethodsUsages = addFindWhatCheckbox(FindLocalize.findWhatMethodsUsagesCheckbox(), options.isMethodsUsages, true);

        if (!psiClass.isAnnotationType()) {
            myCbFieldsUsages = addFindWhatCheckbox(FindLocalize.findWhatFieldsUsagesCheckbox(), options.isFieldsUsages, true);
            if (psiClass.isInterface()) {
                myCbImplementingClasses =
                    addFindWhatCheckbox(FindLocalize.findWhatImplementingClassesCheckbox(), options.isImplementingClasses, true);
                myCbDerivedInterfaces =
                    addFindWhatCheckbox(FindLocalize.findWhatDerivedInterfacesCheckbox(), options.isDerivedInterfaces, true);
            }
            else if (!psiClass.hasModifierProperty(PsiModifier.FINAL)) {
                myCbDerivedClasses = addFindWhatCheckbox(FindLocalize.findWhatDerivedClassesCheckbox(), options.isDerivedClasses, true);
            }
        }
    }

    @Override
    @RequiredUIAccess
    protected void update() {
        StateRestoringCheckBoxWrapper searchForTextOccurrences = myCbToSearchForTextOccurrences;
        if (searchForTextOccurrences != null) {
            if (isSelected(myCbUsages)) {
                searchForTextOccurrences.makeSelectable();
            }
            else {
                searchForTextOccurrences.makeUnselectable(false);
            }
        }
    }

    @Override
    protected boolean isOkActionEnabled() {
        return isSelected(myCbUsages)
            || isSelected(myCbFieldsUsages)
            || isSelected(myCbMethodsUsages)
            || isSelected(myCbImplementingClasses)
            || isSelected(myCbDerivedInterfaces)
            || isSelected(myCbDerivedClasses);
    }
}
