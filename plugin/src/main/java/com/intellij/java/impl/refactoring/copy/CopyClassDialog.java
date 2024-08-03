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
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.Label;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.RecentsManager;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.FormBuilder;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.usage.UsageViewUtil;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;

class CopyClassDialog extends DialogWrapper {
    private static final String RECENTS_KEY = "CopyClassDialog.RECENTS_KEY";

    private final JLabel myInformationLabel;
    private EditorTextField myNameField;
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
        LocalizeValue text = myDoClone
            ? RefactoringLocalize.copyClassClone01(UsageViewUtil.getType(aClass), UsageViewUtil.getLongName(aClass))
            : RefactoringLocalize.copyClassCopy01(UsageViewUtil.getType(aClass), UsageViewUtil.getLongName(aClass));
        myInformationLabel = new JLabel();
        myInformationLabel.setText(text.get());
        myInformationLabel.setFont(myInformationLabel.getFont().deriveFont(Font.BOLD));
        init();
        myDestinationCB.setData(myProject, defaultTargetDirectory, this::setErrorText, myTfPackage.getChildComponent());
        myNameField.setText(UsageViewUtil.getShortName(aClass));
        myNameField.selectAll();
    }

    @Override
    @Nonnull
    protected Action[] createActions() {
        return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myNameField;
    }

    @Override
    protected JComponent createCenterPanel() {
        return new JPanel(new BorderLayout());
    }

    @Override
    protected JComponent createNorthPanel() {
        myNameField = new EditorTextField("");

        String qualifiedName = getQualifiedName();
        myTfPackage = new PackageNameReferenceEditorCombo(
            qualifiedName,
            myProject,
            RECENTS_KEY,
            RefactoringLocalize.chooseDestinationPackage().get()
        );
        myTfPackage.setTextFieldPreferredWidth(Math.max(qualifiedName.length() + 5, 40));

        Label packageLabel = Label.create(RefactoringLocalize.destinationPackage());
        packageLabel.setTarget(TargetAWT.wrap(myTfPackage));
        if (myDoClone) {
            myTfPackage.setVisible(false);
            packageLabel.setVisible(false);
        }

        Label label = Label.create(RefactoringLocalize.targetDestinationFolder());
        final boolean isMultipleSourceRoots = ProjectRootManager.getInstance(myProject).getContentSourceRoots().length > 1;
        myDestinationCB.setVisible(!myDoClone && isMultipleSourceRoots);
        label.setVisible(!myDoClone && isMultipleSourceRoots);
        label.setTarget(TargetAWT.wrap(myDestinationCB));

        return FormBuilder.createFormBuilder()
            .addComponent(myInformationLabel)
            .addLabeledComponent(RefactoringLocalize.copyFilesNewNameLabel().get(), myNameField, UIUtil.LARGE_VGAP)
            .addLabeledComponent((JComponent) TargetAWT.to(packageLabel), myTfPackage)
            .addLabeledComponent((JComponent) TargetAWT.to(label), myDestinationCB)
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

    @Override
    @RequiredUIAccess
    protected void doOKAction() {
        final String packageName = myTfPackage.getText();
        final String className = getClassName();

        final String[] errorString = new String[1];
        final PsiManager manager = PsiManager.getInstance(myProject);
        final PsiNameHelper nameHelper = PsiNameHelper.getInstance(manager.getProject());
        if (packageName.length() > 0 && !nameHelper.isQualifiedName(packageName)) {
            errorString[0] = RefactoringLocalize.invalidTargetPackageNameSpecified().get();
        }
        else if (className != null && className.isEmpty()) {
            errorString[0] = RefactoringLocalize.noClassNameSpecified().get();
        }
        else {
            if (!nameHelper.isIdentifier(className)) {
                errorString[0] = RefactoringMessageUtil.getIncorrectIdentifierMessage(className);
            }
            else if (!myDoClone) {
                try {
                    final PackageWrapper targetPackage = new PackageWrapper(manager, packageName);
                    myDestination = myDestinationCB.selectDirectory(targetPackage, false);
                    if (myDestination == null) {
                        return;
                    }
                }
                catch (IncorrectOperationException e) {
                    errorString[0] = e.getMessage();
                }
            }
            RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, packageName);
        }

        if (errorString[0] != null) {
            if (errorString[0].length() > 0) {
                Messages.showMessageDialog(myProject, errorString[0], RefactoringLocalize.errorTitle().get(), UIUtil.getErrorIcon());
            }
            myNameField.requestFocusInWindow();
            return;
        }
        super.doOKAction();
    }

    @Override
    protected void doHelpAction() {
        HelpManager.getInstance().invokeHelp(HelpID.COPY_CLASS);
    }
}
