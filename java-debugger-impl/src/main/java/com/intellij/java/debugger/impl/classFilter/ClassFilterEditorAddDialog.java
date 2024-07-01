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
 * @author: Eugene Zhuravlev
 * Date: Sep 11, 2002
 * Time: 5:23:47 PM
 */
package com.intellij.java.debugger.impl.classFilter;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.localize.UILocalize;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;

class ClassFilterEditorAddDialog extends DialogWrapper {
  private final Project myProject;
  private TextFieldWithBrowseButton myClassName;
  @Nullable
  private final String myHelpId;

  public ClassFilterEditorAddDialog(Project project, @Nullable String helpId) {
    super(project, true);
    myProject = project;
    myHelpId = helpId;
    setTitle(UILocalize.classFilterEditorAddDialogTitle());
    init();
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final JLabel header = new JLabel(UILocalize.labelClassFilterEditorAddDialogFilterPattern().get());
    myClassName = new TextFieldWithBrowseButton(new JTextField(35));
    final JLabel iconLabel = new JBLabel(UIUtil.getQuestionIcon());
    
    panel.add(
      header,
      new GridBagConstraints(
        1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
        JBUI.insets(5, 10, 0, 0), 0, 0
      )
    );
    panel.add(
      myClassName,
      new GridBagConstraints(
        1, 1, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
        JBUI.insets(5, 10, 0, 0), 0, 0
      )
    );
    panel.add(
      iconLabel,
      new GridBagConstraints(
        0, 0, 1, 2, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
        JBUI.insetsTop(15), 0, 0
      )
    );

    myClassName.addActionListener(e -> {
      PsiClass currentClass = getSelectedClass();
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createNoInnerClassesScopeChooser(
        UILocalize.classFilterEditorChooseClassTitle().get(),
        GlobalSearchScope.allScope(myProject),
        null,
        null
      );
      if (currentClass != null) {
        PsiFile containingFile = currentClass.getContainingFile();
        if (containingFile != null) {
          PsiDirectory containingDirectory = containingFile.getContainingDirectory();
          if (containingDirectory != null) {
            chooser.selectDirectory(containingDirectory);
          }
        }
      }
      chooser.showDialog();
      PsiClass selectedClass = chooser.getSelected();
      if (selectedClass != null) {
        myClassName.setText(selectedClass.getQualifiedName());
      }
    });

    myClassName.setEnabled(myProject != null);

    return panel;
  }

  private PsiClass getSelectedClass() {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    String classQName = myClassName.getText();
    if ("".equals(classQName)) {
      return null;
    }
    return JavaPsiFacade.getInstance(psiManager.getProject()).findClass(classQName, GlobalSearchScope.allScope(myProject));
  }

  @RequiredUIAccess
  public JComponent getPreferredFocusedComponent() {
    return myClassName.getTextField();
  }

  public String getPattern() {
    return myClassName.getText();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.debugger.ui.breakpoints.BreakpointsConfigurationDialogFactory.BreakpointsConfigurationDialog.AddFieldBreakpointDialog";
  }

  @Override @Nullable
  protected String getHelpId() {
    return myHelpId;
  }
}
