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
package com.intellij.java.debugger.impl.ui.breakpoints;

import com.intellij.java.debugger.ui.classFilter.ClassFilter;
import com.intellij.java.debugger.impl.InstanceFilter;
import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.ui.InstanceFilterEditor;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.IdeBorderFactory;

import javax.swing.*;
import java.awt.*;

/**
 * User: lex
 * Date: Aug 29, 2003
 * Time: 2:44:38 PM
 */
public class EditInstanceFiltersDialog extends DialogWrapper{
  private InstanceFilterEditor myInstanceFilterEditor;
  private final Project myProject;

  public EditInstanceFiltersDialog (Project project) {
    super(project, true);
    myProject = project;
    setTitle(DebuggerBundle.message("instance.filters.dialog.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    JPanel contentPanel = new JPanel(new BorderLayout());

    Box mainPanel = Box.createHorizontalBox();

    myInstanceFilterEditor = new InstanceFilterEditor(myProject);
    myInstanceFilterEditor.setPreferredSize(new Dimension(400, 200));
    myInstanceFilterEditor.setBorder(IdeBorderFactory.createTitledBorder(
      DebuggerBundle.message("instance.filters.dialog.instance.filters.group"), false));
    mainPanel.add(myInstanceFilterEditor);

    contentPanel.add(mainPanel, BorderLayout.CENTER);

    return contentPanel;
  }

  public void dispose(){
    myInstanceFilterEditor.stopEditing();
    super.dispose();
  }

  public void setFilters(InstanceFilter[] filters) {
    ClassFilter[] cFilters = InstanceFilter.createClassFilters(filters);
    myInstanceFilterEditor.setFilters(cFilters);
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.debugger.ui.breakpoints.EditInstanceFiltersDialog";
  }

  public InstanceFilter[] getFilters() {
    ClassFilter [] cFilters = myInstanceFilterEditor.getFilters();
    InstanceFilter [] ifilters = new InstanceFilter[cFilters.length];
    for (int i = 0; i < ifilters.length; i++) {
      ifilters[i] = InstanceFilter.create(cFilters[i]);
    }
    return ifilters;
  }
}
