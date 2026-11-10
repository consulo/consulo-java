/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.java.analysis.impl.find.findUsages.JavaThrowFindUsagesOptions;
import com.intellij.java.analysis.impl.psi.impl.search.ThrowSearchUtil;
import consulo.disposer.Disposable;
import consulo.find.FindUsagesHandler;
import consulo.find.localize.FindLocalize;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.StateRestoringCheckBoxWrapper;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public class FindThrowUsagesDialogDescriptor extends JavaFindUsagesDialogDescriptor<JavaThrowFindUsagesOptions> {
    private final ThrowSearchUtil.Root[] myRoots;
    private @Nullable StateRestoringCheckBoxWrapper myCbUsages;
    private @Nullable ComboBox<ThrowSearchUtil.Root> myCbExns;

    @RequiredUIAccess
    public FindThrowUsagesDialogDescriptor(
        PsiElement element,
        Project project,
        JavaThrowFindUsagesOptions findUsagesOptions,
        boolean toShowInNewTab,
        boolean mustOpenInNewTab,
        boolean isSingleFile,
        FindUsagesHandler handler
    ) {
        super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
        ThrowSearchUtil.Root[] roots = ThrowSearchUtil.getSearchRoots(element);
        myRoots = roots == null ? new ThrowSearchUtil.Root[0] : roots;
    }

    @Override
    protected @Nullable Component getPreferredFocusedControl() {
        return myCbUsages == null ? null : myCbUsages.getComponent();
    }

    @Override
    @RequiredUIAccess
    protected void buildElementComponent(Consumer<Component> consumer, Disposable uiDisposable) {
        ComboBox<ThrowSearchUtil.Root> exceptions = ComboBox.create(myRoots);
        exceptions.setRenderer((presentation, item) -> presentation.append(String.valueOf(item.getValue())));
        exceptions.selectFirst();
        myCbExns = exceptions;
        consumer.accept(exceptions);
    }

    @Override
    public void calcFindUsagesOptions(JavaThrowFindUsagesOptions options) {
        super.calcFindUsagesOptions(options);
        if (isToChange(myCbUsages)) {
            options.isUsages = isSelected(myCbUsages);
        }
        ComboBox<ThrowSearchUtil.Root> exceptions = myCbExns;
        ThrowSearchUtil.Root root = exceptions == null ? null : exceptions.getValue();
        if (root != null) {
            options.setRoot(root);
        }
    }

    @Override
    @RequiredUIAccess
    protected void addFindWhatOptions() {
        myCbUsages = addFindWhatCheckbox(FindLocalize.findWhatUsagesCheckbox(), myFindUsagesOptions.isUsages, true);
    }

    @Override
    protected boolean isOkActionEnabled() {
        return isSelected(myCbUsages);
    }
}
