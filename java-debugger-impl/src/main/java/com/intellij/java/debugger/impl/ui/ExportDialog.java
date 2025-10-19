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

/**
 * created at Dec 14, 2001
 *
 * @author Jeka
 */
package com.intellij.java.debugger.impl.ui;

import com.intellij.java.debugger.impl.DebuggerInvocationUtil;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.HelpID;
import com.intellij.java.debugger.impl.actions.ThreadDumpAction;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.jdi.VirtualMachineProxyImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.MessageDescriptor;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.application.HelpManager;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.internal.com.sun.jdi.*;
import consulo.project.Project;
import consulo.ui.ModalityState;
import consulo.ui.ex.awt.*;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;

public class ExportDialog extends DialogWrapper {
    private final JTextArea myTextArea = new JTextArea();
    private TextFieldWithBrowseButton myTfFilePath;
    private final Project myProject;
    private final DebugProcessImpl myDebugProcess;
    private final CopyToClipboardAction myCopyToClipboardAction = new CopyToClipboardAction();
    private static final @NonNls String DEFAULT_REPORT_FILE_NAME = "threads_report.txt";

    public ExportDialog(DebugProcessImpl debugProcess, String destinationDirectory) {
        super(debugProcess.getProject(), true);
        myDebugProcess = debugProcess;
        myProject = debugProcess.getProject();
        setTitle(JavaDebuggerLocalize.threadsExportDialogTitle());
        setOKButtonText(JavaDebuggerLocalize.buttonSave());

        init();

        setOKActionEnabled(false);
        myCopyToClipboardAction.setEnabled(false);

        myTextArea.setText(MessageDescriptor.EVALUATING.getLabel().get());
        debugProcess.getManagerThread().invoke(new ExportThreadsCommand(myProject.getApplication().getModalityStateForComponent(myTextArea)));

        myTfFilePath.setText(destinationDirectory + File.separator + DEFAULT_REPORT_FILE_NAME);
        setHorizontalStretch(1.5f);
    }

    @Nonnull
    protected Action[] createActions() {
        return new Action[]{getOKAction(), myCopyToClipboardAction, getCancelAction(), getHelpAction()};
    }

    protected void doHelpAction() {
        HelpManager.getInstance().invokeHelp(HelpID.EXPORT_THREADS);
    }

    protected JComponent createNorthPanel() {
        JPanel box = new JPanel(new BorderLayout());
        box.add(new JLabel(JavaDebuggerLocalize.labelThreadsExportDialogFile().get()), BorderLayout.WEST);
        myTfFilePath = new TextFieldWithBrowseButton();
        myTfFilePath.addBrowseFolderListener(null, null, myProject, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
        box.add(myTfFilePath, BorderLayout.CENTER);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(box, BorderLayout.CENTER);
        panel.add(Box.createVerticalStrut(7), BorderLayout.SOUTH);
        return panel;
    }

    protected JComponent createCenterPanel() {
        myTextArea.setEditable(false);
        JScrollPane pane = ScrollPaneFactory.createScrollPane(myTextArea);
        pane.setPreferredSize(new Dimension(400, 300));
        return pane;
    }

    protected void doOKAction() {
        String path = myTfFilePath.getText();
        File file = new File(path);
        if (file.isDirectory()) {
            Messages.showMessageDialog(
                myProject,
                JavaDebuggerLocalize.errorThreadsExportDialogFileIsDirectory().get(),
                JavaDebuggerLocalize.threadsExportDialogTitle().get(),
                UIUtil.getErrorIcon()
            );
        }
        else if (file.exists()) {
            int answer = Messages.showYesNoDialog(
                myProject,
                JavaDebuggerLocalize.errorThreadsExportDialogFileAlreadyExists(path).get(),
                JavaDebuggerLocalize.threadsExportDialogTitle().get(),
                UIUtil.getQuestionIcon()
            );
            if (answer == 0) {
                super.doOKAction();
            }
        }
        else {
            super.doOKAction();
        }
    }

    public String getFilePath() {
        return myTfFilePath.getText();
    }

    public String getTextToSave() {
        return myTextArea.getText();
    }

    protected String getDimensionServiceKey() {
        return "#com.intellij.debugger.ui.ExportDialog";
    }

    public static String getExportThreadsText(VirtualMachineProxyImpl vmProxy) {
        final StringBuilder buffer = new StringBuilder(512);
        List<ThreadReference> threads = vmProxy.getVirtualMachine().allThreads();
        for (ThreadReference threadReference : threads) {
            final String name = threadName(threadReference);
            if (name == null) {
                continue;
            }
            buffer.append(name);
            ReferenceType referenceType = threadReference.referenceType();
            if (referenceType != null) {
                //noinspection HardCodedStringLiteral
                Field daemon = referenceType.fieldByName("daemon");
                if (daemon != null) {
                    Value value = threadReference.getValue(daemon);
                    if (value instanceof BooleanValue && ((BooleanValue) value).booleanValue()) {
                        buffer.append(" ").append(JavaDebuggerLocalize.threadsExportAttributeLabelDaemon().get());
                    }
                }

                //noinspection HardCodedStringLiteral
                Field priority = referenceType.fieldByName("priority");
                if (priority != null) {
                    Value value = threadReference.getValue(priority);
                    if (value instanceof IntegerValue) {
                        buffer.append(", ").append(JavaDebuggerLocalize.threadsExportAttributeLabelPriority(((IntegerValue) value).intValue()).get());
                    }
                }
            }

            ThreadGroupReference groupReference = threadReference.threadGroup();
            if (groupReference != null) {
                buffer.append(", ").append(JavaDebuggerLocalize.threadsExportAttributeLabelGroup(groupReference.name()).get());
            }
            buffer.append(", ").append(
                JavaDebuggerLocalize.threadsExportAttributeLabelStatus(DebuggerUtilsEx.getThreadStatusText(threadReference.status())).get());

            try {
                if (vmProxy.canGetOwnedMonitorInfo() && vmProxy.canGetMonitorInfo()) {
                    List<ObjectReference> list = threadReference.ownedMonitors();
                    for (ObjectReference reference : list) {
                        final List<ThreadReference> waiting = reference.waitingThreads();
                        for (ThreadReference thread : waiting) {
                            final String waitingThreadName = threadName(thread);
                            if (waitingThreadName != null) {
                                buffer.append("\n\t ").append(JavaDebuggerLocalize.threadsExportAttributeLabelBlocksThread(waitingThreadName).get());
                            }
                        }
                    }
                }

                ObjectReference waitedMonitor = vmProxy.canGetCurrentContendedMonitor() ? threadReference.currentContendedMonitor() : null;
                if (waitedMonitor != null) {
                    if (vmProxy.canGetMonitorInfo()) {
                        ThreadReference waitedThread = waitedMonitor.owningThread();
                        if (waitedThread != null) {
                            final String waitedThreadName = threadName(waitedThread);
                            if (waitedThreadName != null) {
                                buffer.append("\n\t ").append(JavaDebuggerLocalize.threadsExportAttributeLabelWaitingForThread(waitedThreadName, ThreadDumpAction.renderObject(waitedMonitor)).get());
                            }
                        }
                    }
                }

                final List<StackFrame> frames = threadReference.frames();
                for (StackFrame stackFrame : frames) {
                    final Location location = stackFrame.location();
                    buffer.append("\n\t  ").append(renderLocation(location));
                }
            }
            catch (IncompatibleThreadStateException e) {
                buffer.append("\n\t ").append(JavaDebuggerLocalize.threadsExportAttributeErrorIncompatibleState().get());
            }
            buffer.append("\n\n");
        }
        return buffer.toString();
    }

    private static String renderLocation(final Location location) {
        String sourceName;
        try {
            sourceName = location.sourceName();
        }
        catch (AbsentInformationException e) {
            sourceName = "Unknown Source";
        }
        return JavaDebuggerLocalize.exportThreadsStackframeFormat(location.declaringType().name() + "." + location.method().name(), sourceName, location.lineNumber()).get();
    }

    private static String threadName(ThreadReference threadReference) {
        try {
            return threadReference.name() + "@" + threadReference.uniqueID();
        }
        catch (ObjectCollectedException e) {
            return null;
        }
    }

    private class CopyToClipboardAction extends AbstractAction {
        public CopyToClipboardAction() {
            super(JavaDebuggerLocalize.buttonCopy().get());
            putValue(Action.SHORT_DESCRIPTION, JavaDebuggerLocalize.exportDialogCopyActionDescription().get());
        }

        public void actionPerformed(ActionEvent e) {
            String s = StringUtil.convertLineSeparators(myTextArea.getText());
            CopyPasteManager.getInstance().setContents(new StringSelection(s));
        }
    }

    private class ExportThreadsCommand extends DebuggerCommandImpl {
        protected ModalityState myModalityState;

        public ExportThreadsCommand(ModalityState modalityState) {
            myModalityState = modalityState;
        }

        private void setText(final String text) {
            DebuggerInvocationUtil.invokeLater(myProject, () -> {
                myTextArea.setText(text);
                setOKActionEnabled(true);
                myCopyToClipboardAction.setEnabled(true);
            }, myModalityState);
        }

        protected void action() {
            setText(getExportThreadsText(myDebugProcess.getVirtualMachineProxy()));
        }
    }
}
