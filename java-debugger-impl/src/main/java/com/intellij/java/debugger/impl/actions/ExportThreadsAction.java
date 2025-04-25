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

import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.ui.ExportDialog;
import consulo.platform.Platform;
import consulo.platform.base.localize.ActionLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @author Jeka
 */
public class ExportThreadsAction extends AnAction {
    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();

        if (context.getDebuggerSession() != null) {
            String destinationDirectory = "";
            VirtualFile baseDir = project.getBaseDir();
            if (baseDir != null) {
                destinationDirectory = baseDir.getPresentableUrl();
            }

            ExportDialog dialog = new ExportDialog(context.getDebugProcess(), destinationDirectory);
            dialog.show();
            if (dialog.isOK()) {
                try {
                    File file = new File(dialog.getFilePath());
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                        String text = StringUtil.convertLineSeparators(
                            dialog.getTextToSave(),
                            Platform.current().os().lineSeparator().getSeparatorString()
                        );
                        writer.write(text);
                    }
                }
                catch (IOException ex) {
                    Messages.showMessageDialog(
                        project,
                        ex.getMessage(),
                        ActionLocalize.actionExportthreadsText().get(),
                        UIUtil.getErrorIcon()
                    );
                }
            }
        }
    }

    @Override
    @RequiredUIAccess
    public void update(@Nonnull AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        Project project = event.getData(Project.KEY);
        if (project == null) {
            presentation.setEnabled(false);
            return;
        }
        DebuggerSession debuggerSession = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
        presentation.setEnabled(debuggerSession != null && debuggerSession.isPaused());
    }
}