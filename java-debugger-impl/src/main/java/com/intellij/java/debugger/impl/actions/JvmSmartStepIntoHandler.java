/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.engine.*;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.codeEditor.Editor;
import consulo.component.extension.ExtensionPointName;
import consulo.execution.debug.XDebuggerActions;
import consulo.execution.debug.ui.DebuggerUIUtil;
import consulo.fileEditor.TextEditor;
import consulo.ide.impl.idea.ui.popup.list.ListPopupImpl;
import consulo.language.psi.PsiElement;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.JBList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Podkhalyuzin
 * @since 2011-11-22
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class JvmSmartStepIntoHandler {
    public static final ExtensionPointName<JvmSmartStepIntoHandler> EP_NAME = ExtensionPointName.create(JvmSmartStepIntoHandler.class);

    @Nonnull
    public abstract List<SmartStepTarget> findSmartStepTargets(SourcePosition position);

    public abstract boolean isAvailable(SourcePosition position);

    /**
     * Override this if you haven't PsiMethod, like in Kotlin.
     *
     * @param position
     * @param session
     * @param fileEditor
     * @return false to continue for another handler or for default action (step into)
     */
    @RequiredUIAccess
    public boolean doSmartStep(SourcePosition position, DebuggerSession session, TextEditor fileEditor) {
        List<SmartStepTarget> targets = findSmartStepTargets(position);
        if (!targets.isEmpty()) {
            SmartStepTarget firstTarget = targets.get(0);
            if (targets.size() == 1) {
                session.sessionResumed();
                session.stepInto(true, createMethodFilter(firstTarget));
            }
            else {
                Editor editor = fileEditor.getEditor();
                PsiMethodListPopupStep popupStep =
                    new PsiMethodListPopupStep(editor, targets, new PsiMethodListPopupStep.OnChooseRunnable() {
                        @Override
                        @RequiredUIAccess
                        public void execute(SmartStepTarget chosenTarget) {
                            session.sessionResumed();
                            session.stepInto(true, createMethodFilter(chosenTarget));
                        }
                    });
                ListPopupImpl popup = new ListPopupImpl(popupStep);
                DebuggerUIUtil.registerExtraHandleShortcuts(popup, XDebuggerActions.STEP_INTO);
                DebuggerUIUtil.registerExtraHandleShortcuts(popup, XDebuggerActions.SMART_STEP_INTO);
                popup.addListSelectionListener(e -> {
                    popupStep.getScopeHighlighter().dropHighlight();
                    if (!e.getValueIsAdjusting()) {
                        SmartStepTarget selectedTarget = (SmartStepTarget)((JBList)e.getSource()).getSelectedValue();
                        if (selectedTarget != null) {
                            highlightTarget(popupStep, selectedTarget);
                        }
                    }
                });
                highlightTarget(popupStep, firstTarget);
                DebuggerUIUtil.showPopupForEditorLine(popup, editor, position.getLine());
            }
            return true;
        }
        return false;
    }

    private static void highlightTarget(PsiMethodListPopupStep popupStep, SmartStepTarget target) {
        PsiElement highlightElement = target.getHighlightElement();
        if (highlightElement != null) {
            popupStep.getScopeHighlighter().highlight(highlightElement, Collections.singletonList(highlightElement));
        }
    }

    /**
     * Override in case if your JVMNames slightly different then it can be provided by getJvmSignature method.
     *
     * @param stepTarget
     * @return SmartStepFilter
     */
    @Nullable
    protected MethodFilter createMethodFilter(SmartStepTarget stepTarget) {
        if (stepTarget instanceof MethodSmartStepTarget methodSmartStepTarget) {
            PsiMethod method = methodSmartStepTarget.getMethod();
            if (stepTarget.needsBreakpointRequest()) {
                return method.getContainingClass() instanceof PsiAnonymousClass
                    ? new ClassInstanceMethodFilter(method, stepTarget.getCallingExpressionLines())
                    : new AnonymousClassMethodFilter(method, stepTarget.getCallingExpressionLines());
            }
            else {
                return new BasicStepMethodFilter(method, stepTarget.getCallingExpressionLines());
            }
        }
        if (stepTarget instanceof LambdaSmartStepTarget lambdaTarget) {
            return new LambdaMethodFilter(lambdaTarget.getLambda(), lambdaTarget.getOrdinal(), stepTarget.getCallingExpressionLines());
        }
        return null;
    }
}
