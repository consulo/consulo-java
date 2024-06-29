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
package com.intellij.java.debugger.impl.ui;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.classFilter.ClassFilterEditor;
import com.intellij.java.debugger.ui.classFilter.ClassFilter;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.image.Image;

/**
 * User: lex
 * Date: Aug 29, 2003
 * Time: 2:38:30 PM
 */
public class InstanceFilterEditor extends ClassFilterEditor {
  public InstanceFilterEditor(Project project) {
    super(project);
  }

  protected void addClassFilter() {
    String idString = Messages.showInputDialog(myProject,
      DebuggerBundle.message("add.instance.filter.dialog.prompt"),
      DebuggerBundle.message("add.instance.filter.dialog.title"),
      UIUtil.getQuestionIcon()
    );
    if (idString != null) {
      ClassFilter filter = createFilter(idString);
      if (filter != null) {
        myTableModel.addRow(filter);
        int row = myTableModel.getRowCount() - 1;
        myTable.getSelectionModel().setSelectionInterval(row, row);
        myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));
      }
      myTable.requestFocus();
    }
  }

  protected String getAddButtonText() {
    return DebuggerBundle.message("button.add");
  }

  @Override
  protected Image getAddButtonIcon() {
    return PlatformIconGroup.generalAdd();
  }

  @Override
  protected boolean addPatternButtonVisible() {
    return false;
  }

  protected ClassFilter createFilter(String pattern) {
    try {
      Long.parseLong(pattern);
      return super.createFilter(pattern);
    } catch (NumberFormatException e) {
      Messages.showMessageDialog(this,
        DebuggerBundle.message("add.instance.filter.dialog.error.numeric.value.expected"),
        DebuggerBundle.message("add.instance.filter.dialog.title"),
        UIUtil.getErrorIcon()
      );
      return null;
    }
  }
}
