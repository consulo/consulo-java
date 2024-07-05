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
package com.intellij.java.impl.refactoring.turnRefsToSuper;

import consulo.application.HelpManager;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import consulo.language.editor.refactoring.RefactoringBundle;
import com.intellij.java.impl.refactoring.ui.ClassCellRenderer;
import consulo.language.editor.refactoring.ui.RefactoringDialog;
import com.intellij.java.impl.refactoring.util.RefactoringHierarchyUtil;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.UIUtil;

import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author dsl
 * Date: 06.06.2002
 */
public class TurnRefsToSuperDialog extends RefactoringDialog {
  @Nonnull
  private final PsiClass mySubClass;
  private final List mySuperClasses;

  private JList mySuperClassesList = null;
  private final JCheckBox myCbReplaceInstanceOf = new JCheckBox();

  TurnRefsToSuperDialog(Project project, @Nonnull PsiClass subClass, List superClasses) {
    super(project, true);

    mySubClass = subClass;
    mySuperClasses = superClasses;

    setTitle(TurnRefsToSuperHandler.REFACTORING_NAME);
    init();
  }

  @Nullable
  public PsiClass getSuperClass() {
    if(mySuperClassesList != null) {
      return (PsiClass) mySuperClassesList.getSelectedValue();
    }
    else {
      return null;
    }
  }

  public boolean isUseInInstanceOf() {
    return myCbReplaceInstanceOf.isSelected();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.TURN_REFS_TO_SUPER);
  }

  public JComponent getPreferredFocusedComponent() {
    return mySuperClassesList;
  }


  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));

    final JLabel classListLabel = new JLabel();
    panel.add(classListLabel, BorderLayout.NORTH);

    mySuperClassesList = new JBList(mySuperClasses.toArray());
    mySuperClassesList.setCellRenderer(new ClassCellRenderer(mySuperClassesList.getCellRenderer()));
    mySuperClassesList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    classListLabel.setText(RefactoringLocalize.turnrefstosuperChangeUsagesTo(mySubClass.getQualifiedName()).get());

    PsiClass nearestBase = RefactoringHierarchyUtil.getNearestBaseClass(mySubClass, true);
    int indexToSelect = 0;
    if(nearestBase != null) {
      indexToSelect = mySuperClasses.indexOf(nearestBase);
    }
    mySuperClassesList.setSelectedIndex(indexToSelect);
    panel.add(ScrollPaneFactory.createScrollPane(mySuperClassesList), BorderLayout.CENTER);

    myCbReplaceInstanceOf.setText(RefactoringLocalize.turnrefstosuperUseSuperclassInInstanceof().get());
    myCbReplaceInstanceOf.setSelected(false);
    myCbReplaceInstanceOf.setFocusable(false);
    panel.add(myCbReplaceInstanceOf, BorderLayout.SOUTH);

    return panel;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperDialog";
  }

  protected void doAction() {
    JavaRefactoringSettings.getInstance().TURN_REFS_TO_SUPER_PREVIEW_USAGES = isPreviewUsages();
    final PsiClass superClass = getSuperClass();
    if (superClass != null) {
      invokeRefactoring(new TurnRefsToSuperProcessor(getProject(), mySubClass, superClass, isUseInInstanceOf()));
    }
  }

  protected JComponent createNorthPanel() {
    return null;
  }
}
