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
import consulo.annotation.access.RequiredReadAction;
import consulo.application.HelpManager;
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
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Label;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.RecentsManager;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.undoRedo.CommandProcessor;
import consulo.usage.UsageViewUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class MoveMembersDialog extends RefactoringDialog implements MoveMembersOptions {
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
    private final CheckBox myIntroduceEnumConstants = CheckBox.create(RefactoringLocalize.moveEnumConstantCb(), true);

    @RequiredUIAccess
    public MoveMembersDialog(
        Project project,
        PsiClass sourceClass,
        PsiClass initialTargetClass,
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
        List<MemberInfo> memberList = new ArrayList<>(fields.length + methods.length);

        for (PsiClass innerClass : innerClasses) {
            if (!innerClass.isStatic()) {
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
            if (field.isStatic()) {
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
            if (method.isStatic()) {
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

    @Nullable
    @Override
    @RequiredUIAccess
    public String getMemberVisibility() {
        return myVisibilityPanel.getVisibility();
    }

    @Override
    public boolean makeEnumConstant() {
        return myIntroduceEnumConstants.isVisible() && myIntroduceEnumConstants.isEnabled() && myIntroduceEnumConstants.getValueOrError();
    }

    @Override
    protected String getDimensionServiceKey() {
        return "#com.intellij.refactoring.move.moveMembers.MoveMembersDialog";
    }

    @Override
    protected JComponent createNorthPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        JPanel panel;
        Box box = Box.createVerticalBox();

        panel = new JPanel(new BorderLayout());
        JTextField sourceClassField = new JTextField();
        sourceClassField.setText(mySourceClassName);
        sourceClassField.setEditable(false);
        panel.add(new JLabel(RefactoringLocalize.moveMembersMoveMembersFromLabel().get()), BorderLayout.NORTH);
        panel.add(sourceClassField, BorderLayout.CENTER);
        box.add(panel);

        box.add(Box.createVerticalStrut(10));

        panel = new JPanel(new BorderLayout());
        Label label = Label.create(RefactoringLocalize.moveMembersToFullyQualifiedNameLabel());
        label.setTarget(TargetAWT.wrap(myTfTargetClassName));
        panel.add(TargetAWT.to(label), BorderLayout.NORTH);
        panel.add(myTfTargetClassName, BorderLayout.CENTER);
        panel.add(TargetAWT.to(myIntroduceEnumConstants), BorderLayout.SOUTH);
        box.add(panel);

        myTfTargetClassName.setButtonIcon(PlatformIconGroup.nodesClass());
        myTfTargetClassName.getChildComponent().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            @RequiredUIAccess
            public void documentChanged(DocumentEvent e) {
                myMemberInfoModel.updateTargetClass();
                validateButtons();
            }
        });

        mainPanel.add(box, BorderLayout.CENTER);
        mainPanel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);

        validateButtons();
        return mainPanel;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        LocalizeValue title = RefactoringLocalize.moveMembersMembersToBeMovedBorderTitle();
        MemberSelectionPanel selectionPanel = new MemberSelectionPanel(title.get(), myMemberInfos, null);
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
    @RequiredUIAccess
    public JComponent getPreferredFocusedComponent() {
        return myTfTargetClassName.getChildComponent();
    }

    @Override
    public PsiMember[] getSelectedMembers() {
        Collection<MemberInfo> selectedMemberInfos = myTable.getSelectedMemberInfos();
        List<PsiMember> list = new ArrayList<>();
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
    @RequiredUIAccess
    protected void doAction() {
        LocalizeValue message = validateInputData();

        if (message != null) {
            if (message != LocalizeValue.empty()) {
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
            @RequiredUIAccess
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
    private LocalizeValue validateInputData() {
        PsiManager manager = PsiManager.getInstance(myProject);
        String fqName = getTargetClassName();
        if (fqName != null && fqName.isEmpty()) {
            return RefactoringLocalize.noDestinationClassSpecified();
        }
        else {
            if (!PsiNameHelper.getInstance(manager.getProject()).isQualifiedName(fqName)) {
                return RefactoringLocalize.zeroIsNotALegalFqName(fqName);
            }
            else {
                RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, fqName);
                PsiClass[] targetClass = new PsiClass[]{null};
                CommandProcessor.getInstance().newCommand()
                    .project(myProject)
                    .name(RefactoringLocalize.createClassCommand(fqName))
                    .run(() -> {
                        try {
                            targetClass[0] = findOrCreateTargetClass(manager, fqName);
                        }
                        catch (IncorrectOperationException e) {
                            CommonRefactoringUtil.showErrorMessage(
                                MoveMembersImpl.REFACTORING_NAME,
                                LocalizeValue.ofNullable(e.getMessage()),
                                HelpID.MOVE_MEMBERS,
                                myProject
                            );
                        }
                    });

                if (targetClass[0] == null) {
                    return LocalizeValue.empty();
                }

                if (mySourceClass.equals(targetClass[0])) {
                    return RefactoringLocalize.sourceAndDestinationClassesShouldBeDifferent();
                }
                else if (!mySourceClass.getLanguage().equals(targetClass[0].getLanguage())) {
                    return RefactoringLocalize.moveToDifferentLanguage(
                        UsageViewUtil.getType(mySourceClass),
                        mySourceClass.getQualifiedName(),
                        targetClass[0].getQualifiedName()
                    );
                }
                else {
                    for (MemberInfo info : myMemberInfos) {
                        if (!info.isChecked()) {
                            continue;
                        }
                        if (PsiTreeUtil.isAncestor(info.getMember(), targetClass[0], false)) {
                            return RefactoringLocalize.cannotMoveInnerClass0IntoItself(info.getDisplayName());
                        }
                    }

                    if (!targetClass[0].isWritable()) {
                        CommonRefactoringUtil.checkReadOnlyStatus(myProject, targetClass[0]);
                        return LocalizeValue.empty();
                    }

                    return null;
                }
            }
        }
    }

    @Nullable
    @RequiredUIAccess
    private PsiClass findOrCreateTargetClass(PsiManager manager, String fqName) throws IncorrectOperationException {
        String className;
        String packageName;
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

        PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(
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
            MoveMembersImpl.REFACTORING_NAME.get(),
            UIUtil.getQuestionIcon()
        );
        if (answer != 0) {
            return null;
        }
        SimpleReference<IncorrectOperationException> eRef = new SimpleReference<>();
        PsiClass newClass = myProject.getApplication().runWriteAction((Supplier<PsiClass>) () -> {
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
    @RequiredUIAccess
    protected void doHelpAction() {
        HelpManager.getInstance().invokeHelp(HelpID.MOVE_MEMBERS);
    }

    private class ChooseClassAction implements ActionListener {
        @Override
        @RequiredUIAccess
        public void actionPerformed(ActionEvent e) {
            TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createWithInnerClassesScopeChooser(
                RefactoringLocalize.chooseDestinationClass().get(),
                GlobalSearchScope.projectScope(myProject),
                aClass -> aClass.getParent() instanceof PsiFile || aClass.isStatic(),
                null
            );
            String targetClassName = getTargetClassName();
            if (targetClassName != null) {
                PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(targetClassName, GlobalSearchScope.allScope(myProject));
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

        @RequiredReadAction
        public boolean isMemberEnabled(MemberInfo member) {
            if (myTargetClass != null && myTargetClass.isInterface() && !PsiUtil.isLanguageLevel8OrHigher(myTargetClass)) {
                return !(member.getMember() instanceof PsiMethod);
            }
            return super.isMemberEnabled(member);
        }

        @RequiredUIAccess
        public void updateTargetClass() {
            PsiManager manager = PsiManager.getInstance(myProject);
            myTargetClass =
                JavaPsiFacade.getInstance(manager.getProject()).findClass(getTargetClassName(), GlobalSearchScope.projectScope(myProject));
            myTable.fireExternalDataChange();
            myIntroduceEnumConstants.setEnabled(myTargetClass != null && myTargetClass.isEnum());
        }
    }
}
