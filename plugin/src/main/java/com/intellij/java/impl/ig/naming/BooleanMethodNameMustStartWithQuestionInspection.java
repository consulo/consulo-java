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
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.codeInspection.ui.ListTable;
import consulo.ide.impl.idea.codeInspection.ui.ListWrappingTableModel;
import consulo.java.language.module.util.JavaClassNames;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class BooleanMethodNameMustStartWithQuestionInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreBooleanMethods = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreInAnnotationInterface = true;

  @SuppressWarnings({"PublicField"})
  public boolean onlyWarnOnBaseMethods = true;

  /**
   * @noinspection PublicField
   */
  @NonNls
  public String questionString = "is,can,has,should,could,will,shall,check,contains,equals,add,put,remove,startsWith,endsWith";

  List<String> questionList = new ArrayList(32);

  public BooleanMethodNameMustStartWithQuestionInspection() {
    parseString(questionString, questionList);
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("boolean.method.name.must.start.with.question.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("boolean.method.name.must.start.with.question.problem.descriptor");
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
    final JPanel panel = new JPanel(new GridBagLayout());
    final ListTable table = new ListTable(new ListWrappingTableModel(questionList, InspectionGadgetsBundle
        .message("boolean.method.name.must.start.with.question.table.column.name")));
    final JPanel tablePanel = UiUtils.createAddRemovePanel(table);

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    constraints.fill = GridBagConstraints.BOTH;
    panel.add(tablePanel, constraints);

    final consulo.language.editor.inspection.ui.CheckBox checkBox1 =
        new consulo.language.editor.inspection.ui.CheckBox(InspectionGadgetsBundle.message("ignore.methods.with.boolean.return.type.option"), this, "ignoreBooleanMethods");
    constraints.gridy = 1;
    constraints.weighty = 0.0;
    panel.add(checkBox1, constraints);

    final consulo.language.editor.inspection.ui.CheckBox checkBox2 =
        new consulo.language.editor.inspection.ui.CheckBox(InspectionGadgetsBundle.message("ignore.boolean.methods.in.an.interface.option"), this, "ignoreInAnnotationInterface");
    constraints.gridy = 2;
    panel.add(checkBox2, constraints);

    final consulo.language.editor.inspection.ui.CheckBox checkBox3 =
        new consulo.language.editor.inspection.ui.CheckBox(InspectionGadgetsBundle.message("ignore.methods.overriding.super.method"), this, "onlyWarnOnBaseMethods");
    constraints.gridy = 3;
    panel.add(checkBox3, constraints);
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
    return new BooleanMethodNameMustStartWithQuestionVisitor();
  }

  private class BooleanMethodNameMustStartWithQuestionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      final PsiType returnType = method.getReturnType();
      if (returnType == null) {
        return;
      } else if (!returnType.equals(PsiType.BOOLEAN)) {
        if (ignoreBooleanMethods || !returnType.equalsToText(JavaClassNames.JAVA_LANG_BOOLEAN)) {
          return;
        }
      }
      if (ignoreInAnnotationInterface) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && containingClass.isAnnotationType()) {
          return;
        }
      }
      final String name = method.getName();
      for (String question : questionList) {
        if (name.startsWith(question)) {
          return;
        }
      }
      if (onlyWarnOnBaseMethods) {
        if (MethodUtils.hasSuper(method)) {
          return;
        }
      } else if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      registerMethodError(method);
    }
  }
}
