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

import com.intellij.java.analysis.impl.find.findUsages.JavaMethodFindUsagesOptions;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
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

public class FindMethodUsagesDialogDescriptor extends JavaFindUsagesDialogDescriptor<JavaMethodFindUsagesOptions> {
    private final boolean myHasFindWhatPanel;
    private @Nullable StateRestoringCheckBoxWrapper myCbUsages;
    private @Nullable StateRestoringCheckBoxWrapper myCbImplementingMethods;
    private @Nullable StateRestoringCheckBoxWrapper myCbOverridingMethods;

    @RequiredUIAccess
    public FindMethodUsagesDialogDescriptor(
        PsiElement element,
        Project project,
        FindUsagesOptions findUsagesOptions,
        boolean toShowInNewTab,
        boolean mustOpenInNewTab,
        boolean isSingleFile,
        FindUsagesHandler handler
    ) {
        super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
        myHasFindWhatPanel = canBeOverridden((PsiMethod) element);
    }

    private static boolean canBeOverridden(PsiMethod method) {
        PsiClass aClass = method.getContainingClass();
        return !method.isConstructor()
            && !method.hasModifierProperty(PsiModifier.STATIC)
            && !method.hasModifierProperty(PsiModifier.FINAL)
            && !method.hasModifierProperty(PsiModifier.PRIVATE)
            && aClass != null
            && !(aClass instanceof PsiAnonymousClass)
            && !aClass.hasModifierProperty(PsiModifier.FINAL);
    }

    @Override
    protected @Nullable Component getPreferredFocusedControl() {
        return myCbUsages == null ? null : myCbUsages.getComponent();
    }

    @Override
    public void calcFindUsagesOptions(JavaMethodFindUsagesOptions options) {
        super.calcFindUsagesOptions(options);

        if (!myHasFindWhatPanel) {
            options.isUsages = true;
        }
        else if (isToChange(myCbUsages)) {
            options.isUsages = isSelected(myCbUsages);
        }
        if (isToChange(myCbOverridingMethods)) {
            options.isOverridingMethods = isSelected(myCbOverridingMethods);
        }
        if (isToChange(myCbImplementingMethods)) {
            options.isImplementingMethods = isSelected(myCbImplementingMethods);
        }
        options.isCheckDeepInheritance = true;
    }

    @Override
    @RequiredUIAccess
    protected void addFindWhatOptions() {
        if (!myHasFindWhatPanel) {
            return;
        }

        JavaMethodFindUsagesOptions options = getFindUsagesOptions();
        myCbUsages = addFindWhatCheckbox(FindLocalize.findWhatUsagesCheckbox(), options.isUsages, true);

        PsiMethod method = (PsiMethod) getPsiElement();
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
            myCbImplementingMethods =
                addFindWhatCheckbox(FindLocalize.findWhatImplementingMethodsCheckbox(), options.isImplementingMethods, true);
        }
        else {
            myCbOverridingMethods =
                addFindWhatCheckbox(FindLocalize.findWhatOverridingMethodsCheckbox(), options.isOverridingMethods, true);
        }
    }

    @Override
    protected boolean isOkActionEnabled() {
        return !myHasFindWhatPanel
            || isSelected(myCbUsages)
            || isSelected(myCbImplementingMethods)
            || isSelected(myCbOverridingMethods);
    }
}
