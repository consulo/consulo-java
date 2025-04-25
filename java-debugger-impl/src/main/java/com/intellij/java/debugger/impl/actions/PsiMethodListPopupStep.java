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

import consulo.language.editor.refactoring.unwrap.ScopeHighlighter;
import com.intellij.java.debugger.DebuggerBundle;
import consulo.application.AllIcons;
import consulo.codeEditor.Editor;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.popup.*;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 * Date: Nov 21, 2006
 */
class PsiMethodListPopupStep implements ListPopupStep<SmartStepTarget> {
    private final List<SmartStepTarget> myTargets;
    private final OnChooseRunnable myStepRunnable;
    private final ScopeHighlighter myScopeHighlighter;

    public interface OnChooseRunnable {
        void execute(SmartStepTarget stepTarget);
    }

    public PsiMethodListPopupStep(Editor editor, final List<SmartStepTarget> targets, final OnChooseRunnable stepRunnable) {
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
        if (aValue instanceof MethodSmartStepTarget) {
            return IconDescriptorUpdaters.getIcon(((MethodSmartStepTarget)aValue).getMethod(), 0);
        }
        if (aValue instanceof LambdaSmartStepTarget) {
            return AllIcons.Nodes.Function;
        }
        return null;
    }

    @Override
    @Nonnull
    public String getTextFor(SmartStepTarget value) {
        final String label = value.getLabel();
        final String formatted;
        if (value instanceof MethodSmartStepTarget) {
            final PsiMethod method = ((MethodSmartStepTarget)value).getMethod();
            formatted = PsiFormatUtil.formatMethod(
                method,
                PsiSubstitutor.EMPTY,
                PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                PsiFormatUtil.SHOW_TYPE,
                999
            );
        }
        else if (value instanceof LambdaSmartStepTarget) {
            final PsiLambdaExpression lambda = ((LambdaSmartStepTarget)value).getLambda();
            formatted = PsiFormatUtil.formatType(lambda.getType(), 0, PsiSubstitutor.EMPTY);
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
        return DebuggerBundle.message("title.smart.step.popup");
    }

    @Override
    public PopupStep onChosen(SmartStepTarget selectedValue, final boolean finalChoice) {
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
