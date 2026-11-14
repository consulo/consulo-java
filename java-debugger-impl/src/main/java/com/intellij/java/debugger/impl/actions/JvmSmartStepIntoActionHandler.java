// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.progress.ProgressManager;
import consulo.component.ProcessCanceledException;
import consulo.document.util.TextRange;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.frame.XSuspendContext;
import consulo.execution.debug.step.XSmartStepIntoHandler;
import consulo.execution.debug.step.XSmartStepIntoVariant;
import consulo.language.psi.PsiElement;
import consulo.logging.Logger;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class JvmSmartStepIntoActionHandler extends XSmartStepIntoHandler<JvmSmartStepIntoActionHandler.JvmSmartStepIntoVariant> {
    private static final Logger LOG = Logger.getInstance(JvmSmartStepIntoActionHandler.class);

    private final DebuggerSession mySession;

    public JvmSmartStepIntoActionHandler(DebuggerSession session) {
        mySession = session;
    }

    public CompletableFuture<List<JvmSmartStepIntoVariant>> computeSmartStepVariantsAsync(XSourcePosition position) {
        return findVariants(position, true);
    }

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

    /**
     * The platform asks for the variants synchronously on the EDT. The targets are computed by {@link #computeSmartStepVariantsAsync},
     * the EDT waits for them under a cancellable modal progress, so the handlers can compute them on the debugger manager thread.
     */
    @Override
    @RequiredUIAccess
    public List<JvmSmartStepIntoVariant> computeSmartStepVariants(XSourcePosition position) {
        CompletableFuture<List<JvmSmartStepIntoVariant>> future = computeSmartStepVariantsAsync(position);
        if (!future.isDone()) {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> {
                    while (!future.isDone()) {
                        ProgressManager.checkCanceled();
                        try {
                            future.get(10, TimeUnit.MILLISECONDS);
                        }
                        catch (TimeoutException ignored) {
                        }
                        catch (InterruptedException e) {
                            throw new ProcessCanceledException(e);
                        }
                        catch (ExecutionException ignored) {
                            return;
                        }
                    }
                },
                JavaDebuggerLocalize.titleSmartStepPopup(),
                true,
                mySession.getProject()
            );
            if (!future.isDone()) {
                // cancelled by the user, stay at the current position
                future.cancel(false);
                throw new ProcessCanceledException();
            }
        }
        try {
            return future.join();
        }
        catch (CancellationException | CompletionException e) {
            LOG.debug(e);
            return List.of();
        }
    }

    @Override
    public String getPopupTitle(XSourcePosition position) {
        return JavaDebuggerLocalize.titleSmartStepPopup().get();
    }

    @Override
    @RequiredUIAccess
    public void startStepInto(JvmSmartStepIntoVariant variant, @Nullable XSuspendContext context) {
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
