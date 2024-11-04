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
package com.intellij.java.impl.refactoring.removemiddleman;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.ui.MemberSelectionPanel;
import com.intellij.java.impl.refactoring.ui.MemberSelectionTable;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.application.HelpManager;
import consulo.java.localize.JavaRefactoringLocalize;
import consulo.language.editor.refactoring.classMember.DelegatingMemberInfoModel;
import consulo.language.editor.refactoring.ui.RefactoringDialog;
import consulo.localize.LocalizeValue;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings({"OverridableMethodCallInConstructor"})
public class RemoveMiddlemanDialog extends RefactoringDialog {
    private final JTextField fieldNameLabel;

    private final List<MemberInfo> delegateMethods;

    private final PsiField myField;

    RemoveMiddlemanDialog(PsiField field, MemberInfo[] delegateMethods) {
        super(field.getProject(), true);
        myField = field;
        this.delegateMethods = Arrays.asList(delegateMethods);
        fieldNameLabel = new JTextField();
        fieldNameLabel.setText(
            PsiFormatUtil.formatVariable(myField, PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_NAME, PsiSubstitutor.EMPTY)
        );
        setTitle(JavaRefactoringLocalize.removeMiddlemanTitle());
        init();
    }

    @Override
    protected String getDimensionServiceKey() {
        return "RefactorJ.RemoveMiddleman";
    }

    @Override
    protected JComponent createCenterPanel() {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
        final MemberSelectionPanel selectionPanel = new MemberSelectionPanel(
            JavaRefactoringLocalize.removeMiddlemanMethodsToInlineTitle().get(),
            delegateMethods,
            JavaRefactoringLocalize.removeMiddlemanColumnHeader().get()
        );
        final MemberSelectionTable table = selectionPanel.getTable();
        table.setMemberInfoModel(new DelegatingMemberInfoModel<>(table.getMemberInfoModel()) {
            @Override
            public int checkForProblems(@Nonnull final MemberInfo member) {
                return hasSuperMethods(member) ? ERROR : OK;
            }

            @Override
            public String getTooltipText(final MemberInfo member) {
                return hasSuperMethods(member)
                    ? JavaRefactoringLocalize.removeMiddlemanTooltipWarning().get()
                    : super.getTooltipText(member);
            }

            private boolean hasSuperMethods(final MemberInfo member) {
                return member.isChecked() && member.isToAbstract()
                    && member.getMember() instanceof PsiMethod method
                    && method.findDeepestSuperMethods().length > 0;
            }
        });
        panel.add(selectionPanel, BorderLayout.CENTER);
        return panel;
    }

    @Override
    protected JComponent createNorthPanel() {
        fieldNameLabel.setEditable(false);
        final JPanel sourceClassPanel = new JPanel(new BorderLayout());
        sourceClassPanel.add(new JLabel(LocalizeValue.localizeTODO("Delegating field").get()), BorderLayout.NORTH);
        sourceClassPanel.add(fieldNameLabel, BorderLayout.CENTER);
        return sourceClassPanel;
    }

    @Override
    @RequiredUIAccess
    protected void doHelpAction() {
        HelpManager.getInstance().invokeHelp(HelpID.RemoveMiddleman);
    }

    @Override
    protected void doAction() {
        invokeRefactoring(new RemoveMiddlemanProcessor(myField, delegateMethods));
    }
}
