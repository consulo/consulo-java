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

/**
 * created at Oct 8, 2001
 * @author Jeka
 */
package com.intellij.java.impl.refactoring.ui;

import consulo.application.HelpManager;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.internal.laf.MultiLineLabelUI;
import consulo.ui.ex.awt.Messages;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;

public class YesNoPreviewUsagesDialog extends DialogWrapper {
  private JCheckBox myCbPreviewResults;
  private final boolean myToPreviewUsages;
  private final String myMessage;
  private final String myHelpID;

  public YesNoPreviewUsagesDialog(String title, String message, boolean previewUsages,
                                  String helpID, Project project) {
    super(project, false);
    myHelpID = helpID;
    setTitle(title);
    myMessage = message;
    myToPreviewUsages = previewUsages;
    setOKButtonText(RefactoringBundle.message("yes.button"));
    setCancelButtonText(RefactoringBundle.message("no.button"));
    setButtonsAlignment(SwingUtilities.CENTER);
    init();
  }

  protected JComponent createNorthPanel() {
    JLabel label = new JLabel(myMessage);
    label.setUI(new MultiLineLabelUI());
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(label, BorderLayout.CENTER);
    Image icon = Messages.getQuestionIcon();
    if (icon != null) {
      label.setIcon(TargetAWT.to(icon));
      label.setIconTextGap(7);
    }
    return panel;
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  public boolean isPreviewUsages() {
    return myCbPreviewResults.isSelected();
  }

  protected JComponent createSouthPanel() {
    myCbPreviewResults = new JCheckBox();
    myCbPreviewResults.setSelected(myToPreviewUsages);
    myCbPreviewResults.setText(RefactoringBundle.message("preview.usages.to.be.changed"));
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(super.createSouthPanel(), BorderLayout.CENTER);
    panel.add(myCbPreviewResults, BorderLayout.WEST);
    return panel;
  }

  @Nonnull
  protected Action[] createActions() {
    if(myHelpID != null){
      return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
    }
    else {
      return new Action[]{getOKAction(), getCancelAction()};
    }
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpID);
  }
}
