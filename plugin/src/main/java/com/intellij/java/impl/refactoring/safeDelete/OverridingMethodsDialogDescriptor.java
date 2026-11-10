/*
 * Copyright 2013-2026 consulo.io
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
package com.intellij.java.impl.refactoring.safeDelete;

import com.intellij.java.impl.refactoring.safeDelete.usageInfo.SafeDeleteOverridingMethodUsageInfo;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.disposer.Disposable;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.WidthAndHeight;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.Dialog;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.usage.UsageInfo;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Lists the unused methods overriding the deleted ones, so the user picks those to delete too.
 *
 * @author VISTALL
 * @since 2026-11-10
 */
class OverridingMethodsDialogDescriptor extends DialogDescriptor {
    private final Project myProject;
    private final List<UsageInfo> myOverridingMethods;
    private final List<CheckBox> myCheckBoxes = new ArrayList<>();

    OverridingMethodsDialogDescriptor(Project project, List<UsageInfo> overridingMethods) {
        super(RefactoringLocalize.unusedOverridingMethodsTitle());
        myProject = project;
        myOverridingMethods = overridingMethods;
    }

    /**
     * @return the chosen overriding methods, {@code null} when the user cancelled the refactoring
     */
    @RequiredUIAccess
    CompletableFuture<@Nullable List<UsageInfo>> showAsync() {
        Dialog dialog = myProject.getApplication().getInstance(DialogService.class).build(myProject, this);
        return dialog.showAsync().handle((value, error) -> error == null && value != null ? getSelected() : null);
    }

    private List<UsageInfo> getSelected() {
        List<UsageInfo> result = new ArrayList<>();
        for (int i = 0; i < myCheckBoxes.size(); i++) {
            if (myCheckBoxes.get(i).getValueOrError()) {
                result.add(myOverridingMethods.get(i));
            }
        }
        return result;
    }

    @Override
    public @Nullable WidthAndHeight getInitialSize() {
        return WidthAndHeight.ofPixel(500, 300);
    }

    @Override
    @RequiredUIAccess
    public Component createCenterComponent(Disposable uiDisposable) {
        VerticalLayout methods = VerticalLayout.create();
        for (UsageInfo usage : myOverridingMethods) {
            String text = PsiFormatUtil.formatMethod(
                ((SafeDeleteOverridingMethodUsageInfo) usage).getOverridingMethod(),
                PsiSubstitutor.EMPTY,
                PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME
                    | PsiFormatUtilBase.SHOW_PARAMETERS | PsiFormatUtilBase.SHOW_TYPE,
                PsiFormatUtilBase.SHOW_TYPE
            );
            CheckBox checkBox = CheckBox.create(LocalizeValue.of(text), true);
            myCheckBoxes.add(checkBox);
            methods.add(checkBox);
        }

        VerticalLayout header = VerticalLayout.create();
        header.add(Label.create(RefactoringLocalize.thereAreUnusedMethodsThatOverrideMethodsYouDelete()));
        header.add(Label.create(RefactoringLocalize.chooseTheOnesYouWantToBeDeleted()));

        DockLayout layout = DockLayout.create();
        layout.top(header);
        layout.center(ScrollableLayout.create(methods));
        return layout;
    }
}
