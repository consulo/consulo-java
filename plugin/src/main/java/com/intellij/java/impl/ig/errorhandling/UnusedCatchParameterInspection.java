/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.language.psi.PsiCatchSection;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiTryStatement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class UnusedCatchParameterInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreCatchBlocksWithComments = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreTestCases = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unused.catch.parameter.display.name");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel =
      new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "unused.catch.parameter.ignore.catch.option"),
                             "m_ignoreCatchBlocksWithComments");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "unused.catch.parameter.ignore.empty.option"),
                             "m_ignoreTestCases");
    return optionsPanel;
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    final boolean namedIgnoreButUsed = ((Boolean)infos[0]).booleanValue();
    if (namedIgnoreButUsed) {
      return InspectionGadgetsBundle.message(
        "used.catch.parameter.named.ignore.problem.descriptor"
      );
    }
    return InspectionGadgetsBundle.message(
      "unused.catch.parameter.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final boolean namedIgnoreButUsed = ((Boolean)infos[0]).booleanValue();
    if (namedIgnoreButUsed) {
      return null;
    }
    return new RenameFix("ignored", false, false);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnusedCatchParameterVisitor();
  }

  private class UnusedCatchParameterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTryStatement(@Nonnull PsiTryStatement statement) {
      super.visitTryStatement(statement);
      if (m_ignoreTestCases && TestUtils.isInTestCode(statement)) {
        return;
      }
      final PsiCatchSection[] catchSections = statement.getCatchSections();
      for (PsiCatchSection catchSection : catchSections) {
        checkCatchSection(catchSection);
      }
    }

    private void checkCatchSection(PsiCatchSection section) {
      final PsiParameter parameter = section.getParameter();
      if (parameter == null) {
        return;
      }
      @NonNls final String parameterName = parameter.getName();
      final boolean namedIgnore = parameterName.contains("ignore");
      final PsiCodeBlock block = section.getCatchBlock();
      if (block == null) {
        return;
      }
      if (m_ignoreCatchBlocksWithComments) {
        final PsiElement[] children = block.getChildren();
        for (final PsiElement child : children) {
          if (child instanceof PsiComment) {
            return;
          }
        }
      }
      final CatchParameterUsedVisitor visitor =
        new CatchParameterUsedVisitor(parameter);
      block.accept(visitor);
      if (visitor.isUsed()) {
        if (namedIgnore) {
          registerVariableError(parameter, Boolean.valueOf(true));
        }
        return;
      }
      else if (namedIgnore) {
        return;
      }
      registerVariableError(parameter, Boolean.valueOf(false));
    }
  }
}