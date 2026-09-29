// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution.impl.ui;

import com.intellij.java.execution.ShortenCommandLine;
import consulo.java.execution.localize.JavaExecutionLocalize;
import consulo.module.Module;
import consulo.module.ui.awt.ModuleDescriptionsComboBox;
import consulo.project.Project;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.ColoredListCellRenderer;
import consulo.ui.ex.awt.ComboBox;

import org.jspecify.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;

public class ShortenCommandLineModeCombo extends ComboBox<ShortenCommandLine> {
  private final Project myProject;

  public ShortenCommandLineModeCombo(Project project, JrePathEditor pathEditor, ModuleDescriptionsComboBox component) {
    myProject = project;
    initModel(null, pathEditor, component.getSelectedModule());
    setRenderer(new ColoredListCellRenderer<ShortenCommandLine>() {
      @Override
      protected void customizeCellRenderer(JList<? extends ShortenCommandLine> list, ShortenCommandLine value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
          ShortenCommandLine defaultMode = ShortenCommandLine.getDefaultMethod(myProject, getJdkRoot(pathEditor, component.getSelectedModule()));
          append(JavaExecutionLocalize.shortenCommandLineUserLocalDefault(defaultMode.getPresentableName()).get());
          append(" - " + defaultMode.getDescription(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        } else {
          append(value.getPresentableName());
          append(" - " + value.getDescription(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
      }
    });
    ActionListener updateModelListener = e ->
    {
      Object item = getSelectedItem();
      initModel((ShortenCommandLine) item, pathEditor, component.getSelectedModule());
    };
    pathEditor.addActionListener(updateModelListener);
    component.addActionListener(updateModelListener);
  }

  private void initModel(ShortenCommandLine preselection, JrePathEditor pathEditor, Module module) {
    removeAllItems();

    String jdkRoot = getJdkRoot(pathEditor, module);
    addItem(null);
    for (ShortenCommandLine mode : UnifiedShortenCommandLineModeCombo.getApplicableModes(jdkRoot)) {
      addItem(mode);
    }

    setSelectedItem(preselection);
  }

  @Nullable
  private static String getJdkRoot(JrePathEditor pathEditor, Module module) {
    return UnifiedShortenCommandLineModeCombo.getJdkRoot(pathEditor.isAlternativeJreSelected(), pathEditor.getJrePathOrName(), module);
  }
}
