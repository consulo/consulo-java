/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.memberPullUp;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.ui.ClassCellRenderer;
import com.intellij.java.impl.refactoring.ui.MemberSelectionTable;
import com.intellij.java.impl.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.java.impl.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.java.impl.refactoring.util.classMembers.UsesAndInterfacesDependencyMemberInfoModel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.HelpManager;
import consulo.language.editor.refactoring.classMember.MemberInfoModel;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.memberPullUp.PullUpDialogBase;
import consulo.language.editor.refactoring.ui.AbstractMemberSelectionTable;
import consulo.language.editor.ui.util.DocCommentPanel;
import consulo.language.editor.ui.util.DocCommentPolicy;
import consulo.language.psi.PsiElement;
import consulo.language.statistician.StatisticsInfo;
import consulo.language.statistician.StatisticsManager;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.List;

/**
 * @author dsl
 * @since 2002-06-18
 */
public class PullUpDialog extends PullUpDialogBase<MemberInfoStorage, MemberInfo, PsiMember, PsiClass> {
    private final Callback myCallback;
    private DocCommentPanel myJavaDocPanel;

    private final InterfaceContainmentVerifier myInterfaceContainmentVerifier =
        psiMethod -> PullUpProcessor.checkedInterfacesContain(myMemberInfos, psiMethod);

    private static final String PULL_UP_STATISTICS_KEY = "pull.up##";

    public interface Callback {
        boolean checkConflicts(PullUpDialog dialog);
    }

    public PullUpDialog(
        Project project,
        PsiClass aClass,
        List<PsiClass> superClasses,
        MemberInfoStorage memberInfoStorage,
        Callback callback
    ) {
        super(project, aClass, superClasses, memberInfoStorage, JavaPullUpHandler.REFACTORING_NAME.get());
        myCallback = callback;

        init();
    }

    @RequiredUIAccess
    public int getJavaDocPolicy() {
        return myJavaDocPanel.getPolicy();
    }

    @Override
    protected String getDimensionServiceKey() {
        return "#com.intellij.refactoring.memberPullUp.PullUpDialog";
    }

    InterfaceContainmentVerifier getContainmentVerifier() {
        return myInterfaceContainmentVerifier;
    }

    @Override
    protected void initClassCombo(JComboBox classCombo) {
        classCombo.setRenderer(new ClassCellRenderer(classCombo.getRenderer()));
        classCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                if (myMemberSelectionPanel != null) {
                    ((MyMemberInfoModel) myMemberInfoModel).setSuperClass(getSuperClass());
                    myMemberSelectionPanel.getTable().setMemberInfos(myMemberInfos);
                    myMemberSelectionPanel.getTable().fireExternalDataChange();
                }
            }
        });
    }

    @Override
    protected PsiClass getPreselection() {
        PsiClass preselection = RefactoringHierarchyUtil.getNearestBaseClass(myClass, false);

        String statKey = PULL_UP_STATISTICS_KEY + myClass.getQualifiedName();
        for (StatisticsInfo info : StatisticsManager.getInstance().getAllValues(statKey)) {
            String superClassName = info.getValue();
            PsiClass superClass = null;
            for (PsiClass aClass : mySuperClasses) {
                if (Comparing.strEqual(superClassName, aClass.getQualifiedName())) {
                    superClass = aClass;
                    break;
                }
            }
            if (superClass != null && StatisticsManager.getInstance().getUseCount(info) > 0) {
                preselection = superClass;
                break;
            }
        }
        return preselection;
    }

    @Override
    @RequiredUIAccess
    protected void doHelpAction() {
        HelpManager.getInstance().invokeHelp(HelpID.MEMBERS_PULL_UP);
    }

    @Override
    @RequiredUIAccess
    protected void doAction() {
        if (!myCallback.checkConflicts(this)) {
            return;
        }
        JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC = myJavaDocPanel.getPolicy();
        PsiClass superClass = getSuperClass();
        String name = superClass.getQualifiedName();
        if (name != null) {
            StatisticsManager.getInstance().incUseCount(new StatisticsInfo(PULL_UP_STATISTICS_KEY + myClass
                .getQualifiedName(), name));
        }

        List<MemberInfo> infos = getSelectedMemberInfos();
        invokeRefactoring(new PullUpProcessor(myClass, superClass, infos.toArray(new MemberInfo[infos.size()]),
            new DocCommentPolicy(getJavaDocPolicy())
        ));
        close(OK_EXIT_CODE);
    }

    @Override
    @RequiredUIAccess
    protected void addCustomElementsToCentralPanel(JPanel panel) {
        myJavaDocPanel = new DocCommentPanel(RefactoringLocalize.javadocForAbstracts());
        myJavaDocPanel.setPolicy(JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC);
        boolean hasJavadoc = false;
        for (MemberInfo info : myMemberInfos) {
            if (myMemberInfoModel.isAbstractEnabled(info)) {
                info.setToAbstract(myMemberInfoModel.isAbstractWhenDisabled(info));
                if (!hasJavadoc
                    && info.getMember() instanceof PsiDocCommentOwner docCommentOwner
                    && docCommentOwner.getDocComment() != null) {
                    hasJavadoc = true;
                }
            }
        }

        Component component = myJavaDocPanel.getComponent();
        component.setEnabledRecursive(hasJavadoc);
        panel.add(TargetAWT.to(component), BorderLayout.EAST);
    }

    @Override
    protected AbstractMemberSelectionTable<PsiMember, MemberInfo> createMemberSelectionTable(List<MemberInfo> infos) {
        return new MemberSelectionTable(infos, RefactoringLocalize.makeAbstract().get());
    }

    @Override
    protected MemberInfoModel<PsiMember, MemberInfo> createMemberInfoModel() {
        return new MyMemberInfoModel();
    }

    private class MyMemberInfoModel extends UsesAndInterfacesDependencyMemberInfoModel<PsiMember, MemberInfo> {
        public MyMemberInfoModel() {
            super(myClass, getSuperClass(), false, myInterfaceContainmentVerifier);
        }

        @Override
        @RequiredReadAction
        public boolean isMemberEnabled(MemberInfo member) {
            PsiClass currentSuperClass = getSuperClass();
            if (currentSuperClass == null) {
                return true;
            }
            if (myMemberInfoStorage.getDuplicatedMemberInfos(currentSuperClass).contains(member)) {
                return false;
            }
            if (myMemberInfoStorage.getExtending(currentSuperClass).contains(member.getMember())) {
                return false;
            }
            boolean isInterface = currentSuperClass.isInterface();
            if (!isInterface) {
                return true;
            }

            PsiElement element = member.getMember();
            if (element instanceof PsiClass psiClass && psiClass.isInterface()) {
                return true;
            }
            if (element instanceof PsiField field) {
                return field.isStatic();
            }
            if (element instanceof PsiMethod method) {
                PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(currentSuperClass,
                    myClass, PsiSubstitutor.EMPTY
                );
                MethodSignature signature = method.getSignature(superSubstitutor);
                PsiMethod superClassMethod = MethodSignatureUtil.findMethodBySignature(currentSuperClass, signature, false);
                if (superClassMethod != null && !PsiUtil.isLanguageLevel8OrHigher(currentSuperClass)) {
                    return false;
                }
                return !method.isStatic() || PsiUtil.isLanguageLevel8OrHigher(currentSuperClass);
            }
            return true;
        }

        @Override
        @RequiredReadAction
        public boolean isAbstractEnabled(MemberInfo member) {
            PsiClass currentSuperClass = getSuperClass();
            if (currentSuperClass == null || !currentSuperClass.isInterface()) {
                return true;
            }
            return PsiUtil.isLanguageLevel8OrHigher(currentSuperClass);
        }

        @Override
        public boolean isAbstractWhenDisabled(MemberInfo member) {
            PsiClass currentSuperClass = getSuperClass();
            return currentSuperClass != null
                && currentSuperClass.isInterface()
                && member.getMember() instanceof PsiMethod method
                && !method.isStatic();
        }

        @Override
        public int checkForProblems(@Nonnull MemberInfo member) {
            if (member.isChecked()) {
                return OK;
            }
            PsiClass currentSuperClass = getSuperClass();

            if (currentSuperClass != null && currentSuperClass.isInterface()) {
                if (member.getMember().isStatic()) {
                    return super.checkForProblems(member);
                }
                return OK;
            }
            else {
                return super.checkForProblems(member);
            }
        }

        @Override
        public Boolean isFixedAbstract(MemberInfo member) {
            return Boolean.TRUE;
        }
    }
}
