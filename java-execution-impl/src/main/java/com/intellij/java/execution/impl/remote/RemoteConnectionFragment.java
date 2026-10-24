/*
 * Copyright 2013-2026 consulo.io
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
package com.intellij.java.execution.impl.remote;

import com.intellij.java.execution.configurations.RemoteConnection;
import consulo.disposer.Disposable;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.execution.localize.ExecutionLocalize;
import consulo.java.execution.localize.JavaExecutionLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.Platform;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ComboBox;
import consulo.ui.HasValidator.ValidationInfo;
import consulo.ui.TextBox;
import consulo.ui.TextBoxWithExtensions;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

/**
 * How a {@link RemoteConfiguration} connects to the JVM, with the JVM options to start it with; the shared memory transport
 * exists on Windows only.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
class RemoteConnectionFragment implements RunConfigurationFragment<RemoteConfiguration> {
    private enum Mode {
        ATTACH(JavaExecutionLocalize.remoteConfigurationModeAttach()),
        LISTEN(JavaExecutionLocalize.remoteConfigurationModeListen());

        private final LocalizeValue myText;

        Mode(LocalizeValue text) {
            myText = text;
        }
    }

    private enum Transport {
        SOCKET(ExecutionLocalize.remoteConfigurationSocketRadio()),
        SHMEM(ExecutionLocalize.remoteConfigurationSharedMemoryRadio());

        private final LocalizeValue myText;

        Transport(LocalizeValue text) {
            myText = text;
        }
    }

    private enum JDKVersionItem {
        JDK9(JavaExecutionLocalize.remoteConfigurationJdk9OrLater()) {
            @Override
            String getLaunchCommandLine(RemoteConnection connection) {
                String commandLine = JDK5to8.getLaunchCommandLine(connection);
                if (connection.isUseSockets() && !connection.isServerMode()) {
                    String address = connection.getAddress();
                    commandLine = commandLine.replace("address=" + address, "address=*:" + address);
                }
                return commandLine;
            }
        },
        JDK5to8(JavaExecutionLocalize.remoteConfigurationJdk5To8()) {
            @Override
            String getLaunchCommandLine(RemoteConnection connection) {
                return connection.getLaunchCommandLine().replace("-Xdebug", "").replace("-Xrunjdwp:", "-agentlib:jdwp=").trim();
            }
        },
        JDK1_4(JavaExecutionLocalize.remoteConfigurationJdk14()) {
            @Override
            String getLaunchCommandLine(RemoteConnection connection) {
                return connection.getLaunchCommandLine();
            }
        },
        JDK1_3(JavaExecutionLocalize.remoteConfigurationJdk13()) {
            @Override
            String getLaunchCommandLine(RemoteConnection connection) {
                return "-Xnoagent -Djava.compiler=NONE " + connection.getLaunchCommandLine();
            }
        };

        private final LocalizeValue myText;

        JDKVersionItem(LocalizeValue text) {
            myText = text;
        }

        abstract String getLaunchCommandLine(RemoteConnection connection);
    }

    private static final int MIN_PORT_VALUE = 0;
    private static final int MAX_PORT_VALUE = 0xFFFF;

    @Override
    public String getId() {
        return "remoteConnection";
    }

    @Override
    public LocalizeValue getName() {
        return JavaExecutionLocalize.remoteConfigurationFragmentConnection();
    }

    @Override
    public boolean isRemovable() {
        return false;
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<RemoteConfiguration> createEditor(Project project, Disposable parent) {
        return new Editor();
    }

    private static @Nullable ValidationInfo validatePort(@Nullable String port) {
        if (StringUtil.isEmpty(port)) {
            return null;
        }
        try {
            int portValue = Integer.parseInt(port);
            if (portValue >= MIN_PORT_VALUE && portValue <= MAX_PORT_VALUE) {
                return null;
            }
            return new ValidationInfo(JavaExecutionLocalize.remoteConfigurationPortOutOfRange().get());
        }
        catch (NumberFormatException e) {
            return new ValidationInfo(JavaExecutionLocalize.remoteConfigurationPortNotANumber().get());
        }
    }

    private static class Editor implements RunConfigurationFragmentEditor<RemoteConfiguration> {
        private final boolean myWindows = Platform.current().os().isWindows();
        private final ComboBox<Mode> myModeCombo;
        private final ComboBox<Transport> myTransportCombo;
        private final TextBox myHostName;
        private final TextBox myPort;
        private final TextBox myAddress;
        private final ComboBox<JDKVersionItem> myJdkVersionCombo;
        private final TextBoxWithExtensions myArgsBox;

        @RequiredUIAccess
        private Editor() {
            myModeCombo = ComboBox.create(Mode.values());
            myModeCombo.setTextRenderer(mode -> mode == null ? LocalizeValue.empty() : mode.myText);
            myModeCombo.setValue(Mode.ATTACH, false);

            myTransportCombo = ComboBox.create(Transport.values());
            myTransportCombo.setTextRenderer(transport -> transport == null ? LocalizeValue.empty() : transport.myText);
            myTransportCombo.setValue(Transport.SOCKET, false);

            myHostName = TextBox.create();
            myPort = TextBox.create(Integer.toString(MAX_PORT_VALUE));
            myPort.addValidator(RemoteConnectionFragment::validatePort);
            myAddress = TextBox.create();

            myJdkVersionCombo = ComboBox.create(JDKVersionItem.values());
            myJdkVersionCombo.setTextRenderer(item -> item == null ? LocalizeValue.empty() : item.myText);
            myJdkVersionCombo.setValue(JDKVersionItem.JDK9, false);

            myArgsBox = TextBoxWithExtensions.create();
            myArgsBox.setEditable(false);
            myArgsBox.addLastExtension(new TextBoxWithExtensions.Extension(
                false,
                PlatformIconGroup.actionsCopy(),
                null,
                event -> UIAccess.current().getClipboard().setText(StringUtil.notNullize(myArgsBox.getValue()))
            ));

            myModeCombo.addValueListener(event -> updateArgsText());
            myTransportCombo.addValueListener(event -> {
                updateTransportState();
                updateArgsText();
            });
            myHostName.addValueListener(event -> updateArgsText());
            myPort.addValueListener(event -> updateArgsText());
            myAddress.addValueListener(event -> updateArgsText());
            myJdkVersionCombo.addValueListener(event -> updateArgsText());

            updateTransportState();
            updateArgsText();
        }

        @RequiredUIAccess
        @Override
        public void addRows(RunConfigurationFragmentLayout layout) {
            layout.addLabeled(ExecutionLocalize.remoteConfigurationDebuggerModeLabel(), myModeCombo);
            if (myWindows) {
                layout.addLabeled(ExecutionLocalize.remoteConfigurationTransportLabel(), myTransportCombo);
                layout.addLabeled(ExecutionLocalize.remoteConfigurationSharedMemoryAddressLabel(), myAddress);
            }
            layout.addLabeled(ExecutionLocalize.remoteConfigurationHostLabel(), myHostName);
            layout.addLabeled(ExecutionLocalize.remoteConfigurationPortLabel(), myPort);
            layout.addLabeled(JavaExecutionLocalize.remoteConfigurationJvmArgumentsFormatLabel(), myJdkVersionCombo);
            layout.addLabeled(JavaExecutionLocalize.remoteConfigurationCommandLineArgumentsLabel(), myArgsBox);
        }

        private boolean isSocket() {
            return !myWindows || myTransportCombo.getValue() != Transport.SHMEM;
        }

        @RequiredUIAccess
        private void updateTransportState() {
            boolean socket = isSocket();
            myHostName.setEnabled(socket);
            myPort.setEnabled(socket);
            myAddress.setEnabled(!socket);
        }

        @RequiredUIAccess
        private void updateArgsText() {
            boolean useSockets = isSocket();

            RemoteConnection connection = new RemoteConnection(
                useSockets,
                StringUtil.notNullize(myHostName.getValue()).trim(),
                StringUtil.notNullize(useSockets ? myPort.getValue() : myAddress.getValue()).trim(),
                myModeCombo.getValue() == Mode.LISTEN
            );

            JDKVersionItem versionItem = myJdkVersionCombo.getValue();
            myArgsBox.setValue((versionItem == null ? JDKVersionItem.JDK9 : versionItem).getLaunchCommandLine(connection));
        }

        @RequiredUIAccess
        @Override
        public void reset(RemoteConfiguration target) {
            RemoteConfigurationOptions options = target.getOptions();
            myModeCombo.setValue(options.isServerMode() ? Mode.LISTEN : Mode.ATTACH, false);
            myTransportCombo.setValue(options.isUseSocketTransport() ? Transport.SOCKET : Transport.SHMEM, false);
            myAddress.setValue(StringUtil.notNullize(options.getShmemAddress()), false);
            myHostName.setValue(StringUtil.notNullize(options.getHost()), false);
            myPort.setValue(StringUtil.notNullize(options.getPort()), false);

            updateTransportState();
            updateArgsText();
        }

        @RequiredUIAccess
        @Override
        public void apply(RemoteConfiguration target) {
            RemoteConfigurationOptions options = target.getOptions();
            options.setHost(StringUtil.notNullize(myHostName.getValue()).trim());
            options.setPort(StringUtil.notNullize(myPort.getValue()).trim());
            options.setShmemAddress(StringUtil.notNullize(myAddress.getValue()).trim());
            options.setUseSocketTransport(isSocket());
            options.setServerMode(myModeCombo.getValue() == Mode.LISTEN);
        }
    }
}
