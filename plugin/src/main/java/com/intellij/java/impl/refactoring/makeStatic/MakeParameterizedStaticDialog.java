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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 15.04.2002
 * Time: 15:29:56
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.refactoring.makeStatic;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;

import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.application.HelpManager;
import consulo.project.Project;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.Messages;
import consulo.util.lang.StringUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiNameHelper;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiParameterList;
import com.intellij.java.language.psi.PsiTypeParameterListOwner;
import com.intellij.java.impl.refactoring.HelpID;
import consulo.language.editor.refactoring.RefactoringBundle;
import com.intellij.java.impl.refactoring.util.ParameterTablePanel;
import com.intellij.java.analysis.impl.refactoring.util.VariableData;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.usage.UsageViewUtil;

public class MakeParameterizedStaticDialog extends AbstractMakeStaticDialog {
  private final Project myProject;
  private final String[] myNameSuggestions;

  private final JCheckBox myMakeClassParameter = new JCheckBox();
  private JComponent myClassParameterNameInputField;
  private final JCheckBox myMakeFieldParameters = new JCheckBox();

  private ParameterTablePanel myParameterPanel;
  private VariableData[] myVariableData;
  private final boolean myAnyNonFieldMembersUsed;


  public MakeParameterizedStaticDialog(Project project,
                                       PsiTypeParameterListOwner member,
                                       String[] nameSuggestions,
                                       InternalUsageInfo[] internalUsages) {
    super(project, member);
    myProject = project;
    myNameSuggestions = nameSuggestions;

    String type = UsageViewUtil.getType(myMember);
    setTitle(RefactoringBundle.message("make.0.static", StringUtil.capitalize(type)));
    myAnyNonFieldMembersUsed = buildVariableData(internalUsages);
    init();
  }

  private boolean buildVariableData(InternalUsageInfo[] internalUsages) {
    ArrayList<VariableData> variableDatum = new ArrayList<VariableData>();
    boolean nonFieldUsages = MakeStaticUtil.collectVariableData(myMember, internalUsages, variableDatum);

    myVariableData = variableDatum.toArray(new VariableData[0]);
    return nonFieldUsages;
  }

  public boolean isReplaceUsages() {
    return true;
  }

  public boolean isMakeClassParameter() {
    if (myMakeClassParameter != null)
      return myMakeClassParameter.isSelected();
    else
      return false;
  }

  public String getClassParameterName() {
    if (isMakeClassParameter()) {
      if (myClassParameterNameInputField instanceof JTextField) {
        return ((JTextField)myClassParameterNameInputField).getText();
      }
      else if(myClassParameterNameInputField instanceof JComboBox) {
        return (String)(((JComboBox)myClassParameterNameInputField).getEditor().getItem());
      }
      else
        return null;
    }
    else {
      return null;
    }
  }

  /**
   *
   * @return null if field parameters are not selected
   */
  public VariableData[] getVariableData() {
    if(myMakeFieldParameters != null && myMakeFieldParameters.isSelected()) {
      return myVariableData;
    }
    else {
      return null;
    }
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.MAKE_METHOD_STATIC);
  }

  protected JComponent createCenterPanel() {
    GridBagConstraints gbConstraints = new GridBagConstraints();

    JPanel panel = new JPanel(new GridBagLayout());

    gbConstraints.insets = new Insets(4, 8, 4, 8);
    gbConstraints.weighty = 0;
    gbConstraints.weightx = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = GridBagConstraints.RELATIVE;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(createDescriptionLabel(), gbConstraints);

    gbConstraints.weighty = 0;
    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.anchor = GridBagConstraints.WEST;
    String text = myMember instanceof PsiMethod ? RefactoringBundle.message("add.object.as.a.parameter.with.name") : RefactoringBundle.message("add.object.as.a.parameter.to.constructors.with.name");
    myMakeClassParameter.setText(text);
    panel.add(myMakeClassParameter, gbConstraints);
    myMakeClassParameter.setSelected(myAnyNonFieldMembersUsed);

    gbConstraints.insets = new Insets(0, 8, 4, 8);
    gbConstraints.weighty = 0;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 2;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.anchor = GridBagConstraints.NORTHWEST;
    if(myNameSuggestions.length > 1) {
      myClassParameterNameInputField = createComboBoxForName();
    }
    else {
      JTextField textField = new JTextField();
      textField.setText(myNameSuggestions[0]);
      textField.getDocument().addDocumentListener(new DocumentAdapter() {
        public void textChanged(DocumentEvent event) {
          updateControls();
        }
      });
      myClassParameterNameInputField = textField;
    }
    panel.add(myClassParameterNameInputField, gbConstraints);

    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;

    if(myVariableData.length > 0) {
      gbConstraints.insets = new Insets(4, 8, 4, 8);
      gbConstraints.weighty = 0;
      gbConstraints.weightx = 0;
      gbConstraints.gridheight = 1;
      gbConstraints.fill = GridBagConstraints.NONE;
      gbConstraints.anchor = GridBagConstraints.WEST;
      text = myMember instanceof PsiMethod ? RefactoringBundle.message("add.parameters.for.fields") : RefactoringBundle.message("add.parameters.for.fields.to.constructors");
      myMakeFieldParameters.setText(text);
      panel.add(myMakeFieldParameters, gbConstraints);
      myMakeFieldParameters.setSelected(!myAnyNonFieldMembersUsed);

      myParameterPanel = new ParameterTablePanel(myProject, myVariableData, myMember) {
        protected void updateSignature() {
        }

        protected void doEnterAction() {
          clickDefaultButton();
        }

        protected void doCancelAction() {
        }
      };

      gbConstraints.insets = new Insets(0, 8, 4, 8);
      gbConstraints.gridwidth = 2;
      gbConstraints.fill = GridBagConstraints.BOTH;
      gbConstraints.weighty = 1;
      panel.add(myParameterPanel, gbConstraints);
    }

    ActionListener inputFieldValidator = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    };

    myMakeClassParameter.addActionListener(inputFieldValidator);
    myMakeFieldParameters.addActionListener(inputFieldValidator);



    updateControls();

    return panel;
  }

  protected boolean validateData() {
    int ret = 0;
    if (isMakeClassParameter()) {
      final PsiMethod methodWithParameter = checkParameterDoesNotExist();
      if (methodWithParameter != null) {
        String who = methodWithParameter == myMember ? RefactoringBundle.message("this.method") : DescriptiveNameUtil
          .getDescriptiveName(methodWithParameter);
        String message = RefactoringBundle.message("0.already.has.parameter.named.1.use.this.name.anyway", who, getClassParameterName());
        ret = Messages.showYesNoDialog(myProject, message, RefactoringBundle.message("warning.title"), Messages.getWarningIcon());
        myClassParameterNameInputField.requestFocusInWindow();
      }
    }
    return ret == 0;
  }

  private PsiMethod checkParameterDoesNotExist() {
    String parameterName = getClassParameterName();
    if(parameterName == null) return null;
    PsiMethod[] methods = myMember instanceof PsiMethod ? new PsiMethod[]{(PsiMethod)myMember} : ((PsiClass)myMember).getConstructors();
    for (PsiMethod method : methods) {
      PsiParameterList parameterList = method.getParameterList();
      if(parameterList == null) continue;
      PsiParameter[] parameters = parameterList.getParameters();
      for (PsiParameter parameter : parameters) {
        if (parameterName.equals(parameter.getName())) return method;
      }
    }

    return null;
  }

  private void updateControls() {
    if (isMakeClassParameter()) {
      String classParameterName = getClassParameterName();
      if (classParameterName == null) {
        setOKActionEnabled(false);
      }
      else {
        setOKActionEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(classParameterName.trim()));
      }
    }
    else
      setOKActionEnabled(true);

    if(myClassParameterNameInputField != null) {
      myClassParameterNameInputField.setEnabled(isMakeClassParameter());
    }

    if(myParameterPanel != null) {
      myParameterPanel.setEnabled(myMakeFieldParameters.isSelected());
    }
  }

  private JComboBox createComboBoxForName() {
    final ComboBox combobox = new ComboBox(myNameSuggestions,-1);

    combobox.setEditable(true);
    combobox.setSelectedIndex(0);
    combobox.setMaximumRowCount(8);

    combobox.addItemListener(
      new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          updateControls();
        }
      }
    );
    combobox.getEditor().getEditorComponent().addKeyListener(
      new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          updateControls();
        }

        public void keyReleased(KeyEvent e) {
          updateControls();
        }

        public void keyTyped(KeyEvent e) {
          updateControls();
        }
      }
    );

    return combobox;
  }
}
