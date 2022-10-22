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
import com.intellij.java.debugger.impl.DebuggerInvocationUtil;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.HotSwapProgress;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import consulo.application.impl.internal.progress.AbstractProgressIndicatorExBase;
import consulo.application.progress.PerformInBackgroundOption;
import consulo.application.progress.ProgressIndicator;
import consulo.ide.impl.idea.openapi.progress.impl.BackgroundableProcessIndicator;
import consulo.ide.impl.idea.openapi.progress.util.ProgressWindow;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.wm.ToolWindowId;
import consulo.ui.ex.MessageCategory;
import consulo.util.collection.primitive.ints.IntMaps;
import consulo.util.collection.primitive.ints.IntObjectMap;
import consulo.util.lang.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HotSwapProgressImpl extends HotSwapProgress {
    static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("HotSwap", ToolWindowId.DEBUG, true);

    IntObjectMap<List<String>> myMessages = IntMaps.newIntObjectHashMap();
    private final ProgressWindow myProgressWindow;
    private String myTitle = DebuggerBundle.message("progress.hot.swap.title");

    public HotSwapProgressImpl(Project project) {
        super(project);
        myProgressWindow = new BackgroundableProcessIndicator(getProject(), myTitle, new PerformInBackgroundOption() {
            @Override
            public boolean shouldStartInBackground() {
                return DebuggerSettings.getInstance().HOTSWAP_IN_BACKGROUND;
            }

            @Override
            public void processSentToBackground() {
            }

        }, null, null, true);
        myProgressWindow.addStateDelegate(new AbstractProgressIndicatorExBase() {
            @Override
            public void cancel() {
                super.cancel();
                HotSwapProgressImpl.this.cancel();
            }
        });
    }

    @Override
    public void finished() {
        super.finished();

        final List<String> errors = getMessages(MessageCategory.ERROR);
        final List<String> warnings = getMessages(MessageCategory.WARNING);
        if (!errors.isEmpty()) {
            NOTIFICATION_GROUP.createNotification(DebuggerBundle.message("status.hot.swap.completed.with.errors"), buildMessage(errors),
                    consulo.project.ui.notification.NotificationType.ERROR, null).notify(getProject());
        } else if (!warnings.isEmpty()) {
            NOTIFICATION_GROUP.createNotification(DebuggerBundle.message("status.hot.swap.completed.with.warnings"), buildMessage(warnings),
                    consulo.project.ui.notification.NotificationType.WARNING, null).notify(getProject());
        } else if (!myMessages.isEmpty()) {
            List<String> messages = new ArrayList<>();
            for (int category : myMessages.keys()) {
                messages.addAll(getMessages(category));
            }
            NOTIFICATION_GROUP.createNotification(buildMessage(messages), consulo.project.ui.notification.NotificationType.INFORMATION).notify(getProject());
        }
    }

    private List<String> getMessages(int category) {
        final List<String> messages = myMessages.get(category);
        return messages == null ? Collections.<String>emptyList() : messages;
    }

    private static String buildMessage(List<String> messages) {
        return StringUtil.trimEnd(StringUtil.join(messages, " \n").trim(), ";");
    }

    @Override
    public void addMessage(DebuggerSession session, final int type, final String text) {
        List<String> messages = myMessages.get(type);
        if (messages == null) {
            messages = new ArrayList<>();
            myMessages.put(type, messages);
        }
        final StringBuilder builder = new StringBuilder();
        builder.append(session.getSessionName()).append(": ").append(text).append(";");
        messages.add(builder.toString());
    }

    @Override
    public void setText(final String text) {
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
            @Override
            public void run() {
                if (!myProgressWindow.isCanceled() && myProgressWindow.isRunning()) {
                    myProgressWindow.setText(text);
                }
            }
        }, myProgressWindow.getModalityState());

    }

    @Override
    public void setTitle(final String text) {
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
            @Override
            public void run() {
                if (!myProgressWindow.isCanceled() && myProgressWindow.isRunning()) {
                    myProgressWindow.setTitle(text);
                }
            }
        }, myProgressWindow.getModalityState());

    }

    @Override
    public void setFraction(final double v) {
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
            @Override
            public void run() {
                if (!myProgressWindow.isCanceled() && myProgressWindow.isRunning()) {
                    myProgressWindow.setFraction(v);
                }
            }
        }, myProgressWindow.getModalityState());
    }

    @Override
    public boolean isCancelled() {
        return myProgressWindow.isCanceled();
    }

    public ProgressIndicator getProgressIndicator() {
        return myProgressWindow;
    }

    @Override
    public void setDebuggerSession(DebuggerSession session) {
        myTitle = DebuggerBundle.message("progress.hot.swap.title") + " : " + session.getSessionName();
        myProgressWindow.setTitle(myTitle);
    }
}
