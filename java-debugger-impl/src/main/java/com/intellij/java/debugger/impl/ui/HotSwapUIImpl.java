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
package com.intellij.java.debugger.impl.ui;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.HotSwapFile;
import com.intellij.java.debugger.impl.HotSwapManager;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.annotation.component.ServiceImpl;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.Task;
import consulo.compiler.CompilerManager;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationType;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.collection.Lists;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

/**
 * User: lex
 * Date: Oct 2, 2003
 * Time: 6:00:55 PM
 */
@Singleton
@ServiceImpl
public class HotSwapUIImpl extends HotSwapUI {

    protected final List<HotSwapVetoableListener> myListeners = Lists.newLockFreeCopyOnWriteList();
    private boolean myAskBeforeHotswap = true;
    private final Project myProject;
    protected boolean myPerformHotswapAfterThisCompilation = true;

    @Inject
    public HotSwapUIImpl(final Project project) {
        myProject = project;
    }

    @Override
    public void addListener(HotSwapVetoableListener listener) {
        myListeners.add(listener);
    }

    @Override
    public void removeListener(HotSwapVetoableListener listener) {
        myListeners.remove(listener);
    }

    private boolean shouldDisplayHangWarning(DebuggerSettings settings, List<DebuggerSession> sessions) {
        if (!settings.HOTSWAP_HANG_WARNING_ENABLED) {
            return false;
        }

        for (DebuggerSession session : sessions) {
            if (session.isPaused()) {
                return true;
            }
        }
        return false;
    }

    protected void hotSwapSessions(final List<DebuggerSession> sessions) {
        final boolean shouldAskBeforeHotswap = myAskBeforeHotswap;
        myAskBeforeHotswap = true;

        final DebuggerSettings settings = DebuggerSettings.getInstance();
        final String runHotswap = settings.RUN_HOTSWAP_AFTER_COMPILE;
        final boolean shouldDisplayHangWarning = shouldDisplayHangWarning(settings, sessions);

        if (shouldAskBeforeHotswap && DebuggerSettings.RUN_HOTSWAP_NEVER.equals(runHotswap)) {
            return;
        }

        new Task.Backgroundable(myProject, "Looking Classes for HotSwap...", false) {
            private Map<DebuggerSession, Map<String, HotSwapFile>> myModifiedClasses;

            @Override
            public void run(@Nonnull ProgressIndicator progressIndicator) {
                HotSwapProgressImpl progress = new HotSwapProgressImpl((Project) myProject, progressIndicator);

                myModifiedClasses = HotSwapManager.scanForModifiedClasses(sessions, progress);
            }

            @RequiredUIAccess
            @Override
            public void onFinished() {
                Project project = (Project) myProject;

                if (myModifiedClasses.isEmpty()) {
                    final String message = JavaDebuggerLocalize.statusHotswapUptodate().get();
                    HotSwapProgressImpl.NOTIFICATION_GROUP.createNotification(message, NotificationType.INFORMATION).notify(project);
                    return;
                }

                if (shouldAskBeforeHotswap && !DebuggerSettings.RUN_HOTSWAP_ALWAYS.equals(runHotswap)) {
                    final RunHotswapDialog dialog = new RunHotswapDialog(project, sessions, shouldDisplayHangWarning);
                    dialog.show();
                    if (!dialog.isOK()) {
                        for (DebuggerSession session : myModifiedClasses.keySet()) {
                            session.setModifiedClassesScanRequired(true);
                        }
                        return;
                    }
                    final Set<DebuggerSession> toReload = new HashSet<>(dialog.getSessionsToReload());
                    for (DebuggerSession session : myModifiedClasses.keySet()) {
                        if (!toReload.contains(session)) {
                            session.setModifiedClassesScanRequired(true);
                        }
                    }

                    myModifiedClasses.keySet().retainAll(toReload);
                }
                else {
                    if (shouldDisplayHangWarning) {
                        final int answer = Messages.showCheckboxMessageDialog(
                            DebuggerBundle.message("hotswap.dialog.hang.warning"),
                            DebuggerBundle.message("hotswap.dialog.title"),
                            new String[]{
                                "Perform &Reload Classes",
                                "&Skip Reload Classes"
                            },
                            CommonLocalize.dialogOptionsDoNotShow().get(),
                            false,
                            1,
                            1,
                            UIUtil.getWarningIcon(),
                            (exitCode, cb) -> {
                                settings.HOTSWAP_HANG_WARNING_ENABLED = !cb.isSelected();
                                return exitCode == DialogWrapper.OK_EXIT_CODE ? exitCode : DialogWrapper.CANCEL_EXIT_CODE;
                            }
                        );

                        if (answer == DialogWrapper.CANCEL_EXIT_CODE) {
                            for (DebuggerSession session : myModifiedClasses.keySet()) {
                                session.setModifiedClassesScanRequired(true);
                            }
                            return;
                        }
                    }
                }

                if (!myModifiedClasses.isEmpty()) {
                    Task.Backgroundable.queue(project, "Realoading Classes...", progressIndicator -> {
                        HotSwapProgressImpl progress = new HotSwapProgressImpl(project, progressIndicator);

                        HotSwapManager.reloadModifiedClasses(myModifiedClasses, progress);

                        progress.finished();
                    });
                }
            }
        }.queue();
    }

    @Override
    public void reloadChangedClasses(final DebuggerSession session, boolean compileBeforeHotswap) {
        dontAskHotswapAfterThisCompilation();
        if (compileBeforeHotswap) {
            CompilerManager.getInstance(session.getProject()).make(null);
        }
        else {
            if (session.isAttached()) {
                hotSwapSessions(Collections.singletonList(session));
            }
        }
    }

    @Override
    public void dontPerformHotswapAfterThisCompilation() {
        myPerformHotswapAfterThisCompilation = false;
    }

    public void dontAskHotswapAfterThisCompilation() {
        myAskBeforeHotswap = false;
    }
}
