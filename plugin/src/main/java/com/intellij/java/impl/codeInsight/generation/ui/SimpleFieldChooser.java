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
package com.intellij.java.impl.codeInsight.generation.ui;

import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.event.DoubleClickListener;
import consulo.ui.ex.awtUnsafe.TargetAWT;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author ven
 */
public class SimpleFieldChooser extends DialogWrapper {
  private final PsiField[] myFields;
  private JList myList;

  public SimpleFieldChooser(PsiField[] members, Project project) {
    super(project, true);
    myFields = members;
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    final DefaultListModel model = new DefaultListModel ();
    for (PsiField member : myFields) {
      model.addElement(member);
    }
    myList = new JBList(model);
    myList.setCellRenderer(new MyListCellRenderer());
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        if (myList.getSelectedValues().length > 0) {
          doOKAction();
          return true;
        }
        return false;
      }
    }.installOn(myList);

    myList.setPreferredSize(new Dimension(300, 400));
    return myList;
  }

  public Object[] getSelectedElements() {
    return myList.getSelectedValues();
  }

  private static class MyListCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      Icon icon = null;
      if (value instanceof PsiField) {
        PsiField field = (PsiField)value;
        icon = TargetAWT.to(IconDescriptorUpdaters.getIcon(field, 0));
        final String text = PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE, PsiSubstitutor.EMPTY);
        setText(text);
      }
      super.setIcon(icon);
      return this;
    }
  }
}
