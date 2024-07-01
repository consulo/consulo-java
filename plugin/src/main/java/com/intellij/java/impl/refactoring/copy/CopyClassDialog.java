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
package com.intellij.java.impl.refactoring.copy;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.MoveDestination;
import com.intellij.java.impl.refactoring.PackageWrapper;
import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import com.intellij.java.impl.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.java.impl.refactoring.util.RefactoringMessageUtil;
import com.intellij.java.impl.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiNameHelper;
import consulo.application.HelpManager;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.RecentsManager;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.FormBuilder;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.usage.UsageViewUtil;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;

class CopyClassDialog extends DialogWrapper {
  @NonNls private static final String RECENTS_KEY = "CopyClassDialog.RECENTS_KEY";
  private final JLabel myInformationLabel = new JLabel();
  private EditorTextField myNameField;
  private final JLabel myPackageLabel = new JLabel();
  private ReferenceEditorComboWithBrowseButton myTfPackage;
  private final Project myProject;
  private final boolean myDoClone;
  private final PsiDirectory myDefaultTargetDirectory;
  private final DestinationFolderComboBox myDestinationCB = new DestinationFolderComboBox() {
    @Override
    public String getTargetPackage() {
      return myTfPackage.getText().trim();
    }

    @Override
    protected boolean reportBaseInTestSelectionInSource() {
      return true;
    }
  };
  protected MoveDestination myDestination;

  public CopyClassDialog(PsiClass aClass, PsiDirectory defaultTargetDirectory, Project project, boolean doClone) {
    super(project, true);
    myProject = project;
    myDefaultTargetDirectory = defaultTargetDirectory;
    myDoClone = doClone;
    String text = myDoClone ? RefactoringBundle.message("copy.class.clone.0.1", UsageViewUtil.getType(aClass), UsageViewUtil.getLongName(aClass)) :
                  RefactoringBundle.message("copy.class.copy.0.1", UsageViewUtil.getType(aClass), UsageViewUtil.getLongName(aClass));
    myInformationLabel.setText(text);
    myInformationLabel.setFont(myInformationLabel.getFont().deriveFont(Font.BOLD));
    init();
    myDestinationCB.setData(myProject, defaultTargetDirectory,
        s -> setErrorText(s), myTfPackage.getChildComponent());
    myNameField.setText(UsageViewUtil.getShortName(aClass));
    myNameField.selectAll();
  }

  @Nonnull
  protected Action[] createActions() {
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  protected JComponent createCenterPanel() {
    return new JPanel(new BorderLayout());
  }

  protected JComponent createNorthPanel() {
    myNameField = new EditorTextField("");

    String qualifiedName = getQualifiedName();
    myTfPackage = new PackageNameReferenceEditorCombo(qualifiedName, myProject, RECENTS_KEY, RefactoringBundle.message("choose.destination.package"));
    myTfPackage.setTextFieldPreferredWidth(Math.max(qualifiedName.length() + 5, 40));
    myPackageLabel.setText(RefactoringBundle.message("destination.package"));
    myPackageLabel.setLabelFor(myTfPackage);
    if (myDoClone) {
      myTfPackage.setVisible(false);
      myPackageLabel.setVisible(false);
    }

    final JLabel label = new JLabel(RefactoringBundle.message("target.destination.folder"));
    final boolean isMultipleSourceRoots = ProjectRootManager.getInstance(myProject).getContentSourceRoots().length > 1;
    myDestinationCB.setVisible(!myDoClone && isMultipleSourceRoots);
    label.setVisible(!myDoClone && isMultipleSourceRoots);
    label.setLabelFor(myDestinationCB);

    return FormBuilder.createFormBuilder()
      .addComponent(myInformationLabel)
      .addLabeledComponent(RefactoringBundle.message("copy.files.new.name.label"), myNameField, UIUtil.LARGE_VGAP)
      .addLabeledComponent(myPackageLabel, myTfPackage)
      .addLabeledComponent(label, myDestinationCB)
      .getPanel();
  }

  protected String getQualifiedName() {
    String qualifiedName = "";
    if (myDefaultTargetDirectory != null) {
      final PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(myDefaultTargetDirectory);
      if (aPackage != null) {
        qualifiedName = aPackage.getQualifiedName();
      }
    }
    return qualifiedName;
  }

  public MoveDestination getTargetDirectory() {
    return myDestination;
  }

  public String getClassName() {
    return myNameField.getText();
  }

  protected void doOKAction() {
    final String packageName = myTfPackage.getText();
    final String className = getClassName();

    final String[] errorString = new String[1];
    final PsiManager manager = PsiManager.getInstance(myProject);
    final PsiNameHelper nameHelper = PsiNameHelper.getInstance(manager.getProject());
    if (packageName.length() > 0 && !nameHelper.isQualifiedName(packageName)) {
      errorString[0] = RefactoringBundle.message("invalid.target.package.name.specified");
    } else if (className != null && className.isEmpty()) {
      errorString[0] = RefactoringBundle.message("no.class.name.specified");
    } else {
      if (!nameHelper.isIdentifier(className)) {
        errorString[0] = RefactoringMessageUtil.getIncorrectIdentifierMessage(className);
      }
      else if (!myDoClone) {
        try {
          final PackageWrapper targetPackage = new PackageWrapper(manager, packageName);
          myDestination = myDestinationCB.selectDirectory(targetPackage, false);
          if (myDestination == null) return;
        }
        catch (IncorrectOperationException e) {
          errorString[0] = e.getMessage();
        }
      }
      RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, packageName);
    }

    if (errorString[0] != null) {
      if (errorString[0].length() > 0) {
        Messages.showMessageDialog(myProject, errorString[0], RefactoringBundle.message("error.title"), UIUtil.getErrorIcon());
      }
      myNameField.requestFocusInWindow();
      return;
    }
    super.doOKAction();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.COPY_CLASS);
  }
}
