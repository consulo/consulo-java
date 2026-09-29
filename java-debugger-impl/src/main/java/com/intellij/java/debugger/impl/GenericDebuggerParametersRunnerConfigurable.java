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
package com.intellij.java.debugger.impl;

import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.debug.XDebuggerManager;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.ui.Button;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.RadioGroup;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

public class GenericDebuggerParametersRunnerConfigurable extends SettingsEditor<GenericDebuggerRunnerSettings> {
    private static final Logger LOG = Logger.getInstance(GenericDebuggerParametersRunnerConfigurable.class);

    private final Project myProject;

    private boolean myIsLocal;

    private @Nullable RadioGroup<Integer> myTransportGroup;
    private @Nullable Label myPortLabel;
    private @Nullable TextBox myPortField;
    private @Nullable Label myAddressLabel;
    private @Nullable TextBox myAddressField;
    private @Nullable Button myDebuggerSettingsButton;

    public GenericDebuggerParametersRunnerConfigurable(Project project) {
        myProject = project;
    }

    @RequiredUIAccess
    @Override
    protected Component createUIComponent() {
        RadioGroup<Integer> transportGroup = RadioGroup.create();

        HorizontalLayout transportLayout = HorizontalLayout.create();
        transportLayout.add(transportGroup.newButton(
            JavaDebuggerLocalize.labelGenericDebuggerParametersPatcherConfigurableSocket(),
            DebuggerSettings.SOCKET_TRANSPORT
        ));
        transportLayout.add(transportGroup.newButton(
            JavaDebuggerLocalize.labelGenericDebuggerParametersPatcherConfigurableShmem(),
            DebuggerSettings.SHMEM_TRANSPORT
        ));
        transportGroup.setValue(DebuggerSettings.SOCKET_TRANSPORT);
        transportGroup.addValueListener(value -> {
            suggestAvailablePortIfNotSpecified();
            updateUI();
        });
        myTransportGroup = transportGroup;

        Label portLabel = Label.create(JavaDebuggerLocalize.labelGenericDebuggerParametersPatcherConfigurablePort());
        TextBox portField = TextBox.create();
        myPortLabel = portLabel;
        myPortField = portField;

        Label addressLabel = Label.create(JavaDebuggerLocalize.labelGenericDebuggerParametersPatcherConfigurableShmemAddress());
        TextBox addressField = TextBox.create();
        myAddressLabel = addressLabel;
        myAddressField = addressField;

        Button debuggerSettingsButton = Button.create(JavaDebuggerLocalize.buttonDebuggerSettings(), event -> {
            XDebuggerManager.getInstance(myProject).showSettings();

            if (myIsLocal) {
                setTransport(DebuggerSettings.getInstance().DEBUGGER_TRANSPORT);
            }

            suggestAvailablePortIfNotSpecified();
            updateUI();
        });
        myDebuggerSettingsButton = debuggerSettingsButton;

        FormBuilder builder = FormBuilder.create();
        builder.addLabeled(JavaDebuggerLocalize.labelGenericDebuggerParametersPatcherConfigurableTransport(), transportLayout);
        builder.addLabeled(portLabel, portField);
        builder.addLabeled(addressLabel, addressField);
        builder.addBottom(debuggerSettingsButton);

        updateUI();
        return builder.build();
    }

    private boolean isSocket() {
        return getTransport() == DebuggerSettings.SOCKET_TRANSPORT;
    }

    @RequiredUIAccess
    private void updateUI() {
        boolean socket = isSocket();

        setVisible(myPortLabel, socket);
        setVisible(myPortField, socket);
        setVisible(myAddressLabel, !socket);
        setVisible(myAddressField, !socket);

        TextBox addressField = myAddressField;
        if (addressField != null) {
            addressField.setEditable(!myIsLocal);
        }

        TextBox portField = myPortField;
        if (portField != null) {
            portField.setEditable(!myIsLocal);
        }

        Button debuggerSettingsButton = myDebuggerSettingsButton;
        if (debuggerSettingsButton != null) {
            debuggerSettingsButton.setVisible(myIsLocal);
        }
    }

    @RequiredUIAccess
    private static void setVisible(@Nullable Component component, boolean visible) {
        if (component != null) {
            component.setVisible(visible);
        }
    }

    @RequiredUIAccess
    @Override
    public void resetEditorFrom(GenericDebuggerRunnerSettings runnerSettings) {
        myIsLocal = runnerSettings.LOCAL;

        RadioGroup<Integer> transportGroup = myTransportGroup;
        if (transportGroup != null) {
            transportGroup.setValue(runnerSettings.getTransport(), false);
        }

        setPort(StringUtil.notNullize(runnerSettings.getDebugPort()));
        suggestAvailablePortIfNotSpecified();
        updateUI();
    }

    @RequiredUIAccess
    private void suggestAvailablePortIfNotSpecified() {
        String port = getPort();
        boolean portSpecified = !StringUtil.isEmpty(port);
        if (isSocket()) {
            try {
                Integer.parseInt(port);
            }
            catch (NumberFormatException e) {
                portSpecified = false;
            }
        }

        if (!portSpecified) {
            try {
                setPort(DebuggerUtils.getInstance().findAvailableDebugAddress(isSocket()));
            }
            catch (ExecutionException e) {
                LOG.info(e);
            }
        }
    }

    private int getTransport() {
        if (myIsLocal) {
            return DebuggerSettings.getInstance().DEBUGGER_TRANSPORT;
        }

        RadioGroup<Integer> transportGroup = myTransportGroup;
        Integer transport = transportGroup != null ? transportGroup.getValue() : null;
        return transport != null ? transport : DebuggerSettings.SOCKET_TRANSPORT;
    }

    private String getPort() {
        TextBox field = isSocket() ? myPortField : myAddressField;
        return field != null ? StringUtil.notNullize(field.getValue()) : "";
    }

    @RequiredUIAccess
    private void setTransport(int transport) {
        RadioGroup<Integer> transportGroup = myTransportGroup;
        if (transportGroup != null) {
            transportGroup.setValue(transport, false);
        }
    }

    @RequiredUIAccess
    private void setPort(String port) {
        TextBox field = isSocket() ? myPortField : myAddressField;
        if (field != null) {
            field.setValue(port);
        }
    }

    private void checkPort() throws ConfigurationException {
        TextBox portField = myPortField;
        String port = portField != null ? StringUtil.notNullize(portField.getValue()) : "";
        if (isSocket() && !port.isEmpty()) {
            try {
                int value = Integer.parseInt(port);
                if (value < 0 || value > 0xffff) {
                    throw new NumberFormatException();
                }
            }
            catch (NumberFormatException e) {
                throw new ConfigurationException(JavaDebuggerLocalize.errorTextInvalidPort());
            }
        }
    }

    @RequiredUIAccess
    @Override
    public void applyEditorTo(GenericDebuggerRunnerSettings runnerSettings) throws ConfigurationException {
        runnerSettings.LOCAL = myIsLocal;
        checkPort();
        runnerSettings.setDebugPort(getPort());
        if (!myIsLocal) {
            runnerSettings.setTransport(getTransport());
        }
    }
}
