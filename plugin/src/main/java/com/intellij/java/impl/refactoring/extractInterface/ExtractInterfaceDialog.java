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
package com.intellij.java.impl.refactoring.extractInterface;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.extractSuperclass.ExtractSuperBaseProcessor;
import com.intellij.java.impl.refactoring.extractSuperclass.JavaExtractSuperBaseDialog;
import com.intellij.java.impl.refactoring.ui.MemberSelectionPanel;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.psi.*;
import consulo.language.editor.refactoring.classMember.DelegatingMemberInfoModel;
import consulo.language.editor.refactoring.classMember.MemberInfoBase;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.ui.util.DocCommentPolicy;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.collection.ArrayUtil;

import javax.swing.*;
import java.awt.*;
import java.util.List;

class ExtractInterfaceDialog extends JavaExtractSuperBaseDialog {

  public ExtractInterfaceDialog(Project project, PsiClass sourceClass) {
    super(project, sourceClass, collectMembers(sourceClass), ExtractInterfaceHandler.REFACTORING_NAME);
    init();
  }

  private static List<MemberInfo> collectMembers(PsiClass c) {
    return MemberInfo.extractClassMembers(c, new MemberInfoBase.Filter<PsiMember>() {
      public boolean includeMember(PsiMember element) {
        if (element instanceof PsiMethod) {
          return element.hasModifierProperty(PsiModifier.PUBLIC)
            && !element.hasModifierProperty(PsiModifier.STATIC);
        }
        else if (element instanceof PsiField) {
          return element.hasModifierProperty(PsiModifier.FINAL)
            && element.hasModifierProperty(PsiModifier.STATIC)
            && element.hasModifierProperty(PsiModifier.PUBLIC);
        }
        else if (element instanceof PsiClass) {
          return ((PsiClass)element).isInterface() || element.hasModifierProperty(PsiModifier.STATIC);
        }
        return false;
      }
    }, true);
  }

  protected String getClassNameLabelText() {
    return isExtractSuperclass()
      ? RefactoringLocalize.interfaceNamePrompt().get()
      : RefactoringLocalize.renameImplementationClassTo().get();
  }

  @Override
  protected String getPackageNameLabelText() {
    return isExtractSuperclass()
      ? RefactoringLocalize.packageForNewInterface().get()
      : RefactoringLocalize.packageForOriginalClass().get();
  }

  protected String getEntityName() {
    return RefactoringLocalize.extractsuperinterfaceInterface().get();
  }

  @Override
  protected String getTopLabelText() {
    return RefactoringLocalize.extractInterfaceFrom().get();
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    final MemberSelectionPanel memberSelectionPanel = new MemberSelectionPanel(RefactoringLocalize.membersToFormInterface().get(),
      myMemberInfos, null
    );
    memberSelectionPanel.getTable()
      .setMemberInfoModel(new DelegatingMemberInfoModel<>(memberSelectionPanel.getTable().getMemberInfoModel()) {
        public Boolean isFixedAbstract(MemberInfo member) {
          return Boolean.TRUE;
        }
      });
    panel.add(memberSelectionPanel, BorderLayout.CENTER);

    panel.add(TargetAWT.to(myDocCommentPanel.getComponent()), BorderLayout.EAST);

    return panel;
  }

  @Override
  protected LocalizeValue getDocCommentPanelName() {
    return RefactoringLocalize.extractsuperinterfaceJavadoc();
  }

  @Override
  protected String getExtractedSuperNameNotSpecifiedMessage() {
    return RefactoringLocalize.noInterfaceNameSpecified().get();
  }

  @Override
  protected int getDocCommentPolicySetting() {
    return JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_JAVADOC;
  }

  @Override
  protected void setDocCommentPolicySetting(int policy) {
    JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_JAVADOC = policy;
  }

  @Override
  protected ExtractSuperBaseProcessor createProcessor() {
    return new ExtractInterfaceProcessor(myProject, false, getTargetDirectory(), getExtractedSuperName(),
      mySourceClass, ArrayUtil.toObjectArray(getSelectedMemberInfos(), MemberInfo.class),
      new DocCommentPolicy(getDocCommentPolicy())
    );
  }

  @Override
  protected String getHelpId() {
    return HelpID.EXTRACT_INTERFACE;
  }
}
