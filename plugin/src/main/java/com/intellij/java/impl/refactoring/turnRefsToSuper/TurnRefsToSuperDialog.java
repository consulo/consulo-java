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
package com.intellij.java.impl.refactoring.turnRefsToSuper;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.ui.ClassCellRenderer;
import com.intellij.java.impl.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.HelpManager;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringDialog;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.UIUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author dsl
 * @since 2002-06-06
 */
public class TurnRefsToSuperDialog extends RefactoringDialog {
    @Nonnull
    private final PsiClass mySubClass;
    private final List mySuperClasses;

    private JList mySuperClassesList = null;
    private final JCheckBox myCbReplaceInstanceOf = new JCheckBox();

    TurnRefsToSuperDialog(Project project, @Nonnull PsiClass subClass, List superClasses) {
        super(project, true);

        mySubClass = subClass;
        mySuperClasses = superClasses;

        setTitle(TurnRefsToSuperHandler.REFACTORING_NAME);
        init();
    }

    @Nullable
    public PsiClass getSuperClass() {
        if (mySuperClassesList != null) {
            return (PsiClass) mySuperClassesList.getSelectedValue();
        }
        else {
            return null;
        }
    }

    public boolean isUseInInstanceOf() {
        return myCbReplaceInstanceOf.isSelected();
    }

    @Override
    @RequiredUIAccess
    protected void doHelpAction() {
        HelpManager.getInstance().invokeHelp(HelpID.TURN_REFS_TO_SUPER);
    }

    @Override
    @RequiredUIAccess
    public JComponent getPreferredFocusedComponent() {
        return mySuperClassesList;
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));

        JLabel classListLabel = new JLabel();
        panel.add(classListLabel, BorderLayout.NORTH);

        mySuperClassesList = new JBList(mySuperClasses.toArray());
        mySuperClassesList.setCellRenderer(new ClassCellRenderer(mySuperClassesList.getCellRenderer()));
        mySuperClassesList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        classListLabel.setText(RefactoringLocalize.turnrefstosuperChangeUsagesTo(mySubClass.getQualifiedName()).get());

        PsiClass nearestBase = RefactoringHierarchyUtil.getNearestBaseClass(mySubClass, true);
        int indexToSelect = 0;
        if (nearestBase != null) {
            indexToSelect = mySuperClasses.indexOf(nearestBase);
        }
        mySuperClassesList.setSelectedIndex(indexToSelect);
        panel.add(ScrollPaneFactory.createScrollPane(mySuperClassesList), BorderLayout.CENTER);

        myCbReplaceInstanceOf.setText(RefactoringLocalize.turnrefstosuperUseSuperclassInInstanceof().get());
        myCbReplaceInstanceOf.setSelected(false);
        myCbReplaceInstanceOf.setFocusable(false);
        panel.add(myCbReplaceInstanceOf, BorderLayout.SOUTH);

        return panel;
    }

    @Override
    protected String getDimensionServiceKey() {
        return "#com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperDialog";
    }

    @Override
    protected void doAction() {
        JavaRefactoringSettings.getInstance().TURN_REFS_TO_SUPER_PREVIEW_USAGES = isPreviewUsages();
        PsiClass superClass = getSuperClass();
        if (superClass != null) {
            invokeRefactoring(new TurnRefsToSuperProcessor(getProject(), mySubClass, superClass, isUseInInstanceOf()));
        }
    }

    @Override
    protected JComponent createNorthPanel() {
        return null;
    }
}
