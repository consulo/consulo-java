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
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.unwrap.ScopeHighlighter;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.popup.*;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 * @since 2006-11-21
 */
class PsiMethodListPopupStep implements ListPopupStep<SmartStepTarget> {
    private final List<SmartStepTarget> myTargets;
    private final OnChooseRunnable myStepRunnable;
    private final ScopeHighlighter myScopeHighlighter;

    public interface OnChooseRunnable {
        void execute(SmartStepTarget stepTarget);
    }

    public PsiMethodListPopupStep(Editor editor, List<SmartStepTarget> targets, OnChooseRunnable stepRunnable) {
        myTargets = targets;
        myScopeHighlighter = new ScopeHighlighter(editor);
        myStepRunnable = stepRunnable;
    }

    @Nonnull
    public ScopeHighlighter getScopeHighlighter() {
        return myScopeHighlighter;
    }

    @Override
    @Nonnull
    public List<SmartStepTarget> getValues() {
        return myTargets;
    }

    @Override
    public boolean isSelectable(SmartStepTarget value) {
        return true;
    }

    @Override
    @RequiredUIAccess
    public Image getIconFor(SmartStepTarget aValue) {
        if (aValue instanceof MethodSmartStepTarget methodSmartStepTarget) {
            return IconDescriptorUpdaters.getIcon(methodSmartStepTarget.getMethod(), 0);
        }
        if (aValue instanceof LambdaSmartStepTarget) {
            return PlatformIconGroup.nodesFunction();
        }
        return null;
    }

    @Nonnull
    @Override
    public String getTextFor(SmartStepTarget value) {
        String label = value.getLabel();
        String formatted;
        if (value instanceof MethodSmartStepTarget methodSmartStepTarget) {
            formatted = PsiFormatUtil.formatMethod(
                methodSmartStepTarget.getMethod(),
                PsiSubstitutor.EMPTY,
                PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                PsiFormatUtil.SHOW_TYPE,
                999
            );
        }
        else if (value instanceof LambdaSmartStepTarget lambdaSmartStepTarget) {
            formatted = PsiFormatUtil.formatType(lambdaSmartStepTarget.getLambda().getType(), 0, PsiSubstitutor.EMPTY);
        }
        else {
            formatted = "";
        }
        return label != null ? label + formatted : formatted;
    }

    @Override
    public ListSeparator getSeparatorAbove(SmartStepTarget value) {
        return null;
    }

    @Override
    public int getDefaultOptionIndex() {
        return 0;
    }

    @Override
    public String getTitle() {
        return JavaDebuggerLocalize.titleSmartStepPopup().get();
    }

    @Override
    public PopupStep onChosen(SmartStepTarget selectedValue, boolean finalChoice) {
        if (finalChoice) {
            myScopeHighlighter.dropHighlight();
            myStepRunnable.execute(selectedValue);
        }
        return FINAL_CHOICE;
    }

    @Override
    public Runnable getFinalRunnable() {
        return null;
    }

    @Override
    public boolean hasSubstep(SmartStepTarget selectedValue) {
        return false;
    }

    @Override
    public void canceled() {
        myScopeHighlighter.dropHighlight();
    }

    @Override
    public boolean isMnemonicsNavigationEnabled() {
        return false;
    }

    @Override
    public MnemonicNavigationFilter getMnemonicNavigationFilter() {
        return null;
    }

    @Override
    public boolean isSpeedSearchEnabled() {
        return false;
    }

    @Override
    public SpeedSearchFilter getSpeedSearchFilter() {
        return null;
    }

    @Override
    public boolean isAutoSelectionEnabled() {
        return false;
    }
}
