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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import consulo.annotation.access.RequiredReadAction;
import consulo.localize.LocalizeValue;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.LocalizeAction;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import com.intellij.java.language.psi.PsiVariable;
import consulo.ui.ex.awt.UIUtil;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexey Kudravtsev
 */
public class SideEffectWarningDialog extends DialogWrapper {
  private final PsiVariable myVariable;
  private final String myBeforeText;
  private final String myAfterText;
  private final boolean myCanCopeWithSideEffects;
  private LocalizeAction myRemoveAllAction;
  private LocalizeAction myCancelAllAction;
  public static final int MAKE_STATEMENT = 1;
  public static final int DELETE_ALL = 2;
  public static final int CANCEL = 0;

  public SideEffectWarningDialog(Project project, boolean canBeParent, PsiVariable variable, String beforeText, String afterText, boolean canCopeWithSideEffects) {
    super(project, canBeParent);
    myVariable = variable;
    myBeforeText = beforeText;
    myAfterText = afterText;
    myCanCopeWithSideEffects = canCopeWithSideEffects;
    setTitle(JavaQuickFixBundle.message("side.effects.warning.dialog.title"));
    init();
  }

  @Nonnull
  @Override
  protected Action[] createActions() {
    List<AbstractAction> actions = new ArrayList<>();
    myRemoveAllAction = new LocalizeAction(LocalizeValue.localizeTODO(JavaQuickFixBundle.message("side.effect.action.remove"))) {
      @Override
      public void actionPerformed(ActionEvent e) {
        close(DELETE_ALL);
      }
    };
    myRemoveAllAction.putValue(DEFAULT_ACTION, myRemoveAllAction);
    actions.add(myRemoveAllAction);
    if (myCanCopeWithSideEffects) {
        LocalizeAction makeStmtAction = new LocalizeAction(LocalizeValue.localizeTODO(JavaQuickFixBundle.message("side.effect.action.transform"))) {
        @Override
        public void actionPerformed(ActionEvent e) {
          close(MAKE_STATEMENT);
        }
      };
      actions.add(makeStmtAction);
    }
    myCancelAllAction = new LocalizeAction(LocalizeValue.localizeTODO(JavaQuickFixBundle.message("side.effect.action.cancel"))) {
      @Override
      public void actionPerformed(ActionEvent e) {
        doCancelAction();
      }

    };
    actions.add(myCancelAllAction);
    return actions.toArray(new Action[actions.size()]);
  }

  @Nonnull
  @Override
  protected LocalizeAction getCancelAction() {
    return myCancelAllAction;
  }

  @Nonnull
  @Override
  protected LocalizeAction getOKAction() {
    return myRemoveAllAction;
  }

  @Override
  public void doCancelAction() {
    close(CANCEL);
  }

  @Override
  @RequiredUIAccess
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final String text = sideEffectsDescription();
    final JLabel label = new JLabel(text);
    label.setIcon(TargetAWT.to(UIUtil.getWarningIcon()));
    panel.add(label, BorderLayout.NORTH);
    return panel;
  }

  @RequiredReadAction
  protected String sideEffectsDescription() {
    if (myCanCopeWithSideEffects) {
      return JavaQuickFixBundle.message(
        "side.effect.message2",
        myVariable.getName(),
        myVariable.getType().getPresentableText(),
        myBeforeText,
        myAfterText
      );
    }
    else {
      return JavaQuickFixBundle.message("side.effect.message1", myVariable.getName());
    }
  }
}
