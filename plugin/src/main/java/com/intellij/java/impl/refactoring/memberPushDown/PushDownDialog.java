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
package com.intellij.java.impl.refactoring.memberPushDown;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.ui.MemberSelectionPanel;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import consulo.application.HelpManager;
import consulo.language.editor.refactoring.classMember.MemberInfoChange;
import consulo.language.editor.refactoring.classMember.MemberInfoModel;
import consulo.language.editor.refactoring.classMember.UsedByDependencyMemberInfoModel;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringDialog;
import consulo.language.editor.ui.util.DocCommentPanel;
import consulo.language.editor.ui.util.DocCommentPolicy;
import consulo.project.Project;
import consulo.ui.ex.awtUnsafe.TargetAWT;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PushDownDialog extends RefactoringDialog {
  private final List<MemberInfo> myMemberInfos;
  private final PsiClass myClass;
  private DocCommentPanel myJavaDocPanel;
  private MemberInfoModel<PsiMember, MemberInfo> myMemberInfoModel;

  public PushDownDialog(Project project, MemberInfo[] memberInfos, PsiClass aClass) {
    super(project, true);
    myMemberInfos = Arrays.asList(memberInfos);
    myClass = aClass;

    setTitle(JavaPushDownHandler.REFACTORING_NAME);

    init();
  }

  public int getJavaDocPolicy() {
    return myJavaDocPanel.getPolicy();
  }

  public MemberInfo[] getSelectedMemberInfos() {
    ArrayList<MemberInfo> list = new ArrayList<MemberInfo>(myMemberInfos.size());
    for (MemberInfo info : myMemberInfos) {
      if (info.isChecked() && myMemberInfoModel.isMemberEnabled(info)) {
        list.add(info);
      }
    }
    return list.toArray(new MemberInfo[list.size()]);
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.MEMBERS_PUSH_DOWN);
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.memberPushDown.PushDownDialog";
  }

  protected JComponent createNorthPanel() {
    GridBagConstraints gbConstraints = new GridBagConstraints();

    JPanel panel = new JPanel(new GridBagLayout());

    gbConstraints.insets = new Insets(4, 0, 10, 8);
    gbConstraints.weighty = 1;
    gbConstraints.weightx = 1;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(new JLabel(RefactoringLocalize.pushMembersFrom0DownLabel(myClass.getQualifiedName()).get()), gbConstraints);
    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    MemberSelectionPanel memberSelectionPanel = new MemberSelectionPanel(
      RefactoringLocalize.membersToBePushedDownPanelTitle().get(),
      myMemberInfos,
      RefactoringLocalize.keepAbstractColumnHeader().get()
    );
    panel.add(memberSelectionPanel, BorderLayout.CENTER);

    myMemberInfoModel = new MyMemberInfoModel();
    myMemberInfoModel.memberInfoChanged(new MemberInfoChange<>(myMemberInfos));
    memberSelectionPanel.getTable().setMemberInfoModel(myMemberInfoModel);
    memberSelectionPanel.getTable().addMemberInfoChangeListener(myMemberInfoModel);


    myJavaDocPanel = new DocCommentPanel(RefactoringLocalize.pushDownJavadocPanelTitle());
    myJavaDocPanel.setPolicy(JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC);
    panel.add(TargetAWT.to(myJavaDocPanel.getComponent()), BorderLayout.EAST);
    return panel;
  }

  protected void doAction() {
    if(!isOKActionEnabled()) return;

    JavaRefactoringSettings.getInstance().PUSH_DOWN_PREVIEW_USAGES = isPreviewUsages();

    invokeRefactoring (new PushDownProcessor(
            getProject(), getSelectedMemberInfos(), myClass,
            new DocCommentPolicy(getJavaDocPolicy())));
  }

  private class MyMemberInfoModel extends UsedByDependencyMemberInfoModel<PsiMember, PsiClass, MemberInfo> {
    public MyMemberInfoModel() {
      super(myClass);
    }
  }
}
