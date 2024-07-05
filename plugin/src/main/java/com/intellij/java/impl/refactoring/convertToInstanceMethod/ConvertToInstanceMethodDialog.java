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
package com.intellij.java.impl.refactoring.convertToInstanceMethod;

import consulo.application.HelpManager;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.logging.Logger;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiVariable;
import com.intellij.java.impl.refactoring.HelpID;
import consulo.language.editor.refactoring.RefactoringBundle;
import com.intellij.java.impl.refactoring.move.moveInstanceMethod.MoveInstanceMethodDialogBase;
import consulo.ui.ex.awt.event.DoubleClickListener;
import consulo.ui.ex.awt.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author ven
 */
public class ConvertToInstanceMethodDialog  extends MoveInstanceMethodDialogBase {
  private static final Logger LOG = Logger.getInstance(ConvertToInstanceMethodDialog.class);
  public ConvertToInstanceMethodDialog(final PsiMethod method, final PsiParameter[] variables) {
    super(method, variables, ConvertToInstanceMethodHandler.REFACTORING_NAME);
    init();
  }

  protected void doAction() {
    final PsiVariable targetVariable = (PsiVariable)myList.getSelectedValue();
    LOG.assertTrue(targetVariable instanceof PsiParameter);
    final ConvertToInstanceMethodProcessor processor = new ConvertToInstanceMethodProcessor(myMethod.getProject(),
                                                                                            myMethod, (PsiParameter)targetVariable,
                                                                                            myVisibilityPanel.getVisibility());
    if (!verifyTargetClass(processor.getTargetClass())) return;
    invokeRefactoring(processor);
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.CONVERT_TO_INSTANCE_METHOD);
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    final JLabel label = new JLabel(RefactoringLocalize.moveinstancemethodSelectAnInstanceParameter().get());
    panel.add(label, BorderLayout.NORTH);
    panel.add(createListAndVisibilityPanels(), BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected JList createTargetVariableChooser() {
    final JList variableChooser = super.createTargetVariableChooser();
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        Point point = e.getPoint();
        int index = variableChooser.locationToIndex(point);
        if (index == -1) return false;
        if (!variableChooser.getCellBounds(index, index).contains(point)) return false;
        doRefactorAction();
        return true;
      }
    }.installOn(variableChooser);
    return variableChooser;
  }
}
