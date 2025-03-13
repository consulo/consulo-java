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
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressIndicatorListener;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationType;
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

    private ProgressIndicator myProgressIndicator;

    public HotSwapProgressImpl(Project project, ProgressIndicator progressIndicator) {
        super(project);
        myProgressIndicator = progressIndicator;

        progressIndicator.addListener(new ProgressIndicatorListener() {
            @Override
            public void canceled() {
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
                NotificationType.ERROR, null).notify(getProject());
        }
        else if (!warnings.isEmpty()) {
            NOTIFICATION_GROUP.createNotification(DebuggerBundle.message("status.hot.swap.completed.with.warnings"), buildMessage(warnings),
                NotificationType.WARNING, null).notify(getProject());
        }
        else if (!myMessages.isEmpty()) {
            List<String> messages = new ArrayList<>();
            for (int category : myMessages.keys()) {
                messages.addAll(getMessages(category));
            }
            NOTIFICATION_GROUP.createNotification(buildMessage(messages), NotificationType.INFORMATION).notify(getProject());
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
        DebuggerInvocationUtil.invokeLater(getProject(), () -> {
            if (!myProgressIndicator.isCanceled() && myProgressIndicator.isRunning()) {
                myProgressIndicator.setText2(text);
            }
        }, myProgressIndicator.getModalityState());

    }

    @Override
    public void setTitle(final String text) {
        DebuggerInvocationUtil.invokeLater(getProject(), () -> {
            if (!myProgressIndicator.isCanceled() && myProgressIndicator.isRunning()) {
                myProgressIndicator.setText(text);
            }
        }, myProgressIndicator.getModalityState());

    }

    @Override
    public void setFraction(final double v) {
        DebuggerInvocationUtil.invokeLater(getProject(), () -> {
            if (!myProgressIndicator.isCanceled() && myProgressIndicator.isRunning()) {
                myProgressIndicator.setFraction(v);
            }
        }, myProgressIndicator.getModalityState());
    }

    @Override
    public boolean isCancelled() {
        return myProgressIndicator.isCanceled();
    }

    public ProgressIndicator getProgressIndicator() {
        return myProgressIndicator;
    }

    @Override
    public void setDebuggerSession(DebuggerSession session) {
        // TODO replace by another localize
        LocalizeValue title = LocalizeValue.join(JavaDebuggerLocalize.progressHotSwapTitle(), LocalizeValue.localizeTODO(" : "), LocalizeValue.of(session.getSessionName()));

        myProgressIndicator.setText(title.get());
    }
}
