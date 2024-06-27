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
package com.intellij.java.impl.ide.util;

import consulo.platform.base.localize.CommonLocalize;
import consulo.platform.base.localize.IdeLocalize;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

//TODO: review title and text!!!
class SuperMethodWarningDialog extends DialogWrapper {
  public static final int NO_EXIT_CODE = NEXT_USER_EXIT_CODE + 1;
  private final String myName;
  private final String[] myClassNames;
  private final String myActionString;
  private final boolean myIsSuperAbstract;
  private final boolean myIsParentInterface;
  private final boolean myIsContainedInInterface;

  public SuperMethodWarningDialog(
    Project project,
    String name,
    String actionString,
    boolean isSuperAbstract,
    boolean isParentInterface,
    boolean isContainedInInterface,
    String... classNames
  ) {
    super(project, true);
    myName = name;
    myClassNames = classNames;
    myActionString = actionString;
    myIsSuperAbstract = isSuperAbstract;
    myIsParentInterface = isParentInterface;
    myIsContainedInInterface = isContainedInInterface;
    setTitle(IdeLocalize.titleWarning());
    setButtonsAlignment(SwingUtilities.CENTER);
    setOKButtonText(CommonLocalize.buttonYes().get());
    init();
  }

  @Nonnull
  protected Action[] createActions(){
    return new Action[]{getOKAction(),new NoAction(),getCancelAction()};
  }

  public JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    Image icon = UIUtil.getWarningIcon();
    if (icon != null){
      JLabel iconLabel = new JBLabel(UIUtil.getQuestionIcon());
      panel.add(iconLabel, BorderLayout.WEST);
    }
    JPanel labelsPanel = new JPanel(new GridLayout(0, 1, 0, 0));
    labelsPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 10));
    String classType = myIsParentInterface ? IdeLocalize.elementOfInterface().get() : IdeLocalize.elementOfClass().get();
    String methodString = IdeLocalize.elementMethod().get();
    labelsPanel.add(new JLabel(IdeLocalize.labelMethod(myName).get()));
    if (myClassNames.length == 1) {
      final String className = myClassNames[0];
      labelsPanel.add(new JLabel(
        myIsContainedInInterface || !myIsSuperAbstract
          ? IdeLocalize.labelOverridesMethodOf_class_or_interfaceName(methodString, classType, className).get()
          : IdeLocalize.labelImplementsMethodOf_class_or_interfaceName(methodString, classType, className).get()
      ));
    } else {
      labelsPanel.add(new JLabel(IdeLocalize.labelImplementsMethodOf_interfaces().get()));
      for (final String className : myClassNames) {
        labelsPanel.add(new JLabel("    " + className));
      }
    }
    labelsPanel.add(new JLabel(IdeLocalize.promptDoYouWantToAction_verbTheMethodFrom_class(myActionString, myClassNames.length > 1 ? 2 : 1).get()));
    panel.add(labelsPanel, BorderLayout.CENTER);
    return panel;
  }

  public static String capitalize(String text) {
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  public JComponent createCenterPanel() {
    return null;
  }

  private class NoAction extends AbstractAction {
    public NoAction() {
      super(CommonLocalize.buttonNo().get());
    }

    public void actionPerformed(ActionEvent e) {
      close(NO_EXIT_CODE);
    }
  }
}

