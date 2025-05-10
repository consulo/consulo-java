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

import com.intellij.java.impl.ig.psiutils.LibraryUtil;
import com.intellij.java.impl.ig.ui.UiUtils;
import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.CheckBox;
import consulo.ide.impl.idea.codeInspection.ui.ListTable;
import consulo.ide.impl.idea.codeInspection.ui.ListWrappingTableModel;
import consulo.language.psi.PsiElement;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

@ExtensionImpl
public class BadExceptionDeclaredInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public String exceptionsString = "";

  /**
   * @noinspection PublicField
   */
  public final ExternalizableStringSet exceptions = new ExternalizableStringSet(
    CommonClassNames.JAVA_LANG_THROWABLE,
    CommonClassNames.JAVA_LANG_EXCEPTION,
    CommonClassNames.JAVA_LANG_ERROR,
    CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION,
    CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION,
    CommonClassNames.JAVA_LANG_CLASS_CAST_EXCEPTION,
    CommonClassNames.JAVA_LANG_ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION
  );

  /**
   * @noinspection PublicField
   */
  public boolean ignoreTestCases = false;

  public boolean ignoreLibraryOverrides = false;

  public BadExceptionDeclaredInspection() {
    if (exceptionsString.length() != 0) {
      exceptions.clear();
      final List<String> strings = StringUtil.split(exceptionsString, ",");
      for (String string : strings) {
        exceptions.add(string);
      }
      exceptionsString = "";
    }
  }

  @Override
  @Nonnull
  public String getID() {
    return "ProhibitedExceptionDeclared";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.badExceptionDeclaredDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.badExceptionDeclaredProblemDescriptor().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new GridBagLayout());
    final ListTable table =
      new ListTable(new ListWrappingTableModel(exceptions, InspectionGadgetsLocalize.exceptionClassColumnName().get()));
    JPanel tablePanel = UiUtils.createAddRemoveTreeClassChooserPanel(
      table,
      InspectionGadgetsLocalize.chooseExceptionClass().get(),
        CommonClassNames.JAVA_LANG_THROWABLE
    );
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    constraints.fill = GridBagConstraints.BOTH;
    panel.add(tablePanel, constraints);

    final CheckBox checkBox1 =
      new CheckBox(InspectionGadgetsBundle.message("ignore.exceptions.declared.in.tests.option"), this,
                   "ignoreTestCases");
    constraints.gridy = 1;
    constraints.weighty = 0.0;
    panel.add(checkBox1, constraints);

    final CheckBox checkBox2 = new CheckBox(
      InspectionGadgetsLocalize.ignoreExceptionsDeclaredOnLibraryOverrideOption().get(),
      this,
      "ignoreLibraryOverrides"
    );
    constraints.gridy = 2;
    panel.add(checkBox2, constraints);
    return panel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BadExceptionDeclaredVisitor();
  }

  private class BadExceptionDeclaredVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      super.visitMethod(method);
      if (ignoreTestCases) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && TestFrameworks.getInstance().isTestClass(containingClass)) {
          return;
        }
        if (TestUtils.isJUnitTestMethod(method)) {
          return;
        }
      }
      if (ignoreLibraryOverrides && LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      final PsiReferenceList throwsList = method.getThrowsList();
      final PsiJavaCodeReferenceElement[] references = throwsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement reference : references) {
        final PsiElement element = reference.resolve();
        if (!(element instanceof PsiClass)) {
          continue;
        }
        final PsiClass thrownClass = (PsiClass)element;
        final String qualifiedName = thrownClass.getQualifiedName();
        if (qualifiedName != null && exceptions.contains(qualifiedName)) {
          registerError(reference);
        }
      }
    }
  }
}