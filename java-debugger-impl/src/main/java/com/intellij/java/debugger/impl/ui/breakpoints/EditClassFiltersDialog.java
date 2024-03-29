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

/*
 * Class EditClassFiltersDialog
 * @author Jeka
 */
package com.intellij.java.debugger.impl.ui.breakpoints;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.language.util.ClassFilter;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.IdeBorderFactory;
import com.intellij.java.debugger.impl.classFilter.ClassFilterEditor;

import javax.swing.*;
import java.awt.*;

public class EditClassFiltersDialog extends DialogWrapper {
  private ClassFilterEditor myClassFilterEditor;
  private ClassFilterEditor myClassExclusionFilterEditor;
  private Project myProject;
  private ClassFilter myChooserFilter;

  public EditClassFiltersDialog(Project project) {
    this(project, null);
  }

  public EditClassFiltersDialog(Project project, ClassFilter filter) {
    super(project, true);
    myChooserFilter = filter;
    myProject = project;
    setTitle(DebuggerBundle.message("class.filters.dialog.title"));
    init();
  }


  protected JComponent createCenterPanel() {
    JPanel contentPanel = new JPanel(new BorderLayout());

    Box mainPanel = Box.createHorizontalBox();

    myClassFilterEditor = new ClassFilterEditor(myProject, myChooserFilter, "reference.viewBreakpoints.classFilters.newPattern");
    myClassFilterEditor.setPreferredSize(new Dimension(400, 200));
    myClassFilterEditor.setBorder(IdeBorderFactory.createTitledBorder(
      DebuggerBundle.message("class.filters.dialog.inclusion.filters.group"), false));
    mainPanel.add(myClassFilterEditor);

    myClassExclusionFilterEditor = new ClassFilterEditor(myProject, myChooserFilter, "reference.viewBreakpoints.classFilters.newPattern");
    myClassExclusionFilterEditor.setPreferredSize(new Dimension(400, 200));
    myClassExclusionFilterEditor.setBorder(IdeBorderFactory.createTitledBorder(
      DebuggerBundle.message("class.filters.dialog.exclusion.filters.group"), false));
    mainPanel.add(myClassExclusionFilterEditor);

    contentPanel.add(mainPanel, BorderLayout.CENTER);

    return contentPanel;
  }

  public void dispose(){
    myClassFilterEditor.stopEditing();
    super.dispose();
  }

  public void setFilters(com.intellij.java.debugger.ui.classFilter.ClassFilter[] filters, com.intellij.java.debugger.ui.classFilter.ClassFilter[] inverseFilters) {
    myClassFilterEditor.setFilters(filters);
    myClassExclusionFilterEditor.setFilters(inverseFilters);
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.debugger.ui.breakpoints.EditClassFiltersDialog";
  }

  public com.intellij.java.debugger.ui.classFilter.ClassFilter[] getFilters() {
    return myClassFilterEditor.getFilters();
  }

  public com.intellij.java.debugger.ui.classFilter.ClassFilter[] getExclusionFilters() {
    return myClassExclusionFilterEditor.getFilters();
  }

  protected String getHelpId() {
    return "reference.viewBreakpoints.classFilters";
  }
}