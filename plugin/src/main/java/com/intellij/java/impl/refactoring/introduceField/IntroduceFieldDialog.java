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
package com.intellij.java.impl.refactoring.introduceField;

import com.intellij.java.impl.codeInsight.completion.JavaCompletionUtil;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.introduceParameter.AbstractJavaInplaceIntroducer;
import com.intellij.java.impl.refactoring.ui.NameSuggestionsGenerator;
import com.intellij.java.impl.refactoring.ui.NameSuggestionsManager;
import com.intellij.java.impl.refactoring.ui.TypeSelector;
import com.intellij.java.impl.refactoring.ui.TypeSelectorManager;
import com.intellij.java.impl.refactoring.util.RefactoringMessageUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.HelpManager;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.editor.refactoring.ui.NameSuggestionsField;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;

class IntroduceFieldDialog extends DialogWrapper {


    public static BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace;

    private final Project myProject;
    private final PsiClass myParentClass;
    private final PsiExpression myInitializerExpression;
    private final String myEnteredName;
    private final PsiLocalVariable myLocalVariable;
    private final boolean myIsInvokedOnDeclaration;
    private final boolean myWillBeDeclaredStatic;
    private final TypeSelectorManager myTypeSelectorManager;

    private NameSuggestionsField myNameField;

    private final IntroduceFieldCentralPanel myCentralPanel;

    private TypeSelector myTypeSelector;
    private NameSuggestionsManager myNameSuggestionsManager;
    private static final String REFACTORING_NAME = RefactoringBundle.message("introduce.field.title");

    public IntroduceFieldDialog(
        Project project,
        PsiClass parentClass,
        PsiExpression initializerExpression,
        PsiLocalVariable localVariable,
        boolean isCurrentMethodConstructor, boolean isInvokedOnDeclaration, boolean willBeDeclaredStatic,
        PsiExpression[] occurrences, boolean allowInitInMethod, boolean allowInitInMethodIfAll,
        TypeSelectorManager typeSelectorManager, String enteredName
    ) {
        super(project, true);
        myProject = project;
        myParentClass = parentClass;
        myInitializerExpression = initializerExpression;
        myEnteredName = enteredName;
        myCentralPanel =
            new IntroduceFieldDialogPanel(
                parentClass,
                initializerExpression,
                localVariable,
                isCurrentMethodConstructor,
                isInvokedOnDeclaration,
                willBeDeclaredStatic,
                occurrences,
                allowInitInMethod,
                allowInitInMethodIfAll,
                typeSelectorManager
            );
        myLocalVariable = localVariable;
        myIsInvokedOnDeclaration = isInvokedOnDeclaration;
        myWillBeDeclaredStatic = willBeDeclaredStatic;

        myTypeSelectorManager = typeSelectorManager;

        setTitle(REFACTORING_NAME);
        init();

        myCentralPanel.initializeControls(initializerExpression, ourLastInitializerPlace);
        updateButtons();
    }

    public void setReplaceAllOccurrences(boolean replaceAll) {
        myCentralPanel.setReplaceAllOccurrences(replaceAll);
    }

    public String getEnteredName() {
        return myNameField.getEnteredName();
    }

    public BaseExpressionToFieldHandler.InitializationPlace getInitializerPlace() {

        return myCentralPanel.getInitializerPlace();
    }

    @PsiModifier.ModifierConstant
    public String getFieldVisibility() {
        return myCentralPanel.getFieldVisibility();
    }

    public boolean isReplaceAllOccurrences() {
        return myCentralPanel.isReplaceAllOccurrences();

    }

    public boolean isDeleteVariable() {
        return myCentralPanel.isDeleteVariable();

    }

    public boolean isDeclareFinal() {
        return myCentralPanel.isDeclareFinal();
    }

    public PsiType getFieldType() {
        return myTypeSelector.getSelectedType();
    }


    @Nonnull
    protected Action[] createActions() {
        return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
    }


    protected JComponent createNorthPanel() {

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbConstraints = new GridBagConstraints();

        gbConstraints.insets = JBUI.insets(4, 4, 4, 0);
        gbConstraints.anchor = GridBagConstraints.EAST;
        gbConstraints.fill = GridBagConstraints.BOTH;

        gbConstraints.gridwidth = 1;
        gbConstraints.weightx = 0;
        gbConstraints.weighty = 1;
        gbConstraints.gridx = 0;
        gbConstraints.gridy = 0;

        JLabel type = new JLabel(getTypeLabel());

        panel.add(type, gbConstraints);

        gbConstraints.gridx++;
        gbConstraints.insets = JBUI.insets(4, 0, 4, 4);
        gbConstraints.weightx = 0;
        myTypeSelector = myTypeSelectorManager.getTypeSelector();
        panel.add(myTypeSelector.getComponent(), gbConstraints);

        gbConstraints.insets = JBUI.insets(4, 4, 4, 0);
        gbConstraints.gridwidth = 1;
        gbConstraints.weightx = 0;
        gbConstraints.weighty = 1;
        gbConstraints.gridx = 0;
        gbConstraints.gridy = 1;
        JLabel namePrompt = new JLabel(RefactoringLocalize.namePrompt().get());
        panel.add(namePrompt, gbConstraints);

        gbConstraints.insets = JBUI.insets(4, 0, 4, 4);
        gbConstraints.gridwidth = 1;
        gbConstraints.weightx = 1;
        gbConstraints.gridx = 1;
        gbConstraints.gridy = 1;
        myNameField = new NameSuggestionsField(myProject);
        panel.add(myNameField.getComponent(), gbConstraints);
        myNameField.addDataChangedListener(this::updateButtons);
        namePrompt.setLabelFor(myNameField.getFocusableComponent());

        myNameSuggestionsManager = new NameSuggestionsManager(
            myTypeSelector,
            myNameField,
            createGenerator(
                myWillBeDeclaredStatic,
                myLocalVariable,
                myInitializerExpression,
                myIsInvokedOnDeclaration,
                myEnteredName,
                myParentClass,
                myProject
            )
        );
        myNameSuggestionsManager.setLabelsFor(type, namePrompt);

        return panel;
    }

    private void updateButtons() {
        setOKActionEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(getEnteredName()));
    }

    private String getTypeLabel() {
        return myWillBeDeclaredStatic
            ? RefactoringLocalize.introduceFieldStaticFieldOfType().get()
            : RefactoringLocalize.introduceFieldFieldOfType().get();
    }

    protected JComponent createCenterPanel() {
        return myCentralPanel.createCenterPanel();
    }

    static NameSuggestionsGenerator createGenerator(
        final boolean willBeDeclaredStatic,
        final PsiLocalVariable localVariable,
        final PsiExpression initializerExpression,
        final boolean isInvokedOnDeclaration,
        @Nullable final String enteredName,
        final PsiClass parentClass,
        final Project project
    ) {
        return new NameSuggestionsGenerator() {
            private final JavaCodeStyleManager myCodeStyleManager = JavaCodeStyleManager.getInstance(project);

            @RequiredReadAction
            public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
                VariableKind variableKind = willBeDeclaredStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;

                String propertyName = null;
                if (isInvokedOnDeclaration) {
                    propertyName = myCodeStyleManager.variableNameToPropertyName(localVariable.getName(), VariableKind.LOCAL_VARIABLE);
                }
                SuggestedNameInfo nameInfo =
                    myCodeStyleManager.suggestVariableName(variableKind, propertyName, initializerExpression, type);
                if (initializerExpression != null) {
                    String[] names = nameInfo.names;
                    for (int i = 0, namesLength = names.length; i < namesLength; i++) {
                        String name = names[i];
                        if (parentClass.findFieldByName(name, false) != null) {
                            names[i] = myCodeStyleManager.suggestUniqueVariableName(name, initializerExpression, true);
                        }
                    }
                }
                String[] strings =
                    AbstractJavaInplaceIntroducer.appendUnresolvedExprName(JavaCompletionUtil.completeVariableNameForRefactoring(
                        myCodeStyleManager,
                        type,
                        VariableKind.LOCAL_VARIABLE,
                        nameInfo
                    ), initializerExpression);
                return new SuggestedNameInfo.Delegate(enteredName != null ? ArrayUtil.mergeArrays(
                    new String[]{enteredName},
                    strings
                ) : strings, nameInfo);
            }
        };
    }


    @Override
    @RequiredUIAccess
    protected void doOKAction() {
        String fieldName = getEnteredName();
        LocalizeValue errorString = LocalizeValue.empty();
        if ("".equals(fieldName)) {
            errorString = RefactoringLocalize.noFieldNameSpecified();
        }
        else if (!PsiNameHelper.getInstance(myProject).isIdentifier(fieldName)) {
            errorString = LocalizeValue.localizeTODO(RefactoringMessageUtil.getIncorrectIdentifierMessage(fieldName));
        }
        if (errorString != null) {
            CommonRefactoringUtil.showErrorMessage(
                IntroduceFieldHandler.REFACTORING_NAME,
                errorString,
                HelpID.INTRODUCE_FIELD,
                myProject
            );
            return;
        }

        PsiField oldField = myParentClass.findFieldByName(fieldName, true);

        if (oldField != null) {
            int answer = Messages.showYesNoDialog(
                myProject,
                RefactoringLocalize.fieldExists(fieldName, oldField.getContainingClass().getQualifiedName()).get(),
                IntroduceFieldHandler.REFACTORING_NAME.get(),
                UIUtil.getWarningIcon()
            );
            if (answer != 0) {
                return;
            }
        }

        myCentralPanel.saveFinalState();
        ourLastInitializerPlace = myCentralPanel.getInitializerPlace();
        JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY = getFieldVisibility();

        myNameSuggestionsManager.nameSelected();
        myTypeSelectorManager.typeSelected(getFieldType());
        super.doOKAction();
    }

    public JComponent getPreferredFocusedComponent() {
        return myNameField.getFocusableComponent();
    }

    protected void doHelpAction() {
        HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_FIELD);
    }
}
