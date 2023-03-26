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
package com.intellij.java.impl.ig.logging;

import com.intellij.java.impl.ig.ui.UiUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.codeInspection.ui.ListTable;
import consulo.ide.impl.idea.codeInspection.ui.ListWrappingTableModel;
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
public class ClassWithoutLoggerInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  @NonNls
  public String loggerNamesString = "java.util.logging.Logger" + ',' +
                                    "org.slf4j.Logger" + ',' +
                                    "org.apache.commons.logging.Log" + ',' +
                                    "org.apache.log4j.Logger";
  private final List<String> loggerNames = new ArrayList();

  /**
   * @noinspection PublicField
   */
  public boolean ignoreSuperLoggers = false;

  public ClassWithoutLoggerInspection() {
    parseString(loggerNamesString, loggerNames);
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("no.logger.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("no.logger.problem.descriptor");
  }

  @Override
  public void readSettings(@Nonnull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(loggerNamesString, loggerNames);
  }

  @Override
  public void writeSettings(@Nonnull Element element) throws WriteExternalException {
    loggerNamesString = formatString(loggerNames);
    super.writeSettings(element);
  }

  @Override
  public JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new BorderLayout());
    final ListTable table = new ListTable(new ListWrappingTableModel(loggerNames, InspectionGadgetsBundle.message("logger.class.name")));
    final JPanel tablePanel = UiUtils.createAddRemoveTreeClassChooserPanel(table, InspectionGadgetsBundle.message("choose.logger.class"));
    final consulo.deadCodeNotWorking.impl.CheckBox checkBox = new consulo.deadCodeNotWorking.impl.CheckBox(InspectionGadgetsBundle.message("super.class.logger.option"), this, "ignoreSuperLoggers");
    panel.add(tablePanel, BorderLayout.CENTER);
    panel.add(checkBox, BorderLayout.SOUTH);
    return panel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassWithoutLoggerVisitor();
  }

  private class ClassWithoutLoggerVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
      //no recursion to avoid drilldown
      if (aClass.isInterface() || aClass.isEnum() ||
          aClass.isAnnotationType()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter ||
          aClass instanceof PsiAnonymousClass) {
        return;
      }
      if (aClass.getContainingClass() != null) {
        return;
      }
      final PsiField[] fields;
      if (ignoreSuperLoggers) {
        fields = aClass.getAllFields();
      }
      else {
        fields = aClass.getFields();
      }
      for (PsiField field : fields) {
        if (isLogger(field)) {
          if (PsiUtil.isAccessible(field, aClass, aClass)) {
            return;
          }
        }
      }
      registerClassError(aClass);
    }

    private boolean isLogger(PsiVariable variable) {
      final PsiType type = variable.getType();
      final String text = type.getCanonicalText();
      return loggerNames.contains(text);
    }
  }
}