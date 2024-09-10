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
package com.intellij.java.impl.refactoring.extractMethod;

import com.intellij.java.analysis.impl.refactoring.extractMethod.InputVariables;
import com.intellij.java.analysis.impl.refactoring.util.VariableData;
import com.intellij.java.impl.refactoring.ui.JavaComboBoxVisibilityPanel;
import com.intellij.java.impl.refactoring.ui.TypeSelector;
import com.intellij.java.impl.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.impl.refactoring.util.ParameterTablePanel;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.application.ui.NonFocusableSetting;
import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.ide.impl.idea.refactoring.ui.ComboBoxVisibilityPanel;
import consulo.ide.impl.idea.refactoring.ui.MethodSignatureComponent;
import consulo.java.impl.refactoring.JavaRefactoringBundle;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.refactoring.ui.NameSuggestionsField;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;


/**
 * @author Konstantin Bulenkov
 */
public class ExtractMethodDialog extends DialogWrapper implements AbstractExtractDialog {
    private static final String EXTRACT_METHOD_DEFAULT_VISIBILITY = "extract.method.default.visibility";
    public static final String EXTRACT_METHOD_GENERATE_ANNOTATIONS = "extractMethod.generateAnnotations";
    private final Project myProject;
    private final PsiType myReturnType;
    private final PsiTypeParameterList myTypeParameterList;
    private final PsiType[] myExceptions;
    private final boolean myStaticFlag;
    private final boolean myCanBeStatic;
    private final Nullability myNullability;
    private final PsiElement[] myElementsToExtract;
    private final String myHelpId;

    private final NameSuggestionsField myNameField;
    private final MethodSignatureComponent mySignature;
    private final CheckBox myMakeStatic;
    protected CheckBox myMakeVarargs;
    protected CheckBox myGenerateAnnotations;
    private CheckBox myCbChainedConstructor;

    private final InputVariables myVariableData;
    private final PsiClass myTargetClass;
    private ComboBoxVisibilityPanel<String> myVisibilityPanel;

    private boolean myDefaultVisibility = true;
    private boolean myChangingVisibility;

    private final CheckBox myFoldParameters = CheckBox.create(RefactoringLocalize.declareFoldedParameters());
    public JPanel myCenterPanel;
    public JPanel myParamTable;
    private VariableData[] myInputVariables;
    private TypeSelector mySelector;

    public ExtractMethodDialog(Project project,
                               PsiClass targetClass,
                               final InputVariables inputVariables,
                               PsiType returnType,
                               PsiTypeParameterList typeParameterList,
                               PsiType[] exceptions,
                               boolean isStatic,
                               boolean canBeStatic,
                               final boolean canBeChainedConstructor,
                               String title,
                               String helpId,
                               Nullability nullability,
                               final PsiElement[] elementsToExtract) {
        super(project, true);
        myProject = project;
        myTargetClass = targetClass;
        myReturnType = returnType;
        myTypeParameterList = typeParameterList;
        myExceptions = exceptions;
        myStaticFlag = isStatic;
        myCanBeStatic = canBeStatic;
        myNullability = nullability;
        myElementsToExtract = elementsToExtract;
        myVariableData = inputVariables;
        myHelpId = helpId;
        mySignature = new MethodSignatureComponent("", project, JavaFileType.INSTANCE);
        mySignature.setPreferredSize(JBUI.size(500, 100));
        mySignature.setMinimumSize(JBUI.size(500, 100));
        setTitle(title);

        myNameField = new NameSuggestionsField(suggestMethodNames(), myProject);

        NonFocusableSetting.initFocusability(myFoldParameters);

        myMakeStatic = CheckBox.create(RefactoringLocalize.declareStaticCheckbox());
        NonFocusableSetting.initFocusability(myMakeStatic);

        if (canBeChainedConstructor) {
            myCbChainedConstructor = CheckBox.create(RefactoringLocalize.extractChainedConstructorCheckbox());
            NonFocusableSetting.initFocusability(myCbChainedConstructor);
        }

        init();
    }

    protected String[] suggestMethodNames() {
        return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    protected boolean areTypesDirected() {
        return true;
    }

    @Override
    public boolean isMakeStatic() {
        if (myStaticFlag) {
            return true;
        }
        return myCanBeStatic && myMakeStatic.getValueOrError();
    }

    @Override
    public boolean isChainedConstructor() {
        return myCbChainedConstructor != null && myCbChainedConstructor.getValueOrError();
    }

    @Override
    @Nonnull
    protected Action[] createActions() {
        if (myHelpId != null) {
            return new Action[]{
                getOKAction(),
                getCancelAction(),
                getHelpAction()
            };
        }
        else {
            return new Action[]{
                getOKAction(),
                getCancelAction()
            };
        }
    }

    @Override
    public String getChosenMethodName() {
        return myNameField.getEnteredName();
    }

    @Override
    public VariableData[] getChosenParameters() {
        return myInputVariables;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myNameField;
    }

    @Override
    protected String getHelpId() {
        return myHelpId;
    }

    @Override
    protected void doOKAction() {
        MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        checkMethodConflicts(conflicts);
        if (!conflicts.isEmpty()) {
            final ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts);
            if (!conflictsDialog.showAndGet()) {
                if (conflictsDialog.isShowConflicts()) {
                    close(CANCEL_EXIT_CODE);
                }
                return;
            }
        }

        if (myMakeVarargs != null && myMakeVarargs.getValueOrError()) {
            final VariableData data = myInputVariables[myInputVariables.length - 1];
            if (data.type instanceof PsiArrayType) {
                data.type = new PsiEllipsisType(((PsiArrayType) data.type).getComponentType());
            }
        }
        final PsiMethod containingMethod = getContainingMethod();
        if (containingMethod != null && containingMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
            PropertiesComponent.getInstance(myProject).setValue(EXTRACT_METHOD_DEFAULT_VISIBILITY, getVisibility());
        }

        if (myGenerateAnnotations != null && myGenerateAnnotations.isEnabled()) {
            PropertiesComponent.getInstance(myProject).setValue(EXTRACT_METHOD_GENERATE_ANNOTATIONS, myGenerateAnnotations.getValueOrError(), true);
        }
        super.doOKAction();
    }

    @Override
    protected JComponent createNorthPanel() {
        final DockLayout main = DockLayout.create();
        final DockLayout namePanel = DockLayout.create();
        final Label nameLabel = Label.create(RefactoringLocalize.changesignatureNamePrompt());
        namePanel.top(nameLabel);

        Component nameFieldWrap = TargetAWT.wrap(myNameField);
        namePanel.bottom(nameFieldWrap);
        nameLabel.setTarget(nameFieldWrap);

        myNameField.addDataChangedListener(this::update);

        myVisibilityPanel = createVisibilityPanel();
        myVisibilityPanel.registerUpDownActionsFor(myNameField);
        final DockLayout visibilityAndReturnType = DockLayout.create();
        if (!myTargetClass.isInterface()) {
            visibilityAndReturnType.left(TargetAWT.wrap(myVisibilityPanel));
        }

        final DockLayout returnTypePanel = createReturnTypePanel();
        if (returnTypePanel != null) {
            visibilityAndReturnType.right(returnTypePanel);
        }

        final DockLayout visibilityAndName = DockLayout.create();
        visibilityAndName.left(visibilityAndReturnType);
        visibilityAndName.center(namePanel);
        main.center(visibilityAndName);
        setOKActionEnabled(false);

        setOKActionEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(myNameField.getEnteredName()));

        DockLayout options = DockLayout.create();
        options.left(createOptionsPanel());
        main.bottom(options);

        return (JComponent) TargetAWT.to(main);
    }

    protected boolean isVoidReturn() {
        return false;
    }

    @Nullable
    private DockLayout createReturnTypePanel() {
        if (TypeConversionUtil.isPrimitiveWrapper(myReturnType) && myNullability == Nullability.NULLABLE) {
            return null;
        }
        final TypeSelectorManagerImpl manager = new TypeSelectorManagerImpl(myProject, myReturnType, findOccurrences(), areTypesDirected()) {
            @Override
            public PsiType[] getTypesForAll(boolean direct) {
                final PsiType[] types = super.getTypesForAll(direct);
                return !isVoidReturn() ? types : ArrayUtil.prepend(PsiType.VOID, types);
            }
        };
        mySelector = manager.getTypeSelector();
        final JComponent component = mySelector.getComponent();
        if (component instanceof JComboBox) {
            if (isVoidReturn()) {
                mySelector.selectType(PsiType.VOID);
            }
            DockLayout returnTypePanel = DockLayout.create();
            returnTypePanel.top(Label.create(RefactoringLocalize.changesignatureReturnTypePrompt()));
            returnTypePanel.bottom(TargetAWT.wrap(component));

            ((JComboBox) component).addActionListener(e -> {
                if (myGenerateAnnotations != null) {
                    final PsiType selectedType = mySelector.getSelectedType();
                    final boolean enabled = PsiUtil.resolveClassInType(selectedType) != null;
                    if (!enabled) {
                        myGenerateAnnotations.setValue(false);
                    }
                    myGenerateAnnotations.setEnabled(enabled);
                }
                updateSignature();
            });
            return returnTypePanel;
        }
        return null;
    }

    protected PsiExpression[] findOccurrences() {
        return PsiExpression.EMPTY_ARRAY;
    }

    protected Component createOptionsPanel() {
        VerticalLayout layout = VerticalLayout.create();

        //optionsPanel.add(new JLabel("Options: "));

        if (myStaticFlag || myCanBeStatic) {
            myMakeStatic.setEnabled(!myStaticFlag);
            myMakeStatic.setValue(myStaticFlag);
            if (myVariableData.hasInstanceFields()) {
                myMakeStatic.setLabelText(LocalizeValue.localizeTODO(JavaRefactoringBundle.message("declare.static.pass.fields.checkbox")));
            }
            myMakeStatic.addValueListener(e -> {
                if (myVariableData.hasInstanceFields()) {
                    myVariableData.setPassFields(myMakeStatic.getValueOrError());
                    myInputVariables = myVariableData.getInputVariables().toArray(new VariableData[myVariableData.getInputVariables().size()]);
                    updateVarargsEnabled();
                    createParametersPanel();
                }
                updateSignature();
            });
            layout.add(myMakeStatic);
        }
        else {
            myMakeStatic.setValue(false);
            myMakeStatic.setEnabled(false);
        }

        myFoldParameters.setValue(myVariableData.isFoldingSelectedByDefault());
        myFoldParameters.setVisible(myVariableData.isFoldable());
        myVariableData.setFoldingAvailable(myFoldParameters.getValueOrError());
        myInputVariables = myVariableData.getInputVariables().toArray(new VariableData[myVariableData.getInputVariables().size()]);
        myFoldParameters.addValueListener(e -> {
            myVariableData.setFoldingAvailable(myFoldParameters.getValueOrError());
            myInputVariables = myVariableData.getInputVariables().toArray(new VariableData[myVariableData.getInputVariables().size()]);
            updateVarargsEnabled();
            createParametersPanel();
            updateSignature();
        });
        layout.add(myFoldParameters);

        boolean canBeVarargs = false;
        for (VariableData data : myInputVariables) {
            canBeVarargs |= data.type instanceof PsiArrayType;
        }
        if (myVariableData.isFoldable()) {
            canBeVarargs |= myVariableData.isFoldingSelectedByDefault();
        }

        if (canBeVarargs) {
            myMakeVarargs = CheckBox.create(RefactoringLocalize.declareVarargsCheckbox());
            NonFocusableSetting.initFocusability(myMakeVarargs);
            updateVarargsEnabled();
            myMakeVarargs.addValueListener(e -> updateSignature());
            myMakeVarargs.setValue(false);
            layout.add(myMakeVarargs);
        }

        if (myNullability != null && myNullability != Nullability.UNKNOWN) {
            final boolean isSelected = PropertiesComponent.getInstance(myProject).getBoolean(EXTRACT_METHOD_GENERATE_ANNOTATIONS, true);
            myGenerateAnnotations = CheckBox.create(JavaRefactoringBundle.message("declare.generated.annotations"), isSelected);
            myGenerateAnnotations.addValueListener(e -> updateSignature());
            layout.add(myGenerateAnnotations);
        }

        if (myCbChainedConstructor != null) {
            layout.add(myCbChainedConstructor);
            myCbChainedConstructor.addValueListener(valueEvent -> {
                if (myDefaultVisibility) {
                    myChangingVisibility = true;
                    try {
                        if (isChainedConstructor()) {
                            myVisibilityPanel.setVisibility(VisibilityUtil.getVisibilityModifier(myTargetClass.getModifierList()));
                        }
                        else {
                            myVisibilityPanel.setVisibility(PsiModifier.PRIVATE);
                        }
                    }
                    finally {
                        myChangingVisibility = false;
                    }
                }
                update();
            });
        }
        return layout;
    }

    private ComboBoxVisibilityPanel<String> createVisibilityPanel() {
        final JavaComboBoxVisibilityPanel panel = new JavaComboBoxVisibilityPanel();
        final PsiMethod containingMethod = getContainingMethod();
        panel.setVisibility(containingMethod != null && containingMethod.hasModifierProperty(PsiModifier.PUBLIC) ? PropertiesComponent.getInstance(myProject).getValue
            (EXTRACT_METHOD_DEFAULT_VISIBILITY, PsiModifier.PRIVATE) : PsiModifier.PRIVATE);
        panel.addListener(e -> {
            updateSignature();
            if (!myChangingVisibility) {
                myDefaultVisibility = false;
            }
        });
        return panel;
    }

    private PsiMethod getContainingMethod() {
        return PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(myElementsToExtract), PsiMethod.class);
    }

    private void updateVarargsEnabled() {
        if (myMakeVarargs != null) {
            myMakeVarargs.setEnabled(myInputVariables[myInputVariables.length - 1].type instanceof PsiArrayType);
        }
    }

    private void update() {
        myNameField.setEnabled(!isChainedConstructor());
        if (myMakeStatic != null) {
            myMakeStatic.setEnabled(!myStaticFlag && myCanBeStatic && !isChainedConstructor());
        }
        updateSignature();
        setOKActionEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(myNameField.getEnteredName()) || isChainedConstructor());
    }

    @Override
    public String getVisibility() {
        return myTargetClass.isInterface() ? PsiModifier.PUBLIC : myVisibilityPanel.getVisibility();
    }

    @Override
    protected JComponent createCenterPanel() {
        myCenterPanel = new JPanel(new BorderLayout());
        createParametersPanel();

        final Splitter splitter = new Splitter(true);
        splitter.setShowDividerIcon(false);
        splitter.setFirstComponent(myCenterPanel);
        splitter.setSecondComponent(createSignaturePanel());
        return splitter;
    }

    protected boolean isOutputVariable(PsiVariable var) {
        return false;
    }

    protected void createParametersPanel() {
        if (myParamTable != null) {
            myCenterPanel.remove(myParamTable);
        }

        myParamTable = createParameterTableComponent();
        myParamTable.setMinimumSize(JBUI.size(500, 100));
        myCenterPanel.add(myParamTable, BorderLayout.CENTER);
        final JTable table = UIUtil.findComponentOfType(myParamTable, JTable.class);
        myCenterPanel.add(SeparatorFactory.createSeparator("&Parameters", table), BorderLayout.NORTH);
        if (table != null) {
            table.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    if (table.getRowCount() > 0) {
                        final int col = table.getSelectedColumn();
                        final int row = table.getSelectedRow();
                        if (col == -1 || row == -1) {
                            table.getSelectionModel().setSelectionInterval(0, 0);
                            table.getColumnModel().getSelectionModel().setSelectionInterval(0, 0);
                        }
                    }
                }
            });
        }
    }

    protected ParameterTablePanel createParameterTableComponent() {
        return new ParameterTablePanel(myProject, myInputVariables, myElementsToExtract) {
            @Override
            protected void updateSignature() {
                updateVarargsEnabled();
                ExtractMethodDialog.this.updateSignature();
            }

            @Override
            protected void doEnterAction() {
                clickDefaultButton();
            }

            @Override
            protected void doCancelAction() {
                ExtractMethodDialog.this.doCancelAction();
            }

            @Override
            protected boolean areTypesDirected() {
                return ExtractMethodDialog.this.areTypesDirected();
            }

            @Override
            protected boolean isUsedAfter(PsiVariable variable) {
                return isOutputVariable(variable);
            }
        };
    }

    protected JComponent createSignaturePanel() {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(SeparatorFactory.createSeparator(RefactoringLocalize.signaturePreviewBorderTitle().get(), null), BorderLayout.NORTH);
        panel.add(mySignature, BorderLayout.CENTER);

        updateSignature();
        return panel;
    }

    protected void updateSignature() {
        if (mySignature != null) {
            mySignature.setSignature(getSignature());
        }
    }

    protected String getSignature() {
        final StringBuilder buffer = new StringBuilder();
        if (myGenerateAnnotations != null && myGenerateAnnotations.getValueOrError()) {
            final NullableNotNullManager nullManager = NullableNotNullManager.getInstance(myProject);
            buffer.append("@");
            buffer.append(
                StringUtil.getShortName(myNullability == Nullability.NULLABLE ? nullManager.getDefaultNullable() : nullManager.getDefaultNotNull()));
            buffer.append("\n");
        }
        final String visibilityString = VisibilityUtil.getVisibilityString(getVisibility());
        buffer.append(visibilityString);
        if (buffer.length() > 0) {
            buffer.append(" ");
        }
        if (isMakeStatic() && !isChainedConstructor()) {
            buffer.append("static ");
        }
        if (myTypeParameterList != null) {
            final String typeParamsText = myTypeParameterList.getText();
            if (!typeParamsText.isEmpty()) {
                buffer.append(typeParamsText);
                buffer.append(" ");
            }
        }

        if (isChainedConstructor()) {
            buffer.append(myTargetClass.getName());
        }
        else {
            buffer.append(PsiFormatUtil.formatType(mySelector != null ? mySelector.getSelectedType() : myReturnType, 0, PsiSubstitutor.EMPTY));
            buffer.append(" ");
            buffer.append(myNameField.getEnteredName());
        }
        buffer.append("(");

        final String INDENT = StringUtil.repeatSymbol(' ', buffer.length());

        final VariableData[] datas = myInputVariables;
        int count = 0;
        for (int i = 0; i < datas.length; i++) {
            VariableData data = datas[i];
            if (data.passAsParameter) {
                //String typeAndModifiers = PsiFormatUtil.formatVariable(data.variable,
                //  PsiFormatUtil.SHOW_MODIFIERS | PsiFormatUtil.SHOW_TYPE);
                PsiType type = data.type;
                if (i == datas.length - 1 && type instanceof PsiArrayType && myMakeVarargs != null && myMakeVarargs.getValueOrError()) {
                    type = new PsiEllipsisType(((PsiArrayType) type).getComponentType());
                }

                String typeText = type.getPresentableText();
                if (count > 0) {
                    buffer.append(",\n");
                    buffer.append(INDENT);
                }
                buffer.append(typeText);
                buffer.append(" ");
                buffer.append(data.name);
                count++;
            }
        }
        buffer.append(")");
        if (myExceptions.length > 0) {
            buffer.append("\n");
            buffer.append("throws\n");
            for (PsiType exception : myExceptions) {
                buffer.append(INDENT);
                buffer.append(PsiFormatUtil.formatType(exception, 0, PsiSubstitutor.EMPTY));
                buffer.append("\n");
            }
        }
        return buffer.toString();
    }

    @Override
    protected String getDimensionServiceKey() {
        return "extract.method.dialog";
    }

    protected void checkMethodConflicts(MultiMap<PsiElement, String> conflicts) {
        PsiMethod prototype;
        try {
            PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
            prototype = factory.createMethod(myNameField.getEnteredName().trim(), myReturnType);
            if (myTypeParameterList != null) {
                prototype.getTypeParameterList().replace(myTypeParameterList);
            }
            for (VariableData data : myInputVariables) {
                if (data.passAsParameter) {
                    prototype.getParameterList().add(factory.createParameter(data.name, data.type));
                }
            }
            // set the modifiers with which the method is supposed to be created
            PsiUtil.setModifierProperty(prototype, PsiModifier.PRIVATE, true);
        }
        catch (IncorrectOperationException e) {
            return;
        }

        ConflictsUtil.checkMethodConflicts(myTargetClass, null, prototype, conflicts);
    }

    @Override
    public PsiType getReturnType() {
        return mySelector != null ? mySelector.getSelectedType() : myReturnType;
    }
}
