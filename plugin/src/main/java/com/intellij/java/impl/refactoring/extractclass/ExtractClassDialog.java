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
package com.intellij.java.impl.refactoring.extractclass;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.PackageWrapper;
import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import com.intellij.java.impl.refactoring.ui.JavaVisibilityPanel;
import com.intellij.java.impl.refactoring.ui.MemberSelectionPanel;
import com.intellij.java.impl.refactoring.ui.MemberSelectionTable;
import com.intellij.java.impl.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.impl.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.HelpManager;
import consulo.configurable.ConfigurationException;
import consulo.java.localize.JavaRefactoringLocalize;
import consulo.language.editor.refactoring.classMember.DelegatingMemberInfoModel;
import consulo.language.editor.refactoring.classMember.MemberInfoBase;
import consulo.language.editor.refactoring.classMember.MemberInfoChange;
import consulo.language.editor.refactoring.classMember.MemberInfoChangeListener;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringDialog;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.SymbolPresentationUtil;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.FormBuilder;
import consulo.ui.ex.awt.JBLabelDecorator;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;
import java.util.*;

@SuppressWarnings({"OverridableMethodCallInConstructor"})
class ExtractClassDialog extends RefactoringDialog implements MemberInfoChangeListener<PsiMember, MemberInfo> {
    private final Map<MemberInfoBase<PsiMember>, PsiMember> myMember2CauseMap = new HashMap<>();
    private final PsiClass sourceClass;
    private final List<MemberInfo> memberInfo;
    private final JTextField classNameField;
    private final ReferenceEditorComboWithBrowseButton packageTextField;
    private final DestinationFolderComboBox myDestinationFolderComboBox;
    private final JTextField sourceClassTextField = null;
    private CheckBox myGenerateAccessorsCb;
    private final JavaVisibilityPanel myVisibilityPanel;
    private final CheckBox extractAsEnum;
    private final List<MemberInfo> enumConstants = new ArrayList<>();

    @RequiredUIAccess
    ExtractClassDialog(PsiClass sourceClass, PsiMember selectedMember) {
        super(sourceClass.getProject(), true);
        setModal(true);
        setTitle(JavaRefactoringLocalize.extractClassTitle());
        myVisibilityPanel = new JavaVisibilityPanel(true, true);
        myVisibilityPanel.setVisibility(null);
        this.sourceClass = sourceClass;
        DocumentListener docListener = new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                validateButtons();
            }
        };
        classNameField = new JTextField();
        PsiFile file = sourceClass.getContainingFile();
        String text = file instanceof PsiJavaFile javaFile ? javaFile.getPackageName() : "";
        packageTextField = new PackageNameReferenceEditorCombo(
            text,
            myProject,
            "ExtractClass.RECENTS_KEY",
            JavaRefactoringLocalize.chooseDestinationPackageLabel().get()
        );
        packageTextField.getChildComponent().getDocument().addDocumentListener(new consulo.document.event.DocumentAdapter() {
            @Override
            public void documentChanged(consulo.document.event.DocumentEvent e) {
                validateButtons();
            }
        });
        myDestinationFolderComboBox = new DestinationFolderComboBox() {
            @Override
            public String getTargetPackage() {
                return getPackageName();
            }
        };
        myDestinationFolderComboBox.setData(myProject, sourceClass.getContainingFile().getContainingDirectory(),
            packageTextField.getChildComponent()
        );
        classNameField.getDocument().addDocumentListener(docListener);
        MemberInfo.Filter<PsiMember> filter = element -> {
            if (element instanceof PsiMethod method) {
                return !method.isConstructor() && method.getBody() != null;
            }
            else if (element instanceof PsiField) {
                return true;
            }
            else if (element instanceof PsiClass innerClass) {
                return PsiTreeUtil.isAncestor(ExtractClassDialog.this.sourceClass, innerClass, true);
            }
            return false;
        };
        memberInfo = MemberInfo.extractClassMembers(this.sourceClass, filter, false);
        extractAsEnum = CheckBox.create(JavaRefactoringLocalize.extractDelegateAsEnumCheckbox());
        boolean hasConstants = false;
        for (MemberInfo info : memberInfo) {
            PsiMember member = info.getMember();
            if (member.equals(selectedMember)) {
                info.setChecked(true);
            }
            if (!hasConstants && member instanceof PsiField && member.isStatic() && member.isFinal()) {
                hasConstants = true;
            }
        }
        if (!hasConstants) {
            extractAsEnum.setVisible(false);
        }
        super.init();
        validateButtons();
    }

    @Override
    @RequiredUIAccess
    protected void doAction() {
        List<PsiField> fields = getFieldsToExtract();
        List<PsiMethod> methods = getMethodsToExtract();
        List<PsiClass> classes = getClassesToExtract();
        String newClassName = getClassName();
        String packageName = getPackageName();

        Collections.sort(enumConstants, (o1, o2) -> o1.getMember().getTextOffset() - o2.getMember().getTextOffset());
        ExtractClassProcessor processor = new ExtractClassProcessor(sourceClass, fields, methods, classes, packageName,
            myDestinationFolderComboBox.selectDirectory(new PackageWrapper(PsiManager.getInstance(myProject), packageName), false),
            newClassName,
            myVisibilityPanel.getVisibility(),
            isGenerateAccessors(),
            isExtractAsEnum()
                ? enumConstants
                : Collections.<MemberInfo>emptyList()
        );
        if (processor.getCreatedClass() == null) {
            Messages.showErrorDialog(
                TargetAWT.to(myVisibilityPanel.getComponent()),
                JavaRefactoringLocalize.extractDelegateUnableCreateWarningMessage().get()
            );
            classNameField.requestFocusInWindow();
            return;
        }
        invokeRefactoring(processor);
    }

    @Override
    @RequiredReadAction
    protected void canRun() throws ConfigurationException {
        Project project = sourceClass.getProject();
        PsiNameHelper nameHelper = PsiNameHelper.getInstance(project);
        List<PsiMethod> methods = getMethodsToExtract();
        List<PsiField> fields = getFieldsToExtract();
        List<PsiClass> innerClasses = getClassesToExtract();
        if (methods.isEmpty() && fields.isEmpty() && innerClasses.isEmpty()) {
            throw new ConfigurationException(JavaRefactoringLocalize.dialogMessageNothingFoundToExtract());
        }

        String className = getClassName();
        if (className.length() == 0 || !nameHelper.isIdentifier(className)) {
            throw new ConfigurationException(JavaRefactoringLocalize.invalidExtractedClassName(className));
        }

        /*final String packageName = getPackageName();
        if (packageName.length() == 0 || !nameHelper.isQualifiedName(packageName)) {
            throw new ConfigurationException("\'" + packageName + "\' is invalid extracted class package name");
        }*/
        for (PsiClass innerClass : innerClasses) {
            if (className.equals(innerClass.getName())) {
                throw new ConfigurationException(JavaRefactoringLocalize.extractedClassShouldHaveUniqueName(className));
            }
        }
    }

    @Nonnull
    public String getPackageName() {
        return packageTextField.getText().trim();
    }

    @Nonnull
    public String getClassName() {
        return classNameField.getText().trim();
    }

    public List<PsiField> getFieldsToExtract() {
        return getMembersToExtract(true, PsiField.class);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getMembersToExtract(boolean checked, Class<T> memberClass) {
        List<T> out = new ArrayList<>();
        for (MemberInfo info : memberInfo) {
            if (checked && !info.isChecked()) {
                continue;
            }
            if (!checked && info.isChecked()) {
                continue;
            }
            PsiMember member = info.getMember();
            if (memberClass.isAssignableFrom(member.getClass())) {
                out.add((T)member);
            }
        }
        return out;
    }

    public List<PsiMethod> getMethodsToExtract() {
        return getMembersToExtract(true, PsiMethod.class);
    }

    public List<PsiClass> getClassesToExtract() {
        return getMembersToExtract(true, PsiClass.class);
    }

    public List<PsiClassInitializer> getClassInitializersToExtract() {
        return getMembersToExtract(true, PsiClassInitializer.class);
    }

    public boolean isGenerateAccessors() {
        return myGenerateAccessorsCb.getValue();
    }

    public boolean isExtractAsEnum() {
        return extractAsEnum.isVisible() && extractAsEnum.isEnabled() && extractAsEnum.getValue();
    }

    @Override
    protected String getDimensionServiceKey() {
        return "RefactorJ.ExtractClass";
    }

    @Override
    protected JComponent createNorthPanel() {
        FormBuilder builder = FormBuilder.createFormBuilder()
            .addComponent(
                JBLabelDecorator.createJBLabelDecorator(JavaRefactoringLocalize.extractClassFromLabel(sourceClass.getQualifiedName()).get())
                    .setBold(true)
            )
            .addLabeledComponent(JavaRefactoringLocalize.nameForNewClassLabel().get(), classNameField, UIUtil.LARGE_VGAP)
            .addLabeledComponent(new JLabel(), TargetAWT.to(extractAsEnum))
            .addLabeledComponent(JavaRefactoringLocalize.packageForNewClassLabel().get(), packageTextField);

        if (ProjectRootManager.getInstance(myProject).getContentSourceRoots().length > 1) {
            builder.addLabeledComponent(RefactoringLocalize.targetDestinationFolder().get(), myDestinationFolderComboBox);
        }

        return builder.addVerticalGap(5).getPanel();
    }

    @Override
    @RequiredUIAccess
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        MemberSelectionPanel memberSelectionPanel =
            new MemberSelectionPanel(JavaRefactoringLocalize.membersToExtractLabel().get(), memberInfo, "As enum") {
                @Override
                protected MemberSelectionTable createMemberSelectionTable(final List<MemberInfo> memberInfo, String abstractColumnHeader) {
                    return new MemberSelectionTable(memberInfo, abstractColumnHeader) {
                        @Nullable
                        @Override
                        protected Object getAbstractColumnValue(MemberInfo memberInfo) {
                            if (isExtractAsEnum()) {
                                PsiMember member = memberInfo.getMember();
                                if (isConstantField(member)) {
                                    return enumConstants.contains(memberInfo);
                                }
                            }
                            return null;
                        }

                        @Override
                        protected boolean isAbstractColumnEditable(int rowIndex) {
                            MemberInfo info = memberInfo.get(rowIndex);
                            if (info.isChecked()) {
                                PsiMember member = info.getMember();
                                if (isConstantField(member)) {
                                    if (enumConstants.isEmpty()) {
                                        return true;
                                    }
                                    MemberInfo currentEnumConstant = enumConstants.get(0);
                                    if (((PsiField)currentEnumConstant.getMember()).getType().equals(((PsiField)member).getType())) {
                                        return true;
                                    }
                                }
                            }
                            return false;
                        }
                    };
                }
            };
        final MemberSelectionTable table = memberSelectionPanel.getTable();
        table.setMemberInfoModel(new DelegatingMemberInfoModel<>(table.getMemberInfoModel()) {

            @Override
            public int checkForProblems(@Nonnull MemberInfo member) {
                PsiMember cause = getCause(member);
                if (member.isChecked() && cause != null) {
                    return ERROR;
                }
                if (!member.isChecked() && cause != null) {
                    return WARNING;
                }
                return OK;
            }

            @Override
            @RequiredReadAction
            public String getTooltipText(MemberInfo member) {
                PsiMember cause = getCause(member);
                if (cause != null) {
                    String presentation = SymbolPresentationUtil.getSymbolPresentableText(cause);
                    if (member.isChecked()) {
                        return JavaRefactoringLocalize.extractClassDependsOn0From1Tooltip(presentation, sourceClass.getName()).get();
                    }
                    else {
                        String className = getClassName();
                        return JavaRefactoringLocalize.extractClassDependsOn0FromNewClass(presentation, className).get();
                    }
                }
                return null;
            }

            private PsiMember getCause(MemberInfo member) {
                PsiMember cause = myMember2CauseMap.get(member);

                if (cause != null) {
                    return cause;
                }

                BackpointerUsageVisitor visitor;
                if (member.isChecked()) {
                    visitor = new BackpointerUsageVisitor(getFieldsToExtract(), getClassesToExtract(), getMethodsToExtract(), sourceClass);
                }
                else {
                    visitor =
                        new BackpointerUsageVisitor(
                            getMembersToExtract(false, PsiField.class),
                            getMembersToExtract(false, PsiClass.class),
                            getMembersToExtract(false, PsiMethod.class),
                            sourceClass,
                            false
                        );
                }

                member.getMember().accept(visitor);
                cause = visitor.getCause();
                myMember2CauseMap.put(member, cause);
                return cause;
            }
        });
        panel.add(memberSelectionPanel, BorderLayout.CENTER);
        table.addMemberInfoChangeListener(this);
        extractAsEnum.addValueListener(e -> {
            if (extractAsEnum.getValue()) {
                preselectOneTypeEnumConstants();
            }
            table.repaint();
        });
        myGenerateAccessorsCb = CheckBox.create(JavaRefactoringLocalize.extractDelegateGenerateAccessorsCheckbox());
        panel.add(TargetAWT.to(myGenerateAccessorsCb), BorderLayout.SOUTH);

        panel.add(TargetAWT.to(myVisibilityPanel.getComponent()), BorderLayout.EAST);
        return panel;
    }

    private void preselectOneTypeEnumConstants() {
        if (enumConstants.isEmpty()) {
            MemberInfo selected = null;
            for (MemberInfo info : memberInfo) {
                if (info.isChecked()) {
                    selected = info;
                    break;
                }
            }
            if (selected != null && isConstantField(selected.getMember())) {
                enumConstants.add(selected);
                selected.setToAbstract(true);
            }
        }
        for (MemberInfo info : memberInfo) {
            PsiMember member = info.getMember();
            if (isConstantField(member)) {
                if (enumConstants.isEmpty()
                    || ((PsiField)enumConstants.get(0).getMember()).getType().equals(((PsiField)member).getType())) {
                    if (!enumConstants.contains(info)) {
                        enumConstants.add(info);
                    }
                    info.setToAbstract(true);
                }
            }
        }
    }

    private static boolean isConstantField(PsiMember member) {
        return member instanceof PsiField field
            && member.hasModifierProperty(PsiModifier.STATIC)
            // && member.hasModifierProperty(PsiModifier.FINAL)
            && field.hasInitializer();
    }

    @Override
    @RequiredUIAccess
    public JComponent getPreferredFocusedComponent() {
        return classNameField;
    }

    @Override
    @RequiredUIAccess
    protected void doHelpAction() {
        HelpManager helpManager = HelpManager.getInstance();
        helpManager.invokeHelp(HelpID.ExtractClass);
    }

    @Override
    @RequiredUIAccess
    public void memberInfoChanged(MemberInfoChange<PsiMember, MemberInfo> memberInfoChange) {
        validateButtons();
        myMember2CauseMap.clear();
        if (extractAsEnum.isVisible()) {
            for (MemberInfo info : memberInfoChange.getChangedMembers()) {
                if (info.isToAbstract()) {
                    if (!enumConstants.contains(info)) {
                        enumConstants.add(info);
                    }
                }
                else {
                    enumConstants.remove(info);
                }
            }
            extractAsEnum.setEnabled(canExtractEnum());
        }
    }

    private boolean canExtractEnum() {
        List<PsiField> fields = new ArrayList<>();
        List<PsiClass> innerClasses = new ArrayList<>();
        List<PsiMethod> methods = new ArrayList<>();
        for (MemberInfo info : memberInfo) {
            if (info.isChecked()) {
                PsiMember member = info.getMember();
                if (member instanceof PsiField field) {
                    fields.add(field);
                }
                else if (member instanceof PsiMethod method) {
                    methods.add(method);
                }
                else if (member instanceof PsiClass innerClass) {
                    innerClasses.add(innerClass);
                }
            }
        }
        return !new BackpointerUsageVisitor(fields, innerClasses, methods, sourceClass).backpointerRequired();
    }
}
