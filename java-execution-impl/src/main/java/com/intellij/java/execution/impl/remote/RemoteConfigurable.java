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
package com.intellij.java.execution.impl.remote;

import com.intellij.java.execution.configurations.RemoteConnection;
import com.intellij.java.execution.impl.ui.UnifiedConfigurationModuleSelector;
import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.localize.ExecutionLocalize;
import consulo.java.execution.localize.JavaExecutionLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.Platform;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.HasValidator.ValidationInfo;
import consulo.ui.Label;
import consulo.ui.TextBox;
import consulo.ui.TextBoxWithExtensions;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

public class RemoteConfigurable extends SettingsEditor<RemoteConfiguration> {
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

    private final Project myProject;

    private @Nullable RemoteForm myForm;

    public RemoteConfigurable(Project project) {
        myProject = project;
    }

    @Override
    @RequiredUIAccess
    protected Component createUIComponent() {
        RemoteForm form = new RemoteForm();
        myForm = form;
        return form.myComponent;
    }

    @Override
    @RequiredUIAccess
    protected void resetEditorFrom(RemoteConfiguration configuration) {
        RemoteForm form = myForm;
        if (form != null) {
            form.reset(configuration);
        }
    }

    @Override
    @RequiredUIAccess
    protected void applyEditorTo(RemoteConfiguration configuration) throws ConfigurationException {
        RemoteForm form = myForm;
        if (form != null) {
            form.apply(configuration);
        }
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

    private class RemoteForm {
        private final ComboBox<Mode> myModeCombo;
        private final ComboBox<Transport> myTransportCombo;
        private final Label myHostLabel;
        private final TextBox myHostName;
        private final Label myPortLabel;
        private final TextBox myPort;
        private final Label myAddressLabel;
        private final TextBox myAddress;
        private final ComboBox<JDKVersionItem> myJdkVersionCombo;
        private final TextBoxWithExtensions myArgsBox;
        private final UnifiedConfigurationModuleSelector myModuleSelector;
        private final Component myComponent;

        @RequiredUIAccess
        private RemoteForm() {
            boolean windows = Platform.current().os().isWindows();

            myModeCombo = ComboBox.create(Mode.values());
            myModeCombo.setTextRenderer(mode -> mode == null ? LocalizeValue.empty() : mode.myText);
            myModeCombo.setValue(Mode.ATTACH, false);

            myTransportCombo = ComboBox.create(Transport.values());
            myTransportCombo.setTextRenderer(transport -> transport == null ? LocalizeValue.empty() : transport.myText);
            myTransportCombo.setValue(Transport.SOCKET, false);

            myHostLabel = Label.create(ExecutionLocalize.remoteConfigurationHostLabel());
            myHostName = TextBox.create();

            myPortLabel = Label.create(ExecutionLocalize.remoteConfigurationPortLabel());
            myPort = TextBox.create(Integer.toString(MAX_PORT_VALUE));
            myPort.addValidator(RemoteConfigurable::validatePort);

            myAddressLabel = Label.create(ExecutionLocalize.remoteConfigurationSharedMemoryAddressLabel());
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

            myModuleSelector = new UnifiedConfigurationModuleSelector(myProject, JavaExecutionLocalize.runConfigurationModuleWholeProject());

            FormBuilder builder = FormBuilder.create();
            builder.addLabeled(ExecutionLocalize.remoteConfigurationDebuggerModeLabel(), myModeCombo);
            if (windows) {
                builder.addLabeled(ExecutionLocalize.remoteConfigurationTransportLabel(), myTransportCombo);
                builder.addLabeled(myAddressLabel, myAddress);
            }
            builder.addLabeled(myHostLabel, myHostName);
            builder.addLabeled(myPortLabel, myPort);
            builder.addLabeled(JavaExecutionLocalize.remoteConfigurationJvmArgumentsFormatLabel(), myJdkVersionCombo);
            builder.addLabeled(JavaExecutionLocalize.remoteConfigurationCommandLineArgumentsLabel(), myArgsBox);
            builder.addLabeled(JavaExecutionLocalize.remoteConfigurationModuleLabel(), myModuleSelector.getComponent());
            myComponent = builder.build();

            myModeCombo.addValueListener(event -> updateArgsText());
            myTransportCombo.addValueListener(event -> {
                updateTransportVisibility();
                updateArgsText();
            });
            myHostName.addValueListener(event -> updateArgsText());
            myPort.addValueListener(event -> updateArgsText());
            myAddress.addValueListener(event -> updateArgsText());
            myJdkVersionCombo.addValueListener(event -> updateArgsText());

            updateTransportVisibility();
            updateArgsText();
        }

        private boolean isSocket() {
            return myTransportCombo.getValue() != Transport.SHMEM;
        }

        @RequiredUIAccess
        private void updateTransportVisibility() {
            boolean socket = isSocket();

            myHostLabel.setVisible(socket);
            myHostName.setVisible(socket);
            myPortLabel.setVisible(socket);
            myPort.setVisible(socket);

            myAddressLabel.setVisible(!socket);
            myAddress.setVisible(!socket);
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
        private void reset(RemoteConfiguration configuration) {
            boolean windows = Platform.current().os().isWindows();

            myModeCombo.setValue(configuration.SERVER_MODE ? Mode.LISTEN : Mode.ATTACH, false);

            if (windows) {
                myTransportCombo.setValue(configuration.USE_SOCKET_TRANSPORT ? Transport.SOCKET : Transport.SHMEM, false);
                if (!configuration.USE_SOCKET_TRANSPORT) {
                    myAddress.setValue(configuration.SHMEM_ADDRESS, false);
                }
            }

            if (!windows || configuration.USE_SOCKET_TRANSPORT) {
                configuration.USE_SOCKET_TRANSPORT = true;

                myHostName.setValue(configuration.HOST, false);
                myPort.setValue(configuration.PORT, false);
            }

            myModuleSelector.reset(configuration);

            updateTransportVisibility();
            updateArgsText();
        }

        @RequiredUIAccess
        private void apply(RemoteConfiguration configuration) {
            configuration.HOST = StringUtil.nullize(StringUtil.notNullize(myHostName.getValue()).trim());
            configuration.PORT = StringUtil.nullize(StringUtil.notNullize(myPort.getValue()).trim());
            configuration.SHMEM_ADDRESS = StringUtil.nullize(StringUtil.notNullize(myAddress.getValue()).trim());
            configuration.USE_SOCKET_TRANSPORT = isSocket();
            configuration.SERVER_MODE = myModeCombo.getValue() == Mode.LISTEN;
            myModuleSelector.applyTo(configuration);
        }
    }
}
