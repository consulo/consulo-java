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

import com.intellij.java.analysis.impl.find.findUsages.JavaVariableFindUsagesOptions;
import consulo.find.FindUsagesHandler;
import consulo.find.FindUsagesOptions;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.StateRestoringCheckBoxWrapper;
import org.jspecify.annotations.Nullable;

public class FindVariableUsagesDialogDescriptor extends JavaFindUsagesDialogDescriptor<JavaVariableFindUsagesOptions> {
    @RequiredUIAccess
    public FindVariableUsagesDialogDescriptor(
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
        StateRestoringCheckBoxWrapper skipResultsWhenOneUsage = myCbToSkipResultsWhenOneUsage;
        return skipResultsWhenOneUsage == null ? null : skipResultsWhenOneUsage.getComponent();
    }

    @Override
    public void calcFindUsagesOptions(JavaVariableFindUsagesOptions options) {
        super.calcFindUsagesOptions(options);

        options.isReadAccess = true;
        options.isWriteAccess = true;
    }
}
