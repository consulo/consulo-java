/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.generation.ui;

import com.intellij.java.impl.codeInsight.generation.EqualsHashCodeTemplatesManager;
import com.intellij.java.impl.codeInsight.generation.GenerateEqualsHelper;
import com.intellij.java.impl.refactoring.ui.MemberSelectionPanel;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.projectRoots.JavaVersionService;
import com.intellij.java.language.psi.*;
import consulo.application.ui.NonFocusableSetting;
import consulo.ide.impl.idea.ide.wizard.StepAdapter;
import consulo.ide.impl.idea.refactoring.ui.AbstractMemberSelectionPanel;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.java.analysis.codeInsight.JavaCodeInsightBundle;
import consulo.java.impl.codeInsight.JavaCodeInsightSettings;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.refactoring.classMember.AbstractMemberInfoModel;
import consulo.language.editor.refactoring.classMember.MemberInfoBase;
import consulo.language.editor.refactoring.classMember.MemberInfoTooltipManager;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.ComponentWithBrowseButton;
import consulo.ui.ex.awt.VerticalFlowLayout;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import jakarta.annotation.Nonnull;
import org.jetbrains.java.generate.psi.PsiAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author dsl
 */
public class GenerateEqualsWizard extends AbstractGenerateEqualsWizard<PsiClass, PsiMember, MemberInfo> {
    private static final Logger LOG = Logger.getInstance(GenerateEqualsWizard.class);

    private static final MyMemberInfoFilter MEMBER_INFO_FILTER = new MyMemberInfoFilter();

    public static class JavaGenerateEqualsWizardBuilder extends AbstractGenerateEqualsWizard.Builder<PsiClass, PsiMember, MemberInfo> {
        private final PsiClass myClass;

        private final MemberSelectionPanel myEqualsPanel;
        private final MemberSelectionPanel myHashCodePanel;
        private final MemberSelectionPanel myNonNullPanel;
        private final HashMap<PsiMember, MemberInfo> myFieldsToHashCode;
        private final HashMap<PsiMember, MemberInfo> myFieldsToNonNull;
        private final List<MemberInfo> myClassFields;

        private JavaGenerateEqualsWizardBuilder(PsiClass aClass, boolean needEquals, boolean needHashCode) {
            LOG.assertTrue(needEquals || needHashCode);
            myClass = aClass;
            myClassFields = MemberInfo.extractClassMembers(myClass, MEMBER_INFO_FILTER, false);
            for (MemberInfo myClassField : myClassFields) {
                myClassField.setChecked(true);
            }
            if (needEquals) {
                myEqualsPanel = new MemberSelectionPanel(
                    CodeInsightLocalize.generateEqualsHashcodeEqualsFieldsChooserTitle().get(),
                    myClassFields,
                    null
                );
                myEqualsPanel.getTable().setMemberInfoModel(new EqualsMemberInfoModel());
            }
            else {
                myEqualsPanel = null;
            }
            if (needHashCode) {
                final List<MemberInfo> hashCodeMemberInfos;
                if (needEquals) {
                    myFieldsToHashCode = createFieldToMemberInfoMap(true);
                    hashCodeMemberInfos = Collections.emptyList();
                }
                else {
                    hashCodeMemberInfos = myClassFields;
                    myFieldsToHashCode = null;
                }
                myHashCodePanel = new MemberSelectionPanel(
                    CodeInsightLocalize.generateEqualsHashcodeHashcodeFieldsChooserTitle().get(),
                    hashCodeMemberInfos,
                    null
                );
                myHashCodePanel.getTable().setMemberInfoModel(new HashCodeMemberInfoModel());
                if (needEquals) {
                    updateHashCodeMemberInfos(myClassFields);
                }
            }
            else {
                myHashCodePanel = null;
                myFieldsToHashCode = null;
            }
            myNonNullPanel = new MemberSelectionPanel(
                CodeInsightLocalize.generateEqualsHashcodeNonNullFieldsChooserTitle().get(),
                Collections.<MemberInfo>emptyList(),
                null
            );
            myFieldsToNonNull = createFieldToMemberInfoMap(false);
            for (final Map.Entry<PsiMember, MemberInfo> entry : myFieldsToNonNull.entrySet()) {
                entry.getValue().setChecked(NullableNotNullManager.isNotNull(entry.getKey()));
            }
        }

        @Override
        protected List<MemberInfo> getClassFields() {
            return myClassFields;
        }

        @Override
        protected HashMap<PsiMember, MemberInfo> getFieldsToHashCode() {
            return myFieldsToHashCode;
        }

        @Override
        protected HashMap<PsiMember, MemberInfo> getFieldsToNonNull() {
            return myFieldsToNonNull;
        }

        @Override
        protected AbstractMemberSelectionPanel<PsiMember, MemberInfo> getEqualsPanel() {
            return myEqualsPanel;
        }

        @Override
        protected AbstractMemberSelectionPanel<PsiMember, MemberInfo> getHashCodePanel() {
            return myHashCodePanel;
        }

        @Override
        protected AbstractMemberSelectionPanel<PsiMember, MemberInfo> getNonNullPanel() {
            return myNonNullPanel;
        }

        @Override
        protected PsiClass getPsiClass() {
            return myClass;
        }

        @Override
        protected void updateHashCodeMemberInfos(Collection<MemberInfo> equalsMemberInfos) {
            if (myHashCodePanel == null) {
                return;
            }
            List<MemberInfo> hashCodeFields = new ArrayList<>();

            for (MemberInfo equalsMemberInfo : equalsMemberInfos) {
                hashCodeFields.add(myFieldsToHashCode.get(equalsMemberInfo.getMember()));
            }

            myHashCodePanel.getTable().setMemberInfos(hashCodeFields);
        }

        @Override
        protected void updateNonNullMemberInfos(Collection<MemberInfo> equalsMemberInfos) {
            final ArrayList<MemberInfo> list = new ArrayList<>();

            for (MemberInfo equalsMemberInfo : equalsMemberInfos) {
                PsiField field = (PsiField) equalsMemberInfo.getMember();
                if (!(field.getType() instanceof PsiPrimitiveType)) {
                    list.add(myFieldsToNonNull.get(equalsMemberInfo.getMember()));
                }
            }
            myNonNullPanel.getTable().setMemberInfos(list);
        }

        private HashMap<PsiMember, MemberInfo> createFieldToMemberInfoMap(boolean checkedByDefault) {
            Collection<MemberInfo> memberInfos = MemberInfo.extractClassMembers(myClass, MEMBER_INFO_FILTER, false);
            final HashMap<PsiMember, MemberInfo> result = new HashMap<>();
            for (MemberInfo memberInfo : memberInfos) {
                memberInfo.setChecked(checkedByDefault);
                result.put(memberInfo.getMember(), memberInfo);
            }
            return result;
        }
    }

    public GenerateEqualsWizard(Project project, PsiClass aClass, boolean needEquals, boolean needHashCode) {
        super(project, new JavaGenerateEqualsWizardBuilder(aClass, needEquals, needHashCode));
    }

    public PsiField[] getEqualsFields() {
        return myEqualsPanel != null ? memberInfosToFields(myEqualsPanel.getTable().getSelectedMemberInfos()) : null;
    }

    public PsiField[] getHashCodeFields() {
        return myHashCodePanel != null ? memberInfosToFields(myHashCodePanel.getTable().getSelectedMemberInfos()) : null;
    }

    public PsiField[] getNonNullFields() {
        return memberInfosToFields(myNonNullPanel.getTable().getSelectedMemberInfos());
    }

    private static PsiField[] memberInfosToFields(Collection<MemberInfo> infos) {
        ArrayList<PsiField> list = new ArrayList<>();
        for (MemberInfo info : infos) {
            list.add((PsiField) info.getMember());
        }
        return list.toArray(new PsiField[list.size()]);
    }

    @Override
    protected String getHelpID() {
        return "editing.altInsert.equals";
    }

    private void equalsFieldsSelected() {
        Collection<MemberInfo> selectedMemberInfos = myEqualsPanel.getTable().getSelectedMemberInfos();
        updateHashCodeMemberInfos(selectedMemberInfos);
        updateNonNullMemberInfos(selectedMemberInfos);
    }

    @Override
    protected void doOKAction() {
        if (myEqualsPanel != null) {
            equalsFieldsSelected();
        }
        super.doOKAction();
    }

    @Override
    protected int getNextStep(int step) {
        if (step + 1 == getNonNullStepCode()) {
            if (templateDependsOnFieldsNullability()) {
                for (MemberInfo classField : myClassFields) {
                    if (classField.isChecked()) {
                        PsiField field = (PsiField) classField.getMember();
                        if (!(field.getType() instanceof PsiPrimitiveType)) {
                            return getNonNullStepCode();
                        }
                    }
                }
            }
            return step;
        }

        return super.getNextStep(step);
    }

    private static boolean templateDependsOnFieldsNullability() {
        final EqualsHashCodeTemplatesManager templatesManager = EqualsHashCodeTemplatesManager.getInstance();
        final String notNullCheckPresent = "\\.notNull[^\\w]";
        final Pattern pattern = Pattern.compile(notNullCheckPresent);
        return pattern.matcher(templatesManager.getDefaultEqualsTemplate().getTemplate()).find() || pattern.matcher(templatesManager.getDefaultHashcodeTemplate().getTemplate()).find();
    }

    @Override
    protected void addSteps() {
        if (myEqualsPanel != null) {
            addStep(new TemplateChooserStep(myClass.hasModifierProperty(PsiModifier.FINAL), myClass.getProject()));
        }
        super.addSteps();
    }

    private static class MyMemberInfoFilter implements MemberInfoBase.Filter<PsiMember> {
        @Override
        public boolean includeMember(PsiMember element) {
            return element instanceof PsiField && !element.hasModifierProperty(PsiModifier.STATIC);
        }
    }

    private static class EqualsMemberInfoModel extends AbstractMemberInfoModel<PsiMember, MemberInfo> {
        MemberInfoTooltipManager<PsiMember, MemberInfo> myTooltipManager = new MemberInfoTooltipManager<>((MemberInfoTooltipManager.TooltipProvider<PsiMember, MemberInfo>) memberInfo -> {
            if (checkForProblems(memberInfo) == OK) {
                return null;
            }
            if (!(memberInfo.getMember() instanceof PsiField)) {
                return CodeInsightLocalize.generateEqualsHashcodeInternalError().get();
            }
            final PsiField field = (PsiField) memberInfo.getMember();
            if (!JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_5)) {
                final PsiType type = field.getType();
                if (PsiAdapter.isNestedArray(type)) {
                    return CodeInsightLocalize.generateEqualsWarningEqualsForNestedArraysNotSupported().get();
                }
                if (GenerateEqualsHelper.isArrayOfObjects(type)) {
                    return CodeInsightLocalize.generateEqualsWarningGeneratedEqualsCouldBeIncorrect().get();
                }
            }
            return null;
        });

        @Override
        public boolean isMemberEnabled(MemberInfo member) {
            if (member == null || !(member.getMember() instanceof PsiField)) {
                return false;
            }
            final PsiField field = (PsiField) member.getMember();
            final PsiType type = field.getType();
            return JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_5) || !PsiAdapter.isNestedArray(type);
        }

        @Override
        public int checkForProblems(@Nonnull MemberInfo member) {
            if (!(member.getMember() instanceof PsiField)) {
                return ERROR;
            }
            final PsiField field = (PsiField) member.getMember();
            final PsiType type = field.getType();
            if (!JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_5)) {
                if (PsiAdapter.isNestedArray(type)) {
                    return ERROR;
                }
                if (GenerateEqualsHelper.isArrayOfObjects(type)) {
                    return WARNING;
                }
            }
            return OK;
        }

        @Override
        public String getTooltipText(MemberInfo member) {
            return myTooltipManager.getTooltip(member);
        }
    }

    private static class HashCodeMemberInfoModel extends AbstractMemberInfoModel<PsiMember, MemberInfo> {
        private final MemberInfoTooltipManager<PsiMember, MemberInfo> myTooltipManager =
            new MemberInfoTooltipManager<>((MemberInfoTooltipManager.TooltipProvider<PsiMember, MemberInfo>) memberInfo -> {
                if (isMemberEnabled(memberInfo)) {
                    return null;
                }
                if (!(memberInfo.getMember() instanceof PsiField)) {
                    return CodeInsightLocalize.generateEqualsHashcodeInternalError().get();
                }
                final PsiField field = (PsiField) memberInfo.getMember();
                final PsiType type = field.getType();
                if (!(type instanceof PsiArrayType) || JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_5)) {
                    return null;
                }
                return CodeInsightLocalize.generateEqualsHashcodeWarningHashcodeForArraysIsNotSupported().get();
            });

        @Override
        public boolean isMemberEnabled(MemberInfo member) {
            return member != null && member.getMember() instanceof PsiField;
        }

        @Override
        public String getTooltipText(MemberInfo member) {
            return myTooltipManager.getTooltip(member);
        }
    }

    private static class TemplateChooserStep extends StepAdapter {
        private final JComponent myPanel;

        private TemplateChooserStep(boolean isFinal, Project project) {
            myPanel = new JPanel(new VerticalFlowLayout());
            final JPanel templateChooserPanel = new JPanel(new BorderLayout());
            final JLabel templateChooserLabel = new JLabel(JavaCodeInsightBundle.message("generate.equals.hashcode.template"));
            templateChooserPanel.add(templateChooserLabel, BorderLayout.WEST);

            final ComboBox<String> comboBox = new ComboBox<>();
            final ComponentWithBrowseButton<ComboBox> comboBoxWithBrowseButton =
                new ComponentWithBrowseButton<>(comboBox, new MyEditTemplatesListener(project, myPanel, comboBox));
            templateChooserLabel.setLabelFor(comboBox);
            final EqualsHashCodeTemplatesManager manager = EqualsHashCodeTemplatesManager.getInstance();
            comboBox.setModel(new DefaultComboBoxModel<>(manager.getTemplateNames()));
            comboBox.setSelectedItem(manager.getDefaultTemplateBaseName());
            comboBox.addActionListener(M -> manager.setDefaultTemplate((String) comboBox.getSelectedItem()));

            templateChooserPanel.add(comboBoxWithBrowseButton, BorderLayout.CENTER);
            myPanel.add(templateChooserPanel);

            final CheckBox checkbox = CheckBox.create(CodeInsightLocalize.generateEqualsHashcodeAcceptSublcasses());
            NonFocusableSetting.initFocusability(checkbox);
            checkbox.setValue(!isFinal && JavaCodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER);
            checkbox.setEnabled(!isFinal);
            checkbox.addValueListener(M -> JavaCodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER = checkbox.getValue());
            myPanel.add(TargetAWT.to(checkbox));
            myPanel.add(new JLabel(CodeInsightLocalize.generateEqualsHashcodeAcceptSublcassesExplanation().get()));

            final CheckBox gettersCheckbox = CheckBox.create(JavaCodeInsightBundle.message("generate.equals.hashcode.use.getters"));
            NonFocusableSetting.initFocusability(gettersCheckbox);
            gettersCheckbox.setValue(JavaCodeInsightSettings.getInstance().USE_ACCESSORS_IN_EQUALS_HASHCODE);
            gettersCheckbox.addValueListener(
                M -> JavaCodeInsightSettings.getInstance().USE_ACCESSORS_IN_EQUALS_HASHCODE = gettersCheckbox.getValue()
            );
            myPanel.add(TargetAWT.to(gettersCheckbox));
        }

        @Override
        public JComponent getComponent() {
            return myPanel;
        }

        private static class MyEditTemplatesListener implements ActionListener {
            private final Project myProject;
            private final JComponent myParent;
            private final ComboBox<String> myComboBox;

            public MyEditTemplatesListener(Project project, JComponent panel, ComboBox<String> comboBox) {
                myProject = project;
                myParent = panel;
                myComboBox = comboBox;
            }

            @Override
            @RequiredUIAccess
            public void actionPerformed(ActionEvent e) {
                final EqualsHashCodeTemplatesManager templatesManager = EqualsHashCodeTemplatesManager.getInstance();
                final EqualsHashCodeTemplatesPanel ui = new EqualsHashCodeTemplatesPanel(myProject, EqualsHashCodeTemplatesManager.getInstance());
                ui.selectNodeInTree(templatesManager.getDefaultTemplateBaseName());
                ShowSettingsUtil.getInstance().editConfigurable(myParent, ui);
                myComboBox.setModel(new DefaultComboBoxModel<>(templatesManager.getTemplateNames()));
                myComboBox.setSelectedItem(templatesManager.getDefaultTemplateBaseName());
            }
        }
    }
}
