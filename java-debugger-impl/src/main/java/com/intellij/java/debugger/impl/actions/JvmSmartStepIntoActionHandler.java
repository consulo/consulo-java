// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.frame.XSuspendContext;
import consulo.execution.debug.step.ForceSmartStepIntoSource;
import consulo.execution.debug.step.XSmartStepIntoHandler;
import consulo.execution.debug.step.XSmartStepIntoVariant;
import consulo.language.psi.PsiElement;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class JvmSmartStepIntoActionHandler implements XSmartStepIntoHandler<JvmSmartStepIntoActionHandler.JvmSmartStepIntoVariant> {
    private final DebuggerSession mySession;

    public JvmSmartStepIntoActionHandler(DebuggerSession session) {
        mySession = session;
    }

    @Override
    public CompletableFuture<List<JvmSmartStepIntoVariant>> computeSmartStepVariantsAsync(XSourcePosition position) {
        return findVariants(position, true);
    }

    @Override
    public CompletableFuture<List<JvmSmartStepIntoVariant>> computeStepIntoVariants(XSourcePosition position) {
        return findVariants(position, false);
    }

    private CompletableFuture<List<JvmSmartStepIntoVariant>> findVariants(XSourcePosition xPosition, boolean smart) {
        SourcePosition position = DebuggerUtilsEx.toSourcePosition(xPosition, mySession.getProject());
        JvmSmartStepIntoHandler handler = JvmSmartStepIntoHandler.EP_NAME.findFirstSafe(h -> h.isAvailable(position));
        if (handler == null) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
        CompletableFuture<List<SmartStepTarget>> targets =
            smart ? handler.findSmartStepTargetsAsync(position, mySession) : handler.findStepIntoTargets(position, mySession);
        return targets.thenApply(list -> ContainerUtil.map(list, target -> new JvmSmartStepIntoVariant(target, handler)));
    }

    @Override
    public List<JvmSmartStepIntoVariant> computeSmartStepVariants(XSourcePosition position) {
        throw new IllegalStateException("Should not be called");
    }

    @Override
    public String getPopupTitle(XSourcePosition position) {
        return JavaDebuggerLocalize.titleSmartStepPopup().get();
    }

    @Override
    @RequiredUIAccess
    public void stepIntoEmpty(XDebugSession session) {
        session.forceStepInto();
    }

    @Override
    @RequiredUIAccess
    public void startStepInto(JvmSmartStepIntoVariant variant, @Nullable XSuspendContext context) {
        mySession.stepInto(true, variant.myHandler.createMethodFilter(variant.myTarget));
    }

    static class JvmSmartStepIntoVariant extends XSmartStepIntoVariant implements ForceSmartStepIntoSource {
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
        @Override
        @RequiredReadAction
        public TextRange getHighlightRange() {
            PsiElement element = myTarget.getHighlightElement();
            return element != null ? element.getTextRange() : null;
        }

        @Override
        public boolean needForceSmartStepInto() {
            return myTarget instanceof ForceSmartStepIntoSource forceSmartStepIntoSource && forceSmartStepIntoSource.needForceSmartStepInto();
        }
    }
}
