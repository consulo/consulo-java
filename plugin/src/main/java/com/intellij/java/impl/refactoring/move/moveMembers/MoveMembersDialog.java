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
package com.intellij.java.impl.refactoring.move.moveMembers;

import com.intellij.java.impl.codeInsight.PackageUtil;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.ui.JavaVisibilityPanel;
import com.intellij.java.impl.refactoring.ui.MemberSelectionPanel;
import com.intellij.java.impl.refactoring.ui.MemberSelectionTable;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.impl.refactoring.util.classMembers.UsesAndInterfacesDependencyMemberInfoModel;
import com.intellij.java.impl.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.application.HelpManager;
import consulo.application.util.function.Computable;
import consulo.configurable.ConfigurationException;
import consulo.document.event.DocumentAdapter;
import consulo.document.event.DocumentEvent;
import consulo.language.editor.refactoring.classMember.MemberInfoChange;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.move.MoveCallback;
import consulo.language.editor.refactoring.ui.RefactoringDialog;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.RecentsManager;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.undoRedo.CommandProcessor;
import consulo.usage.UsageViewUtil;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class MoveMembersDialog extends RefactoringDialog implements MoveMembersOptions {
    @NonNls
    private static final String RECENTS_KEY = "MoveMembersDialog.RECENTS_KEY";
    private MyMemberInfoModel myMemberInfoModel;

    private final Project myProject;
    private final PsiClass mySourceClass;
    private final String mySourceClassName;
    private final List<MemberInfo> myMemberInfos;
    private final ReferenceEditorComboWithBrowseButton myTfTargetClassName;
    private MemberSelectionTable myTable;
    private final MoveCallback myMoveCallback;

    JavaVisibilityPanel myVisibilityPanel;
    private final JCheckBox myIntroduceEnumConstants = new JCheckBox(RefactoringLocalize.moveEnumConstantCb().get(), true);

    public MoveMembersDialog(
        Project project,
        PsiClass sourceClass,
        final PsiClass initialTargetClass,
        Set<PsiMember> preselectMembers,
        MoveCallback moveCallback
    ) {
        super(project, true);
        myProject = project;
        mySourceClass = sourceClass;
        myMoveCallback = moveCallback;
        setTitle(MoveMembersImpl.REFACTORING_NAME);

        mySourceClassName = mySourceClass.getQualifiedName();

        PsiField[] fields = mySourceClass.getFields();
        PsiMethod[] methods = mySourceClass.getMethods();
        PsiClass[] innerClasses = mySourceClass.getInnerClasses();
        ArrayList<MemberInfo> memberList = new ArrayList<>(fields.length + methods.length);

        for (PsiClass innerClass : innerClasses) {
            if (!innerClass.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            MemberInfo info = new MemberInfo(innerClass);
            if (preselectMembers.contains(innerClass)) {
                info.setChecked(true);
            }
            memberList.add(info);
        }
        boolean hasConstantFields = false;
        for (PsiField field : fields) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                MemberInfo info = new MemberInfo(field);
                if (preselectMembers.contains(field)) {
                    info.setChecked(true);
                }
                memberList.add(info);
                hasConstantFields = true;
            }
        }
        if (!hasConstantFields) {
            myIntroduceEnumConstants.setVisible(false);
        }
        for (PsiMethod method : methods) {
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                MemberInfo info = new MemberInfo(method);
                if (preselectMembers.contains(method)) {
                    info.setChecked(true);
                }
                memberList.add(info);
            }
        }
        myMemberInfos = memberList;
        String fqName = initialTargetClass != null && !sourceClass.equals(initialTargetClass) ? initialTargetClass.getQualifiedName() : "";
        myTfTargetClassName = new ReferenceEditorComboWithBrowseButton(
            new ChooseClassAction(),
            fqName,
            myProject,
            true,
            JavaCodeFragment.VisibilityChecker.PROJECT_SCOPE_VISIBLE,
            RECENTS_KEY
        );

        init();
    }

    @Override
    @Nullable
    public String getMemberVisibility() {
        return myVisibilityPanel.getVisibility();
    }

    @Override
    public boolean makeEnumConstant() {
        return myIntroduceEnumConstants.isVisible() && myIntroduceEnumConstants.isEnabled() && myIntroduceEnumConstants.isSelected();
    }

    @Override
    protected String getDimensionServiceKey() {
        return "#com.intellij.refactoring.move.moveMembers.MoveMembersDialog";
    }

    @Override
    protected JComponent createNorthPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel _panel;
        Box box = Box.createVerticalBox();

        _panel = new JPanel(new BorderLayout());
        JTextField sourceClassField = new JTextField();
        sourceClassField.setText(mySourceClassName);
        sourceClassField.setEditable(false);
        _panel.add(new JLabel(RefactoringLocalize.moveMembersMoveMembersFromLabel().get()), BorderLayout.NORTH);
        _panel.add(sourceClassField, BorderLayout.CENTER);
        box.add(_panel);

        box.add(Box.createVerticalStrut(10));

        _panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(RefactoringLocalize.moveMembersToFullyQualifiedNameLabel().get());
        label.setLabelFor(myTfTargetClassName);
        _panel.add(label, BorderLayout.NORTH);
        _panel.add(myTfTargetClassName, BorderLayout.CENTER);
        _panel.add(myIntroduceEnumConstants, BorderLayout.SOUTH);
        box.add(_panel);

        myTfTargetClassName.getChildComponent().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            public void documentChanged(DocumentEvent e) {
                myMemberInfoModel.updateTargetClass();
                validateButtons();
            }
        });

        panel.add(box, BorderLayout.CENTER);
        panel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);

        validateButtons();
        return panel;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        final LocalizeValue title = RefactoringLocalize.moveMembersMembersToBeMovedBorderTitle();
        final MemberSelectionPanel selectionPanel = new MemberSelectionPanel(title.get(), myMemberInfos, null);
        myTable = selectionPanel.getTable();
        myMemberInfoModel = new MyMemberInfoModel();
        myMemberInfoModel.memberInfoChanged(new MemberInfoChange<>(myMemberInfos));
        selectionPanel.getTable().setMemberInfoModel(myMemberInfoModel);
        selectionPanel.getTable().addMemberInfoChangeListener(myMemberInfoModel);
        panel.add(selectionPanel, BorderLayout.CENTER);

        myVisibilityPanel = new JavaVisibilityPanel(true, true);
        myVisibilityPanel.setVisibility(null);
        panel.add(TargetAWT.to(myVisibilityPanel.getComponent()), BorderLayout.EAST);

        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myTfTargetClassName.getChildComponent();
    }

    @Override
    public PsiMember[] getSelectedMembers() {
        final Collection<MemberInfo> selectedMemberInfos = myTable.getSelectedMemberInfos();
        ArrayList<PsiMember> list = new ArrayList<>();
        for (MemberInfo selectedMemberInfo : selectedMemberInfos) {
            list.add(selectedMemberInfo.getMember());
        }
        return list.toArray(new PsiMember[list.size()]);
    }

    @Override
    public String getTargetClassName() {
        return myTfTargetClassName.getText();
    }

    @Override
    protected void doAction() {
        String message = validateInputData();

        if (message != null) {
            if (message.length() != 0) {
                CommonRefactoringUtil.showErrorMessage(
                    MoveMembersImpl.REFACTORING_NAME,
                    message,
                    HelpID.MOVE_MEMBERS,
                    myProject
                );
            }
            return;
        }

        invokeRefactoring(new MoveMembersProcessor(getProject(), myMoveCallback, new MoveMembersOptions() {
            @Override
            public String getMemberVisibility() {
                return MoveMembersDialog.this.getMemberVisibility();
            }

            @Override
            public boolean makeEnumConstant() {
                return MoveMembersDialog.this.makeEnumConstant();
            }

            @Override
            public PsiMember[] getSelectedMembers() {
                return MoveMembersDialog.this.getSelectedMembers();
            }

            @Override
            public String getTargetClassName() {
                return MoveMembersDialog.this.getTargetClassName();
            }
        }));

        JavaRefactoringSettings.getInstance().MOVE_PREVIEW_USAGES = isPreviewUsages();
    }

    @Override
    protected void canRun() throws ConfigurationException {
        //if (getTargetClassName().length() == 0) throw new ConfigurationException("Destination class name not found");
    }

    @Nullable
    @RequiredUIAccess
    private String validateInputData() {
        final PsiManager manager = PsiManager.getInstance(myProject);
        final String fqName = getTargetClassName();
        if (fqName != null && fqName.isEmpty()) {
            return RefactoringLocalize.noDestinationClassSpecified().get();
        }
        else {
            if (!PsiNameHelper.getInstance(manager.getProject()).isQualifiedName(fqName)) {
                return RefactoringLocalize.zeroIsNotALegalFqName(fqName).get();
            }
            else {
                RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, fqName);
                final PsiClass[] targetClass = new PsiClass[]{null};
                CommandProcessor.getInstance().executeCommand(myProject, () -> {
                    try {
                        targetClass[0] = findOrCreateTargetClass(manager, fqName);
                    }
                    catch (IncorrectOperationException e) {
                        CommonRefactoringUtil.showErrorMessage(
                            MoveMembersImpl.REFACTORING_NAME,
                            e.getMessage(),
                            HelpID.MOVE_MEMBERS,
                            myProject);
                    }
                }, RefactoringLocalize.createClassCommand(fqName).get(), null);

                if (targetClass[0] == null) {
                    return "";
                }

                if (mySourceClass.equals(targetClass[0])) {
                    return RefactoringLocalize.sourceAndDestinationClassesShouldBeDifferent().get();
                }
                else if (!mySourceClass.getLanguage().equals(targetClass[0].getLanguage())) {
                    return RefactoringLocalize.moveToDifferentLanguage(
                        UsageViewUtil.getType(mySourceClass),
                        mySourceClass.getQualifiedName(),
                        targetClass[0].getQualifiedName()
                    ).get();
                }
                else {
                    for (MemberInfo info : myMemberInfos) {
                        if (!info.isChecked()) {
                            continue;
                        }
                        if (PsiTreeUtil.isAncestor(info.getMember(), targetClass[0], false)) {
                            return RefactoringLocalize.cannotMoveInnerClass0IntoItself(info.getDisplayName()).get();
                        }
                    }

                    if (!targetClass[0].isWritable()) {
                        CommonRefactoringUtil.checkReadOnlyStatus(myProject, targetClass[0]);
                        return "";
                    }

                    return null;
                }
            }
        }
    }

    @Nullable
    @RequiredUIAccess
    private PsiClass findOrCreateTargetClass(final PsiManager manager, final String fqName) throws IncorrectOperationException {
        final String className;
        final String packageName;
        int dotIndex = fqName.lastIndexOf('.');
        if (dotIndex >= 0) {
            packageName = fqName.substring(0, dotIndex);
            className = (dotIndex + 1 < fqName.length()) ? fqName.substring(dotIndex + 1) : "";
        }
        else {
            packageName = "";
            className = fqName;
        }

        PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(fqName, GlobalSearchScope.projectScope(myProject));
        if (aClass != null) {
            return aClass;
        }

        final PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(
            myProject,
            packageName,
            mySourceClass.getContainingFile().getContainingDirectory(),
            true
        );

        if (directory == null) {
            return null;
        }

        int answer = Messages.showYesNoDialog(
            myProject,
            RefactoringLocalize.class0DoesNotExist(fqName).get(),
            MoveMembersImpl.REFACTORING_NAME,
            UIUtil.getQuestionIcon()
        );
        if (answer != 0) {
            return null;
        }
        final Ref<IncorrectOperationException> eRef = new Ref<>();
        final PsiClass newClass = myProject.getApplication().runWriteAction((Computable<PsiClass>) () -> {
            try {
                return JavaDirectoryService.getInstance().createClass(directory, className);
            }
            catch (IncorrectOperationException e) {
                eRef.set(e);
                return null;
            }
        });
        if (!eRef.isNull()) {
            throw eRef.get();
        }
        return newClass;
    }

    @Override
    protected void doHelpAction() {
        HelpManager.getInstance().invokeHelp(HelpID.MOVE_MEMBERS);
    }

    private class ChooseClassAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createWithInnerClassesScopeChooser(
                RefactoringLocalize.chooseDestinationClass().get(),
                GlobalSearchScope.projectScope(myProject),
                aClass -> aClass.getParent() instanceof PsiFile || aClass.hasModifierProperty(PsiModifier.STATIC),
                null
            );
            final String targetClassName = getTargetClassName();
            if (targetClassName != null) {
                final PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(targetClassName, GlobalSearchScope.allScope(myProject));
                if (aClass != null) {
                    chooser.selectDirectory(aClass.getContainingFile().getContainingDirectory());
                }
                else {
                    chooser.selectDirectory(mySourceClass.getContainingFile().getContainingDirectory());
                }
            }

            chooser.showDialog();
            PsiClass aClass = chooser.getSelected();
            if (aClass != null) {
                myTfTargetClassName.setText(aClass.getQualifiedName());
                myMemberInfoModel.updateTargetClass();
            }
        }
    }

    private class MyMemberInfoModel extends UsesAndInterfacesDependencyMemberInfoModel {
        PsiClass myTargetClass = null;

        public MyMemberInfoModel() {
            super(mySourceClass, null, false, DEFAULT_CONTAINMENT_VERIFIER);
        }

        @Nullable
        public Boolean isFixedAbstract(MemberInfo member) {
            return null;
        }

        public boolean isCheckedWhenDisabled(MemberInfo member) {
            return false;
        }

        public boolean isMemberEnabled(MemberInfo member) {
            if (myTargetClass != null && myTargetClass.isInterface() && !PsiUtil.isLanguageLevel8OrHigher(myTargetClass)) {
                return !(member.getMember() instanceof PsiMethod);
            }
            return super.isMemberEnabled(member);
        }

        public void updateTargetClass() {
            final PsiManager manager = PsiManager.getInstance(myProject);
            myTargetClass =
                JavaPsiFacade.getInstance(manager.getProject()).findClass(getTargetClassName(), GlobalSearchScope.projectScope(myProject));
            myTable.fireExternalDataChange();
            myIntroduceEnumConstants.setEnabled(myTargetClass != null && myTargetClass.isEnum());
        }
    }
}
