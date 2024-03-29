/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.deadCode;

import com.intellij.java.analysis.impl.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.java.analysis.impl.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.java.impl.codeInspection.ex.EntryPointsManagerImpl;
import com.intellij.java.impl.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.analysis.impl.codeInsight.JavaInspectionsBundle;
import consulo.ui.ex.awt.JBTabbedPane;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awtUnsafe.TargetAWT;

import javax.swing.*;
import java.awt.*;

@ExtensionImpl
public class UnusedDeclarationInspection extends UnusedDeclarationInspectionBase {
  public UnusedDeclarationInspection() {
  }

  @SuppressWarnings("deprecation")
  @Override
  protected UnusedSymbolLocalInspectionBase createUnusedSymbolLocalInspection() {
    return new UnusedSymbolLocalInspection();
  }

  @Override
  public JComponent createOptionsPanel() {
    JTabbedPane tabs = new JBTabbedPane(SwingConstants.TOP);
    tabs.add("Entry points", new OptionsPanel());
    tabs.add("On the fly editor settings", (Component) myLocalInspectionBase.createOptionsPanel());
    return tabs;
  }

  private class OptionsPanel extends JPanel {
    private final JCheckBox myMainsCheckbox;
    private final JCheckBox myAppletToEntries;
    private final JCheckBox myServletToEntries;
    private final JCheckBox myNonJavaCheckbox;

    private OptionsPanel() {
      super(new GridBagLayout());
      GridBagConstraints gc = new GridBagConstraints();
      gc.weightx = 1;
      gc.weighty = 0;
      gc.insets = JBUI.insets(0, 20, 2, 0);
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;

      myMainsCheckbox = new JCheckBox(JavaInspectionsBundle.message("inspection.dead.code.option.main"));
      myMainsCheckbox.setSelected(ADD_MAINS_TO_ENTRIES);
      myMainsCheckbox.addActionListener(e -> ADD_MAINS_TO_ENTRIES = myMainsCheckbox.isSelected());

      gc.gridy = 0;
      add(myMainsCheckbox, gc);

      myAppletToEntries = new JCheckBox(JavaInspectionsBundle.message("inspection.dead.code.option.applet"));
      myAppletToEntries.setSelected(ADD_APPLET_TO_ENTRIES);
      myAppletToEntries.addActionListener(e -> ADD_APPLET_TO_ENTRIES = myAppletToEntries.isSelected());
      gc.gridy++;
      add(myAppletToEntries, gc);

      myServletToEntries = new JCheckBox(JavaInspectionsBundle.message("inspection.dead.code.option.servlet"));
      myServletToEntries.setSelected(ADD_SERVLET_TO_ENTRIES);
      myServletToEntries.addActionListener(e -> ADD_SERVLET_TO_ENTRIES = myServletToEntries.isSelected());
      gc.gridy++;
      add(myServletToEntries, gc);

      // TODO
//      for (final EntryPoint extension : myExtensions) {
//        if (extension.showUI()) {
//          final JCheckBox extCheckbox = new JCheckBox(extension.getDisplayName());
//          extCheckbox.setSelected(extension.isSelected());
//          extCheckbox.addActionListener(e -> extension.setSelected(extCheckbox.isSelected()));
//          gc.gridy++;
//          add(extCheckbox, gc);
//        }
//      }

      myNonJavaCheckbox = new JCheckBox(JavaInspectionsBundle.message("inspection.dead.code.option.external"));
      myNonJavaCheckbox.setSelected(ADD_NONJAVA_TO_ENTRIES);
      myNonJavaCheckbox.addActionListener(e -> ADD_NONJAVA_TO_ENTRIES = myNonJavaCheckbox.isSelected());

      gc.gridy++;
      add(myNonJavaCheckbox, gc);

      Component configureAnnotations = TargetAWT.to(EntryPointsManagerImpl.createConfigureAnnotationsButton());
      gc.fill = GridBagConstraints.NONE;
      gc.gridy++;
      gc.insets.top = 10;
      gc.weighty = 1;

      add(configureAnnotations, gc);
    }

  }
}
