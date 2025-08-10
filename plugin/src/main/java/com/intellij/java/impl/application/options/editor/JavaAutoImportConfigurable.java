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

package com.intellij.java.impl.application.options.editor;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationBundle;
import consulo.application.localize.ApplicationLocalize;
import consulo.configurable.ProjectConfigurable;
import consulo.disposer.Disposable;
import consulo.java.language.localize.JavaLanguageLocalize;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.editor.DaemonCodeAnalyzerSettings;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.ColoredListCellRenderer;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.TitledSeparator;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import javax.swing.*;
import java.awt.*;


/*
 * User: anna
 * Date: 14-Feb-2008
 */
@ExtensionImpl
public class JavaAutoImportConfigurable implements ProjectConfigurable {
    public enum InsertImportOption {
        INSERT_IMPORTS_ALWAYS(ApplicationLocalize.comboboxInsertImportsAll()),
        INSERT_IMPORTS_ASK(ApplicationLocalize.comboboxInsertImportsAsk()),
        INSERT_IMPORTS_NONE(ApplicationLocalize.comboboxInsertImportsNone());

        private final LocalizeValue myText;

        InsertImportOption(LocalizeValue text) {
            myText = text;
        }
    }

    private JComboBox<InsertImportOption> mySmartPasteCombo;
    private JCheckBox myCbShowImportPopup;
    private JPanel myWholePanel;
    private JCheckBox myCbAddUnambiguousImports;
    private JCheckBox myCbAddMethodImports;
    private JCheckBox myCbOptimizeImports;
    private JPanel myExcludeFromImportAndCompletionPanel;
    private final ExcludeTable myExcludePackagesTable;

    @Inject
    public JavaAutoImportConfigurable(Project project) {
        init();

        myExcludePackagesTable = new ExcludeTable(project);
        myExcludeFromImportAndCompletionPanel.add(myExcludePackagesTable.getComponent(), BorderLayout.CENTER);
    }

    @Nonnull
    @Override
    public String getId() {
        return "editor.preferences.import.java";
    }

    @Nullable
    @Override
    public String getParentId() {
        return "editor.preferences.import";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return JavaLanguageLocalize.javaLanguageDisplayName();
    }

    public void addExcludePackage(String packageName) {
        myExcludePackagesTable.addExcludePackage(packageName);
    }

    @RequiredUIAccess
    @Override
    public void reset() {
        CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
        DaemonCodeAnalyzerSettings daemonSettings = DaemonCodeAnalyzerSettings.getInstance();

        switch (codeInsightSettings.ADD_IMPORTS_ON_PASTE) {
            case CodeInsightSettings.YES:
                mySmartPasteCombo.setSelectedItem(InsertImportOption.INSERT_IMPORTS_ALWAYS);
                break;

            case CodeInsightSettings.NO:
                mySmartPasteCombo.setSelectedItem(InsertImportOption.INSERT_IMPORTS_NONE);
                break;

            case CodeInsightSettings.ASK:
                mySmartPasteCombo.setSelectedItem(InsertImportOption.INSERT_IMPORTS_ASK);
                break;
        }


        myCbShowImportPopup.setSelected(daemonSettings.isImportHintEnabled());
        myCbOptimizeImports.setSelected(codeInsightSettings.OPTIMIZE_IMPORTS_ON_THE_FLY);
        myCbAddUnambiguousImports.setSelected(codeInsightSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY);
        myCbAddMethodImports.setSelected(codeInsightSettings.ADD_MEMBER_IMPORTS_ON_THE_FLY);

        myExcludePackagesTable.reset();
    }

    @RequiredUIAccess
    @Override
    public void disposeUIResources() {

    }

    @RequiredUIAccess
    @Override
    public void apply() {
        CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
        DaemonCodeAnalyzerSettings daemonSettings = DaemonCodeAnalyzerSettings.getInstance();

        codeInsightSettings.ADD_IMPORTS_ON_PASTE = getSmartPasteValue();
        daemonSettings.setImportHintEnabled(myCbShowImportPopup.isSelected());
        codeInsightSettings.OPTIMIZE_IMPORTS_ON_THE_FLY = myCbOptimizeImports.isSelected();
        codeInsightSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = myCbAddUnambiguousImports.isSelected();
        codeInsightSettings.ADD_MEMBER_IMPORTS_ON_THE_FLY = myCbAddMethodImports.isSelected();

        myExcludePackagesTable.apply();

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            DaemonCodeAnalyzer.getInstance(project).restart();
        }
    }

    @RequiredUIAccess
    @Override
    public JComponent createComponent(Disposable disposable) {
        return myWholePanel;
    }

    @RequiredUIAccess
    @Override
    public boolean isModified() {
        CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
        DaemonCodeAnalyzerSettings daemonSettings = DaemonCodeAnalyzerSettings.getInstance();

        boolean isModified = isModified(myCbShowImportPopup, daemonSettings.isImportHintEnabled());
        isModified |= isModified(myCbOptimizeImports, codeInsightSettings.OPTIMIZE_IMPORTS_ON_THE_FLY);
        isModified |= isModified(myCbAddUnambiguousImports, codeInsightSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY);
        isModified |= isModified(myCbAddMethodImports, codeInsightSettings.ADD_MEMBER_IMPORTS_ON_THE_FLY);

        isModified |= getSmartPasteValue() != codeInsightSettings.ADD_IMPORTS_ON_PASTE;
        isModified |= myExcludePackagesTable.isModified();

        return isModified;
    }

    private int getSmartPasteValue() {
        InsertImportOption selectedItem = (InsertImportOption) mySmartPasteCombo.getSelectedItem();
        if (InsertImportOption.INSERT_IMPORTS_ALWAYS.equals(selectedItem)) {
            return CodeInsightSettings.YES;
        }
        else if (InsertImportOption.INSERT_IMPORTS_NONE.equals(selectedItem)) {
            return CodeInsightSettings.NO;
        }
        else {
            return CodeInsightSettings.ASK;
        }
    }

    private static boolean isModified(JToggleButton checkBox, boolean value) {
        return checkBox.isSelected() != value;
    }

    private void init() {
        myWholePanel = new JPanel();
        myWholePanel.setLayout(new GridLayoutManager(6, 4, new Insets(0, 0, 0, 0), -1, -1));
        myCbAddUnambiguousImports = new JCheckBox();
        this.$$$loadButtonText$$$(myCbAddUnambiguousImports, ApplicationBundle.message("checkbox.add.unambiguous.imports.on.the.fly"));
        myWholePanel.add(myCbAddUnambiguousImports, new GridConstraints(2, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, JBUI.scale(2), 0, 0), -1, -1));
        myWholePanel.add(panel1, new GridConstraints(0, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, ApplicationBundle.message("combobox.paste.insert.imports"));
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        mySmartPasteCombo = new JComboBox(InsertImportOption.values());
        mySmartPasteCombo.setRenderer(new ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(@Nonnull JList<? extends InsertImportOption> jList, InsertImportOption o, int i, boolean b, boolean b1) {
                if (o != null) {
                    append(o.myText);
                }
            }
        });
        panel1.add(mySmartPasteCombo, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myExcludeFromImportAndCompletionPanel = new JPanel();
        myExcludeFromImportAndCompletionPanel.setLayout(new BorderLayout(0, 0));
        myWholePanel.add(myExcludeFromImportAndCompletionPanel, new GridConstraints(5, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(JBUI.scale(400), JBUI.scale(150)), null, 0, false));
        final TitledSeparator titledSeparator1 = new TitledSeparator();
        titledSeparator1.setText(ApplicationBundle.message("exclude.from.completion.group"));
        myWholePanel.add(titledSeparator1, new GridConstraints(4, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myCbShowImportPopup = new JCheckBox();
        myCbShowImportPopup.setText("classes");
        myCbShowImportPopup.setMnemonic('C');
        myCbShowImportPopup.setDisplayedMnemonicIndex(0);
        myWholePanel.add(myCbShowImportPopup, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myCbAddMethodImports = new JCheckBox();
        myCbAddMethodImports.setText("static methods and fields");
        myCbAddMethodImports.setMnemonic('S');
        myCbAddMethodImports.setDisplayedMnemonicIndex(0);
        myWholePanel.add(myCbAddMethodImports, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        myCbOptimizeImports = new JCheckBox();
        this.$$$loadButtonText$$$(myCbOptimizeImports, ApplicationBundle.message("checkbox.optimize.imports.on.the.fly"));
        myWholePanel.add(myCbOptimizeImports, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JBLabel jBLabel1 = new JBLabel();
        jBLabel1.setText("Show import popup for:");
        myWholePanel.add(jBLabel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        myWholePanel.add(spacer1, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        label1.setLabelFor(mySmartPasteCombo);
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadLabelText$$$(JLabel component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) {
                    break;
                }
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setDisplayedMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) {
                    break;
                }
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myWholePanel;
    }
}
