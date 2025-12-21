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
package com.intellij.java.impl.refactoring.invertBoolean;

import com.intellij.java.impl.refactoring.HelpID;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.HelpManager;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringDialog;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiNamedElement;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageViewUtil;

import javax.swing.*;

/**
 * @author ven
 */
public class InvertBooleanDialog extends RefactoringDialog {
    private JTextField myNameField;
    private JPanel myPanel;
    private JLabel myLabel;
    private JLabel myCaptionLabel;

    private final PsiNamedElement myElement;

    @RequiredReadAction
    public InvertBooleanDialog(PsiNamedElement element) {
        super(element.getProject(), false);
        myElement = element;
        String name = myElement.getName();
        myNameField.setText(name);
        myLabel.setLabelFor(myNameField);
        String typeString = UsageViewUtil.getType(myElement);
        myLabel.setText(RefactoringLocalize.invertBooleanNameOfInvertedElement(typeString).get());
        myCaptionLabel.setText(RefactoringLocalize.invert01(typeString, DescriptiveNameUtil.getDescriptiveName(myElement)).get());

        setTitle(InvertBooleanHandler.REFACTORING_NAME);
        init();
    }

    @Override
    @RequiredUIAccess
    public JComponent getPreferredFocusedComponent() {
        return myNameField;
    }

    @Override
    @RequiredUIAccess
    protected void doAction() {
        Project project = myElement.getProject();
        String name = myNameField.getText().trim();
        if (name.length() == 0) {
            CommonRefactoringUtil.showErrorMessage(
                InvertBooleanHandler.REFACTORING_NAME,
                RefactoringLocalize.pleaseEnterAValidNameForInvertedElement(UsageViewUtil.getType(myElement)),
                HelpID.INVERT_BOOLEAN,
                project
            );
            return;
        }

        invokeRefactoring(new InvertBooleanProcessor(myElement, name));
    }

    @Override
    @RequiredUIAccess
    protected void doHelpAction() {
        HelpManager.getInstance().invokeHelp(HelpID.INVERT_BOOLEAN);
    }

    @Override
    protected JComponent createCenterPanel() {
        return myPanel;
    }
}
