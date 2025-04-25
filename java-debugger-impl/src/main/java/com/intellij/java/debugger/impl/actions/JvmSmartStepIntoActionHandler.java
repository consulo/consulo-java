// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.frame.XSuspendContext;
import consulo.execution.debug.step.XSmartStepIntoHandler;
import consulo.execution.debug.step.XSmartStepIntoVariant;
import consulo.language.psi.PsiElement;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

public class JvmSmartStepIntoActionHandler extends XSmartStepIntoHandler<JvmSmartStepIntoActionHandler.JvmSmartStepIntoVariant> {
    private final DebuggerSession mySession;

    public JvmSmartStepIntoActionHandler(@Nonnull DebuggerSession session) {
        mySession = session;
    }

    @Nonnull
    @Override
    public List<JvmSmartStepIntoVariant> computeSmartStepVariants(@Nonnull XSourcePosition xPosition) {
        SourcePosition pos = DebuggerUtilsEx.toSourcePosition(xPosition, mySession.getProject());
        JvmSmartStepIntoHandler handler = JvmSmartStepIntoHandler.EP_NAME.findFirstSafe(h -> h.isAvailable(pos));
        if (handler != null) {
            List<SmartStepTarget> targets = handler.findSmartStepTargets(pos);
            return ContainerUtil.map(targets, target -> new JvmSmartStepIntoVariant(target, handler));
        }

        return List.of();
    }

    @Override
    public String getPopupTitle(@Nonnull XSourcePosition position) {
        return JavaDebuggerLocalize.titleSmartStepPopup().get();
    }

    @Override
    @RequiredUIAccess
    public void startStepInto(@Nonnull JvmSmartStepIntoVariant variant, @Nullable XSuspendContext context) {
        mySession.stepInto(true, variant.myHandler.createMethodFilter(variant.myTarget));
    }

    static class JvmSmartStepIntoVariant extends XSmartStepIntoVariant {
        private final SmartStepTarget myTarget;
        private final JvmSmartStepIntoHandler myHandler;

        JvmSmartStepIntoVariant(SmartStepTarget target, JvmSmartStepIntoHandler handler) {
            myTarget = target;
            myHandler = handler;
        }

        @Override
        public String getText() {
            return myTarget.getPresentation();
        }

        @Nullable
        @Override
        public Image getIcon() {
            return myTarget.getIcon();
        }

        @Nullable
        //@Override
        @RequiredReadAction
        public TextRange getHighlightRange() {
            PsiElement element = myTarget.getHighlightElement();
            return element != null ? element.getTextRange() : null;
        }
    }
}
