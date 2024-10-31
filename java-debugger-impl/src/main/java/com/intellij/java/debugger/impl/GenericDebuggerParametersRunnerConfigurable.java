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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.process.ExecutionException;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.ide.impl.idea.xdebugger.impl.settings.DebuggerConfigurable;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;

public class GenericDebuggerParametersRunnerConfigurable extends SettingsEditor<GenericDebuggerRunnerSettings> {
    private static final Logger LOGGER = Logger.getInstance(GenericDebuggerParametersRunnerConfigurable.class);

    private JPanel myPanel;
    private JTextField myAddressField;
    private JPanel myShMemPanel;
    private JPanel myPortPanel;
    private JTextField myPortField;
    private boolean myIsLocal = false;
    private JButton myDebuggerSettings;
    private JRadioButton mySocketTransport;
    private JRadioButton myShmemTransport;
    private JPanel myTransportPanel;

    public GenericDebuggerParametersRunnerConfigurable(final Project project) {
        myDebuggerSettings.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, DebuggerConfigurable.class);
                if (myIsLocal) {
                    setTransport(DebuggerSettings.getInstance().DEBUGGER_TRANSPORT);
                }
                suggestAvailablePortIfNotSpecified();
                updateUI();
            }
        });

        final ActionListener listener = new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                suggestAvailablePortIfNotSpecified();
                updateUI();
                myPanel.repaint();
            }
        };
        mySocketTransport.addActionListener(listener);
        myShmemTransport.addActionListener(listener);

        updateUI();

        myTransportPanel.setVisible(false);

        ButtonGroup group = new ButtonGroup();
        group.add(mySocketTransport);
        group.add(myShmemTransport);
    }

    private boolean isSocket() {
        return getTransport() == DebuggerSettings.SOCKET_TRANSPORT;
    }

    @Override
    @Nonnull
    public JComponent createEditor() {
        return myPanel;
    }

    private void updateUI() {
        myPortPanel.setVisible(isSocket());
        myShMemPanel.setVisible(!isSocket());
        myAddressField.setEditable(!myIsLocal);
        myPortField.setEditable(!myIsLocal);
        mySocketTransport.setEnabled(!myIsLocal);
        myShmemTransport.setEnabled(!myIsLocal);
    }

    @Override
    public void disposeEditor() {
    }

    @Override
    public void resetEditorFrom(GenericDebuggerRunnerSettings runnerSettings) {
        setIsLocal(runnerSettings.LOCAL);
        setTransport(runnerSettings.getTransport());
        setPort(StringUtil.notNullize(runnerSettings.getDebugPort()));
        suggestAvailablePortIfNotSpecified();
        updateUI();
    }

    private void suggestAvailablePortIfNotSpecified() {
        String port = getPort();
        boolean portSpecified = !StringUtil.isEmpty(port);
        boolean isSocketTransport = getTransport() == DebuggerSettings.SOCKET_TRANSPORT;
        if (isSocketTransport) {
            try {
                Integer.parseInt(port);
            }
            catch (NumberFormatException e) {
                portSpecified = false;
            }
        }

        if (!portSpecified) {
            try {
                setPort(DebuggerUtils.getInstance().findAvailableDebugAddress(getTransport()).address());
            }
            catch (ExecutionException e) {
                LOGGER.info(e);
            }
        }
    }

    private int getTransport() {
        if (myIsLocal) {
            return DebuggerSettings.getInstance().DEBUGGER_TRANSPORT;
        }
        else {
            return mySocketTransport.isSelected() ? DebuggerSettings.SOCKET_TRANSPORT : DebuggerSettings.SHMEM_TRANSPORT;
        }
    }

    private String getPort() {
        if (isSocket()) {
            return myPortField.getText();
        }
        else {
            return myAddressField.getText();
        }
    }

    private void checkPort() throws ConfigurationException {
        if (isSocket() && myPortField.getText().length() > 0) {
            try {
                final int port = Integer.parseInt(myPortField.getText());
                if (port < 0 || port > 0xffff) {
                    throw new NumberFormatException();
                }
            }
            catch (NumberFormatException e) {
                throw new ConfigurationException(DebuggerBundle.message("error.text.invalid.port.0", myPortField.getText()));
            }
        }
    }

    private void setTransport(int transport) {
        mySocketTransport.setSelected(transport == DebuggerSettings.SOCKET_TRANSPORT);
        myShmemTransport.setSelected(transport != DebuggerSettings.SOCKET_TRANSPORT);
    }

    private void setIsLocal(boolean b) {
        myTransportPanel.setVisible(true);
        myDebuggerSettings.setVisible(b);
        myIsLocal = b;
    }

    private void setPort(String port) {
        if (isSocket()) {
            myPortField.setText(port);
        }
        else {
            myAddressField.setText(port);
        }
    }

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
