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

import com.intellij.java.analysis.impl.find.findUsages.JavaFindUsagesOptions;
import com.intellij.java.analysis.impl.find.findUsages.JavaMethodFindUsagesOptions;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import consulo.find.FindSettings;
import consulo.find.FindUsagesHandler;
import consulo.find.FindUsagesOptions;
import consulo.find.localize.FindLocalize;
import consulo.find.ui.CommonFindUsagesDialogDescriptor;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.StateRestoringCheckBoxWrapper;
import org.jspecify.annotations.Nullable;

public abstract class JavaFindUsagesDialogDescriptor<T extends JavaFindUsagesOptions> extends CommonFindUsagesDialogDescriptor {
    private final boolean myIncludeOverloadedMethodsAvailable;
    private @Nullable StateRestoringCheckBoxWrapper myCbIncludeOverloadedMethods;

    @RequiredUIAccess
    protected JavaFindUsagesDialogDescriptor(
        PsiElement element,
        Project project,
        FindUsagesOptions findUsagesOptions,
        boolean toShowInNewTab,
        boolean mustOpenInNewTab,
        boolean isSingleFile,
        FindUsagesHandler handler
    ) {
        super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
        myIncludeOverloadedMethodsAvailable = element instanceof PsiMethod method && MethodSignatureUtil.hasOverloads(method);
    }

    public void calcFindUsagesOptions(T options) {
        if (options instanceof JavaMethodFindUsagesOptions methodOptions && isToChange(myCbIncludeOverloadedMethods)) {
            methodOptions.isIncludeOverloadUsages = myIncludeOverloadedMethodsAvailable && isSelected(myCbIncludeOverloadedMethods);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void calcFindUsagesOptions(FindUsagesOptions options) {
        super.calcFindUsagesOptions(options);
        calcFindUsagesOptions((T) options);
    }

    @Override
    @RequiredUIAccess
    protected void doOKAction() {
        super.doOKAction();
        StateRestoringCheckBoxWrapper includeOverloadedMethods = myCbIncludeOverloadedMethods;
        if (myIncludeOverloadedMethodsAvailable && includeOverloadedMethods != null) {
            FindSettings.getInstance().setSearchOverloadedMethods(includeOverloadedMethods.getValue());
        }
    }

    @Override
    @RequiredUIAccess
    protected void addUsagesOptions() {
        super.addUsagesOptions();
        if (myIncludeOverloadedMethodsAvailable) {
            myCbIncludeOverloadedMethods = addUsagesOptionCheckbox(
                FindLocalize.findOptionsIncludeOverloadedMethodsCheckbox(),
                FindSettings.getInstance().isSearchOverloadedMethods(),
                false
            );
        }
    }

    protected final PsiElement getPsiElement() {
        return myPsiElement;
    }

    @SuppressWarnings("unchecked")
    protected T getFindUsagesOptions() {
        return (T) myFindUsagesOptions;
    }
}
