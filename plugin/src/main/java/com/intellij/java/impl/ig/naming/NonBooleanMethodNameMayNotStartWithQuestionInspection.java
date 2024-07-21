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
package com.intellij.java.impl.ig.naming;

import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.impl.ig.psiutils.LibraryUtil;
import com.intellij.java.impl.ig.ui.UiUtils;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.CheckBox;
import consulo.ide.impl.idea.codeInspection.ui.ListTable;
import consulo.ide.impl.idea.codeInspection.ui.ListWrappingTableModel;
import consulo.java.language.module.util.JavaClassNames;
import consulo.ui.ex.awt.FormBuilder;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class NonBooleanMethodNameMayNotStartWithQuestionInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  @NonNls public String questionString = "is,can,has,should,could,will,shall,check,contains,equals,startsWith,endsWith";

  @SuppressWarnings({"PublicField"})
  public boolean ignoreBooleanMethods = false;

  @SuppressWarnings({"PublicField"})
  public boolean onlyWarnOnBaseMethods = true;

  List<String> questionList = new ArrayList(32);

  public NonBooleanMethodNameMayNotStartWithQuestionInspection() {
    parseString(questionString, questionList);
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.nonBooleanMethodNameMustNotStartWithQuestionDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.nonBooleanMethodNameMustNotStartWithQuestionProblemDescriptor().get();
  }

  @Override
  public void readSettings(@Nonnull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(questionString, questionList);
  }

  @Override
  public void writeSettings(@Nonnull Element element) throws WriteExternalException {
    questionString = formatString(questionList);
    super.writeSettings(element);
  }

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final ListTable table = new ListTable(new ListWrappingTableModel(
      questionList,
      InspectionGadgetsLocalize.booleanMethodNameMustStartWithQuestionTableColumnName().get()
    ));
    final JPanel tablePanel = UiUtils.createAddRemovePanel(table);

    final CheckBox checkBox1 =
      new CheckBox(InspectionGadgetsLocalize.ignoreMethodsWithBooleanReturnTypeOption().get(), this, "ignoreBooleanMethods");
    final CheckBox checkBox2 =
      new CheckBox(InspectionGadgetsLocalize.ignoreMethodsOverridingSuperMethod().get(), this, "onlyWarnOnBaseMethods");

    panel.add(tablePanel, BorderLayout.CENTER);
    panel.add(FormBuilder.createFormBuilder().addComponent(checkBox1).addComponent(checkBox2).getPanel(), BorderLayout.SOUTH);
    return panel;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonBooleanMethodNameMayNotStartWithQuestionVisitor();
  }

  private class NonBooleanMethodNameMayNotStartWithQuestionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      super.visitMethod(method);
      final PsiType returnType = method.getReturnType();
      if (returnType == null || returnType.equals(PsiType.BOOLEAN)) {
        return;
      }
      if (ignoreBooleanMethods && returnType.equalsToText(JavaClassNames.JAVA_LANG_BOOLEAN)) {
        return;
      }
      final String name = method.getName();
      boolean startsWithQuestionWord = false;
      for (String question : questionList) {
        if (name.startsWith(question)) {
          if (name.length() == question.length()) {
            startsWithQuestionWord = true;
            break;
          }
          final char nextChar = name.charAt(question.length());
          if (Character.isUpperCase(nextChar) || nextChar == '_') {
            startsWithQuestionWord = true;
            break;
          }
        }
      }
      if (!startsWithQuestionWord) {
        return;
      }
      if (onlyWarnOnBaseMethods) {
        if (MethodUtils.hasSuper(method)) {
          return;
        }
      }
      else if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      registerMethodError(method);
    }
  }
}
