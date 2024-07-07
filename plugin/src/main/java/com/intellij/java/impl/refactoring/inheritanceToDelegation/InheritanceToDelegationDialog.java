/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.inheritanceToDelegation;

import com.intellij.java.language.psi.*;
import consulo.application.HelpManager;
import consulo.configurable.ConfigurationException;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.project.Project;
import consulo.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.classMember.MemberInfoChange;
import consulo.language.editor.refactoring.classMember.MemberInfoModel;
import com.intellij.java.impl.refactoring.ui.ClassCellRenderer;
import com.intellij.java.impl.refactoring.ui.MemberSelectionPanel;
import consulo.language.editor.refactoring.ui.NameSuggestionsField;
import consulo.language.editor.refactoring.ui.RefactoringDialog;
import com.intellij.java.impl.refactoring.util.classMembers.InterfaceMemberDependencyGraph;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

public class InheritanceToDelegationDialog extends RefactoringDialog {
  private final PsiClass[] mySuperClasses;
  private final PsiClass myClass;
  private final HashMap<PsiClass, Collection<MemberInfo>> myBasesToMemberInfos;

  private NameSuggestionsField myFieldNameField;
  private NameSuggestionsField myInnerClassNameField;
  private JCheckBox myCbGenerateGetter;
  private MemberSelectionPanel myMemberSelectionPanel;
  private JComboBox myClassCombo;
  private final Project myProject;
  private MyClassComboItemListener myClassComboItemListener;
  private NameSuggestionsField.DataChanged myDataChangedListener;

  public InheritanceToDelegationDialog(Project project,
                                       PsiClass aClass,
                                       PsiClass[] superClasses,
                                       HashMap<PsiClass,Collection<MemberInfo>> basesToMemberInfos) {
    super(project, true);
    myProject = project;
    myClass = aClass;
    mySuperClasses = superClasses;
    myBasesToMemberInfos = basesToMemberInfos;

    setTitle(InheritanceToDelegationHandler.REFACTORING_NAME);
    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFieldNameField;
  }

  protected void dispose() {
    myInnerClassNameField.removeDataChangedListener(myDataChangedListener);
    myFieldNameField.removeDataChangedListener(myDataChangedListener);
    myClassCombo.removeItemListener(myClassComboItemListener);
    super.dispose();
  }

  @Nonnull
  public String getFieldName() {
    return myFieldNameField.getEnteredName();
  }

  @Nullable
  public String getInnerClassName() {
    if (myInnerClassNameField != null) {
      return myInnerClassNameField.getEnteredName();
    }
    else {
      return null;
    }
  }

  public boolean isGenerateGetter() {
    return myCbGenerateGetter.isSelected();
  }

  @Override
  protected void canRun() throws ConfigurationException {
    final String fieldName = getFieldName();
    final PsiNameHelper helper = PsiNameHelper.getInstance(myProject);
    if (!helper.isIdentifier(fieldName)){
      throw new ConfigurationException("\'" + fieldName + "\' is invalid field name for delegation");
    }
    if (myInnerClassNameField != null) {
      final String className = myInnerClassNameField.getEnteredName();
      if (!helper.isIdentifier(className)) {
        throw new ConfigurationException("\'" + className + "\' is invalid inner class name");
      }
    }
  }

  public Collection<MemberInfo> getSelectedMemberInfos() {
    return myMemberSelectionPanel.getTable().getSelectedMemberInfos();
  }

  public PsiClass getSelectedTargetClass() {
    return (PsiClass)myClassCombo.getSelectedItem();
  }


  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INHERITANCE_TO_DELEGATION);
  }

  protected void doAction() {
    JavaRefactoringSettings.getInstance().INHERITANCE_TO_DELEGATION_DELEGATE_OTHER = myCbGenerateGetter.isSelected();

    final Collection<MemberInfo> selectedMemberInfos = getSelectedMemberInfos();
    final ArrayList<PsiClass> implementedInterfaces = new ArrayList<PsiClass>();
    final ArrayList<PsiMethod> delegatedMethods = new ArrayList<PsiMethod>();

    for (MemberInfo memberInfo : selectedMemberInfos) {
      final PsiElement member = memberInfo.getMember();
      if (member instanceof PsiClass && Boolean.FALSE.equals(memberInfo.getOverrides())) {
        implementedInterfaces.add((PsiClass)member);
      }
      else if (member instanceof PsiMethod) {
        delegatedMethods.add((PsiMethod)member);
      }
    }
    invokeRefactoring(new InheritanceToDelegationProcessor(myProject, myClass,
                                                           getSelectedTargetClass(), getFieldName(),
                                                           getInnerClassName(),
                                                           implementedInterfaces.toArray(new PsiClass[implementedInterfaces.size()]),
                                                           delegatedMethods.toArray(new PsiMethod[delegatedMethods.size()]),
                                                           isGenerateGetter(), isGenerateGetter()));
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridy = 0;
    gbc.gridx = 0;


    gbc.insets = new Insets(4, 0, 0, 8);
    myClassCombo = new JComboBox(mySuperClasses);
    myClassCombo.setRenderer(new ClassCellRenderer(myClassCombo.getRenderer()));
    gbc.gridwidth = 2;
    final JLabel classComboLabel = new JLabel();
    panel.add(classComboLabel, gbc);
    gbc.gridy++;
    panel.add(myClassCombo, gbc);
    classComboLabel.setText(RefactoringLocalize.replaceInheritanceFrom().get());

    myClassComboItemListener = new MyClassComboItemListener();
    myClassCombo.addItemListener(myClassComboItemListener);

    gbc.gridy++;
    gbc.gridwidth = 1;
    gbc.insets = new Insets(4, 0, 4, 0);
    final JLabel fieldNameLabel = new JLabel();
    panel.add(fieldNameLabel, gbc);

    myFieldNameField = new NameSuggestionsField(myProject);
    gbc.gridx++;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets = new Insets(4, 0, 4, 8);
    gbc.weightx = 1.0;
    panel.add(myFieldNameField.getComponent(), gbc);
    fieldNameLabel.setText(RefactoringLocalize.fieldName().get());

    //    if(InheritanceToDelegationUtil.isInnerClassNeeded(myClass, mySuperClass)) {
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.gridwidth = 1;
    gbc.insets = new Insets(4, 0, 4, 0);
    gbc.weightx = 0.0;
    final JLabel innerClassNameLabel = new JLabel();
    panel.add(innerClassNameLabel, gbc);

    /*String[] suggestions = new String[mySuperClasses.length];
    for (int i = 0; i < suggestions.length; i++) {
      suggestions[i] = "My" + mySuperClasses[i].getName();
    }*/
    myInnerClassNameField = new NameSuggestionsField(myProject);
    gbc.gridx++;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets = new Insets(4, 4, 4, 8);
    gbc.weightx = 1.0;
    panel.add(myInnerClassNameField.getComponent(), gbc);
    innerClassNameLabel.setText(RefactoringLocalize.innerClassName().get());

    boolean innerClassNeeded = false;
    for (PsiClass superClass : mySuperClasses) {
      innerClassNeeded |= InheritanceToDelegationUtil.isInnerClassNeeded(myClass, superClass);
    }
    myInnerClassNameField.setVisible(innerClassNeeded);
    innerClassNameLabel.setVisible(innerClassNeeded);
    
    return panel;
  }


  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.weightx = 1.0;

    gbc.weighty = 1.0;
    gbc.gridwidth = 1;
    gbc.insets = new Insets(4, 0, 4, 4);

    myMemberSelectionPanel = new MemberSelectionPanel(RefactoringLocalize.delegateMembers().get(), Collections.<MemberInfo>emptyList(), null);
    panel.add(myMemberSelectionPanel, gbc);
    MyMemberInfoModel memberInfoModel = new InheritanceToDelegationDialog.MyMemberInfoModel();
    myMemberSelectionPanel.getTable().setMemberInfoModel(memberInfoModel);
    myMemberSelectionPanel.getTable().addMemberInfoChangeListener(memberInfoModel);


    gbc.gridy++;
    gbc.insets = new Insets(4, 8, 0, 8);
    gbc.weighty = 0.0;
    myCbGenerateGetter = new JCheckBox(RefactoringLocalize.generateGetterForDelegatedComponent().get());
    myCbGenerateGetter.setFocusable(false);
    panel.add(myCbGenerateGetter, gbc);
    myCbGenerateGetter.setSelected(JavaRefactoringSettings.getInstance().INHERITANCE_TO_DELEGATION_DELEGATE_OTHER);
    updateTargetClass();

    return panel;
  }

  private void updateTargetClass() {
    final PsiClass targetClass = getSelectedTargetClass();
    PsiManager psiManager = myClass.getManager();
    PsiType superType = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createType(targetClass);
    SuggestedNameInfo suggestedNameInfo =
      JavaCodeStyleManager.getInstance(psiManager.getProject()).suggestVariableName(VariableKind.FIELD, null, null, superType);
    myFieldNameField.setSuggestions(suggestedNameInfo.names);
    myInnerClassNameField.getComponent().setEnabled(InheritanceToDelegationUtil.isInnerClassNeeded(myClass, targetClass));
    @NonNls final String suggestion = "My" + targetClass.getName();
    myInnerClassNameField.setSuggestions(new String[]{suggestion});

    myDataChangedListener = new NameSuggestionsField.DataChanged() {
      public void dataChanged() {
        validateButtons();
      }
    };
    myInnerClassNameField.addDataChangedListener(myDataChangedListener);
    myFieldNameField.addDataChangedListener(myDataChangedListener);

    myMemberSelectionPanel.getTable().setMemberInfos(myBasesToMemberInfos.get(targetClass));
    myMemberSelectionPanel.getTable().fireExternalDataChange();
  }

  private class MyMemberInfoModel implements MemberInfoModel<PsiMember, MemberInfo> {
    final HashMap<PsiClass,InterfaceMemberDependencyGraph> myGraphs;

    public MyMemberInfoModel() {
      myGraphs = new HashMap<PsiClass, InterfaceMemberDependencyGraph>();
      for (PsiClass superClass : mySuperClasses) {
        myGraphs.put(superClass, new InterfaceMemberDependencyGraph(superClass));
      }
    }

    public boolean isMemberEnabled(MemberInfo memberInfo) {
      if (getGraph().getDependent().contains(memberInfo.getMember())) {
        return false;
      }
      else {
        return true;
      }
    }

    public boolean isCheckedWhenDisabled(MemberInfo member) {
      return true;
    }

    public boolean isAbstractEnabled(MemberInfo member) {
      return false;
    }

    public boolean isAbstractWhenDisabled(MemberInfo member) {
      return false;
    }

    public Boolean isFixedAbstract(MemberInfo member) {
      return null;
    }

    public int checkForProblems(@Nonnull MemberInfo member) {
      return OK;
    }

    public String getTooltipText(MemberInfo member) {
      return null;
    }

    public void memberInfoChanged(MemberInfoChange<PsiMember, MemberInfo> event) {
      final Collection<MemberInfo> changedMembers = event.getChangedMembers();

      for (MemberInfo changedMember : changedMembers) {
        getGraph().memberChanged(changedMember);
      }
    }

    private InterfaceMemberDependencyGraph getGraph() {
      return myGraphs.get(getSelectedTargetClass());
    }
  }

  private class MyClassComboItemListener implements ItemListener {
    public void itemStateChanged(ItemEvent e) {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        updateTargetClass();
      }
    }
  }
}
