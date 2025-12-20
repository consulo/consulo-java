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

/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.java.impl.refactoring.inlineSuperClass;

import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.editor.refactoring.inline.InlineOptionsDialog;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.ui.util.DocCommentPanel;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;

public class InlineSuperClassRefactoringDialog extends InlineOptionsDialog {
  private final PsiClass mySuperClass;
  private final PsiClass myCurrentInheritor;
  private final PsiClass[] myTargetClasses;
  private final DocCommentPanel myDocPanel;

  protected InlineSuperClassRefactoringDialog(@Nonnull Project project, PsiClass superClass, PsiClass currentInheritor, PsiClass... targetClasses) {
    super(project, false, superClass);
    mySuperClass = superClass;
    myCurrentInheritor = currentInheritor;
    myInvokedOnReference = currentInheritor != null;
    myTargetClasses = targetClasses;
    myDocPanel = new DocCommentPanel(LocalizeValue.localizeTODO("JavaDoc for inlined members"));
    myDocPanel.setPolicy(JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC);
    init();
    setTitle(InlineSuperClassRefactoringHandler.REFACTORING_NAME);
  }

  @Override
  protected void doAction() {
    JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
    if (myRbInlineThisOnly.isEnabled() && myRbInlineAll.isEnabled()) {
      settings.INLINE_SUPER_CLASS_THIS = isInlineThisOnly();
    }
    invokeRefactoring(new InlineSuperClassRefactoringProcessor(getProject(), isInlineThisOnly() ? myCurrentInheritor : null, mySuperClass, myDocPanel.getPolicy(), myTargetClasses));
  }

  @Override
  protected JComponent createNorthPanel() {
    return null;
  }

  @Override
  protected JComponent createCenterPanel() {
    JLabel label = new JLabel("<html>Super class \'" +
        mySuperClass.getQualifiedName() +
        "\' inheritors: " +
        (myTargetClasses.length > 1 ? " <br>&nbsp;&nbsp;&nbsp;\'" : "\'") +
        StringUtil.join(myTargetClasses, PsiClass::getQualifiedName, "\',<br>&nbsp;&nbsp;&nbsp;\'") +
        "\'</html>");
    label.setBorder(IdeBorderFactory.createEmptyBorder(5, 5, 5, 5));
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gc =
        new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
            JBUI.emptyInsets(), 0, 0);
    panel.add(TargetAWT.to(myDocPanel.getComponent()), gc);
    panel.add(label, gc);
    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;
    panel.add(super.createCenterPanel(), gc);
    return panel;
  }

  @Nonnull
  @Override
  protected LocalizeValue getNameLabelText() {
    return LocalizeValue.join(LocalizeValue.localizeTODO("Class "), LocalizeValue.of(mySuperClass.getQualifiedName()));
  }

  @Nonnull
  @Override
  protected LocalizeValue getBorderTitle() {
      return RefactoringLocalize.inlineMethodBorderTitle();
  }

  @Nonnull
  @Override
  protected LocalizeValue getInlineAllText() {
    return RefactoringLocalize.allReferencesAndRemoveSuperClass();
  }

  @Nonnull
  @Override
  protected LocalizeValue getInlineThisText() {
    return RefactoringLocalize.thisReferenceOnlyAndKeepSuperClass();
  }

  @Override
  protected boolean isInlineThis() {
    return JavaRefactoringSettings.getInstance().INLINE_SUPER_CLASS_THIS;
  }
}