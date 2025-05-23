/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.find.findUsages;

import com.intellij.java.analysis.impl.find.findUsages.JavaThrowFindUsagesOptions;
import com.intellij.java.analysis.impl.psi.impl.search.ThrowSearchUtil;
import consulo.find.FindBundle;
import consulo.find.FindUsagesHandler;
import consulo.find.localize.FindLocalize;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.ui.ex.StateRestoringCheckBoxWrapper;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;

public class FindThrowUsagesDialog extends JavaFindUsagesDialog<JavaThrowFindUsagesOptions> {
    private StateRestoringCheckBoxWrapper myCbUsages;
    private JComboBox myCbExns;
    private boolean myHasFindWhatPanel;
    private ThrowSearchUtil.Root[] myRoots;

    public FindThrowUsagesDialog(@Nonnull PsiElement element,
                                 @Nonnull Project project,
                                 @Nonnull JavaThrowFindUsagesOptions findUsagesOptions,
                                 boolean toShowInNewTab,
                                 boolean mustOpenInNewTab,
                                 boolean isSingleFile,
                                 @Nonnull FindUsagesHandler handler) {
        super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
    }

    @Override
    protected void init() {
        // Kludge: myRoots used in super.init, which caller from constructor
        myRoots = ThrowSearchUtil.getSearchRoots(myPsiElement);
        super.init();
    }

    @Override
    public JComponent getPreferredFocusedControl() {
        return myHasFindWhatPanel ? (JComponent) TargetAWT.to(myCbUsages.getComponent()) : null;
    }

    @Override
    protected JComponent createNorthPanel() {
        final JComponent panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbConstraints = new GridBagConstraints();

        gbConstraints.insets = new Insets(0, 0, UIUtil.DEFAULT_VGAP, 0);
        gbConstraints.fill = GridBagConstraints.BOTH;
        gbConstraints.weightx = 1;
        gbConstraints.weighty = 1;
        gbConstraints.anchor = GridBagConstraints.EAST;
        myCbExns = new JComboBox(myRoots);
        panel.add(myCbExns, gbConstraints);

        return panel;
    }

    @Override
    public void calcFindUsagesOptions(final JavaThrowFindUsagesOptions options) {
        super.calcFindUsagesOptions(options);
        options.isUsages = isSelected(myCbUsages) || !myHasFindWhatPanel;
    }

    @Override
    protected JPanel createFindWhatPanel() {
        final JPanel findWhatPanel = new JPanel();
        findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.what.group"), true));
        findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

        myCbUsages = addCheckboxToPanel(FindLocalize.findWhatUsagesCheckbox(), myFindUsagesOptions.isUsages, findWhatPanel, true);
        //final ThrowSearchUtil.Root[] searchRoots = ThrowSearchUtil.getSearchRoots(getPsiElement ());

        //final PsiThrowStatement throwStatement = (PsiThrowStatement)getPsiElement();
        //final boolean exactExnType = ThrowSearchUtil.isExactExnType(throwStatement.getException ());
        //if (exactExnType) {
        //  myCbStrict.setEnabled(false);
        //}
        myHasFindWhatPanel = true;
        return findWhatPanel;
    }

    @Override
    protected void doOKAction() {
        getFindUsagesOptions().setRoot((ThrowSearchUtil.Root) myCbExns.getSelectedItem());
        super.doOKAction();
    }

    @Override
    protected void update() {
        if (!myHasFindWhatPanel) {
            setOKActionEnabled(true);
        }
        else {
            getFindUsagesOptions().setRoot((ThrowSearchUtil.Root) myCbExns.getSelectedItem());
            final boolean hasSelected = isSelected(myCbUsages);
            setOKActionEnabled(hasSelected);
        }
    }
}