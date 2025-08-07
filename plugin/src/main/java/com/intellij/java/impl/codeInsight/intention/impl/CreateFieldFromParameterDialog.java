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
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.impl.refactoring.ui.TypeSelector;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiNameHelper;
import com.intellij.java.language.psi.PsiType;
import consulo.application.ApplicationPropertiesComponent;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Label;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class CreateFieldFromParameterDialog extends DialogWrapper {
  private final Project myProject;
  private final String[] myNames;
  private final PsiType[] myTypes;
  private final PsiClass myTargetClass;
  private final boolean myFieldMayBeFinal;

  private JComponent myNameField;
  private CheckBox myCbFinal;
  private static final @NonNls String PROPERTY_NAME = "CREATE_FIELD_FROM_PARAMETER_DECLARE_FINAL";
  private TypeSelector myTypeSelector;

  public CreateFieldFromParameterDialog(
    Project project,
    String[] names,
    PsiClass targetClass,
    boolean fieldMayBeFinal,
    PsiType... types
  ) {
    super(project, true);
    myProject = project;
    myNames = names;
    myTypes = types;
    myTargetClass = targetClass;
    myFieldMayBeFinal = fieldMayBeFinal;

    setTitle(CodeInsightLocalize.dialogCreateFieldFromParameterTitle());

    init();
  }

  @Override
  protected void doOKAction() {
    if (myCbFinal.isEnabled()) {
      ApplicationPropertiesComponent.getInstance().setValue(PROPERTY_NAME, String.valueOf(myCbFinal.getValueOrError()));
    }

    final PsiField[] fields = myTargetClass.getFields();
    for (PsiField field : fields) {
      if (field.getName().equals(getEnteredName())) {
        int result = Messages.showOkCancelDialog(
          getContentPane(),
          CodeInsightLocalize.dialogCreateFieldFromParameterAlreadyExistsText(getEnteredName()).get(),
          CodeInsightLocalize.dialogCreateFieldFromParameterAlreadyExistsTitle().get(),
          UIUtil.getQuestionIcon());
        if (result == 0) {
          close(OK_EXIT_CODE);
        }
        else {
          return;
        }
      }
    }

    close(OK_EXIT_CODE);
  }

  @Override
  protected void init() {
    super.init();
    updateOkStatus();
  }

  public String getEnteredName() {
    return myNameField instanceof JComboBox combobox ? (String)combobox.getEditor().getItem() : ((JTextField)myNameField).getText();
  }

  public boolean isDeclareFinal() {
    return myCbFinal.isEnabled() && myCbFinal.getValueOrError();
  }

  @Override
  protected JComponent createNorthPanel() {
    if (myNames.length > 1) {
      final ComboBox combobox = new ComboBox<>(myNames, 200);
      myNameField = combobox;
      combobox.setEditable(true);
      combobox.setSelectedIndex(0);
      combobox.setMaximumRowCount(8);

      combobox.registerKeyboardAction(
        e -> {
          if (combobox.isPopupVisible()) {
            combobox.setPopupVisible(false);
          }
          else {
            doCancelAction();
          }
        },
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      );

      combobox.addItemListener(e -> updateOkStatus());
      combobox.getEditor().getEditorComponent().addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            updateOkStatus();
          }

          @Override
          public void keyReleased(KeyEvent e) {
            updateOkStatus();
          }

          @Override
          public void keyTyped(KeyEvent e) {
            updateOkStatus();
          }
        }
      );
    }
    else {
      JTextField field = new JTextField() {
        @Override
        public Dimension getPreferredSize() {
          Dimension size = super.getPreferredSize();
          return new Dimension(200, size.height);
        }
      };
      myNameField = field;
      field.setText(myNames[0]);

      field.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          updateOkStatus();
        }
      });
    }

    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = JBUI.insets(4);
    gbConstraints.anchor = GridBagConstraints.EAST;
    gbConstraints.fill = GridBagConstraints.BOTH;

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    final Label typeLabel = Label.create(CodeInsightLocalize.dialogCreateFieldFromParameterFieldTypeLabel());
    panel.add(TargetAWT.to(typeLabel), gbConstraints);
    gbConstraints.gridx = 1;
    if (myTypes.length > 1) {
      myTypeSelector = new TypeSelector(myProject);
      myTypeSelector.setTypes(myTypes);
    }
    else {
      myTypeSelector = new TypeSelector(myTypes[0], myProject);
    }
    panel.add(myTypeSelector.getComponent(), gbConstraints);

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 1;
    Label namePrompt = Label.create(CodeInsightLocalize.dialogCreateFieldFromParameterFieldNameLabel());
    panel.add(TargetAWT.to(namePrompt), gbConstraints);

    gbConstraints.gridwidth = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridx = 1;
    gbConstraints.gridy = 1;
    panel.add(myNameField, gbConstraints);

    return panel;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 0;
    gbConstraints.gridwidth = 1;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.insets = JBUI.emptyInsets();

    myCbFinal = CheckBox.create(CodeInsightLocalize.dialogCreateFieldFromParameterDeclareFinalCheckbox());
    if (myFieldMayBeFinal) {
      myCbFinal.setValue(ApplicationPropertiesComponent.getInstance().isTrueValue(PROPERTY_NAME));
    }
    else {
      myCbFinal.setValue(false);
      myCbFinal.setEnabled(false);
    }

    gbConstraints.gridy++;
    panel.add(TargetAWT.to(myCbFinal), gbConstraints);
    myCbFinal.addValueListener(e -> {
      requestFocusInNameWindow();
      if (myCbFinal.isEnabled()) {
      }
    });

    return panel;
  }

  private void requestFocusInNameWindow() {
    if (myNameField instanceof JTextField) {
      myNameField.requestFocusInWindow();
    }
    else {
      ((JComboBox)myNameField).getEditor().getEditorComponent().requestFocusInWindow();
    }
  }

  private void updateOkStatus() {
    String text = getEnteredName();
    setOKActionEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(text));
  }

  @Override
  @RequiredUIAccess
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Nullable
  public PsiType getType() {
    return myTypeSelector.getSelectedType();
  }
}
