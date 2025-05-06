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
package com.intellij.java.debugger.impl;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.engine.SuspendManagerUtil;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.java.language.psi.PsiMethod;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XSourcePosition;
import consulo.fileEditor.FileEditorManager;
import consulo.ide.impl.idea.codeInsight.daemon.impl.IdentifierHighlighterPass;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.Couple;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DebuggerContextUtil {
    @RequiredUIAccess
    public static void setStackFrame(DebuggerStateManager manager, StackFrameProxyImpl stackFrame) {
        UIAccess.assertIsUIThread();

        DebuggerContextImpl context = manager.getContext();

        DebuggerSession session = context.getDebuggerSession();
        SuspendContextImpl threadSuspendContext =
            SuspendManagerUtil.getSuspendContextForThread(context.getSuspendContext(), stackFrame.threadProxy());
        DebuggerContextImpl newContext =
            DebuggerContextImpl.createDebuggerContext(session, threadSuspendContext, stackFrame.threadProxy(), stackFrame);

        manager.setState(
            newContext,
            session != null ? session.getState() : DebuggerSession.State.DISPOSED,
            DebuggerSession.Event.REFRESH
        );
    }

    @RequiredUIAccess
    public static void setThread(DebuggerStateManager contextManager, ThreadDescriptorImpl item) {
        UIAccess.assertIsUIThread();

        DebuggerSession session = contextManager.getContext().getDebuggerSession();
        DebuggerContextImpl newContext =
            DebuggerContextImpl.createDebuggerContext(session, item.getSuspendContext(), item.getThreadReference(), null);

        contextManager.setState(
            newContext,
            session != null ? session.getState() : DebuggerSession.State.DISPOSED,
            DebuggerSession.Event.CONTEXT
        );
    }

    @Nonnull
    public static DebuggerContextImpl createDebuggerContext(@Nonnull DebuggerSession session, SuspendContextImpl suspendContext) {
        return DebuggerContextImpl.createDebuggerContext(
            session,
            suspendContext,
            suspendContext != null ? suspendContext.getThread() : null,
            null
        );
    }

    public static SourcePosition findNearest(@Nonnull DebuggerContextImpl context, @Nonnull PsiElement psi, @Nonnull PsiFile file) {
        DebuggerSession session = context.getDebuggerSession();
        if (session != null) {
            try {
                XDebugSession debugSession = session.getXDebugSession();
                if (debugSession != null) {
                    XSourcePosition position = debugSession.getCurrentPosition();
                    Editor editor = FileEditorManager.getInstance(file.getProject()).getSelectedTextEditor(true);

                    //Editor editor = fileEditor instanceof TextEditorImpl ? ((TextEditorImpl)fileEditor).getEditor() : null;
                    if (editor != null && position != null && file.getVirtualFile().equals(position.getFile())) {
                        PsiMethod method = PsiTreeUtil.getParentOfType(PositionUtil.getContextElement(context), PsiMethod.class, false);
                        Couple<Collection<TextRange>> usages =
                            IdentifierHighlighterPass.getHighlightUsages(psi, method != null ? method : file, false);
                        List<TextRange> ranges = new ArrayList<>();
                        ranges.addAll(usages.first);
                        ranges.addAll(usages.second);
                        int breakPointLine = position.getLine();
                        int bestLine = -1;
                        int bestOffset = -1;
                        for (TextRange range : ranges) {
                            int line = editor.offsetToLogicalPosition(range.getStartOffset()).line;
                            if (line > bestLine && line < breakPointLine) {
                                bestLine = line;
                                bestOffset = range.getStartOffset();
                            }
                            else if (line == breakPointLine) {
                                bestOffset = range.getStartOffset();
                                break;
                            }
                        }
                        if (bestOffset > -1) {
                            return SourcePosition.createFromOffset(file, bestOffset);
                        }
                    }
                }
            }
            catch (Exception ignore) {
            }
        }
        return null;
    }
}
