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
import com.intellij.java.language.psi.util.PropertyUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.ui.ex.awt.table.ListTable;
import consulo.ui.ex.awt.table.ListWrappingTableModel;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import org.jdom.Element;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class PublicMethodWithoutLoggingInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public String loggerClassName = "java.util.logging.Logger" + ',' +
                                  "org.slf4j.Logger" + ',' +
                                  "org.apache.commons.logging.Log" + ',' +
                                  "org.apache.log4j.Logger";

  private final List<String> loggerClassNames = new ArrayList();

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.publicMethodWithoutLoggingDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.publicMethodWithoutLoggingProblemDescriptor().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListTable table = new ListTable(
      new ListWrappingTableModel(loggerClassNames, InspectionGadgetsLocalize.loggerClassName().get()));
    return UiUtils.createAddRemoveTreeClassChooserPanel(table, InspectionGadgetsLocalize.chooseLoggerClass().get());
  }

  @Override
  public void readSettings(@Nonnull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(loggerClassName, loggerClassNames);
  }

  @Override
  public void writeSettings(@Nonnull Element element) throws WriteExternalException {
    loggerClassName = formatString(loggerClassNames);
    super.writeSettings(element);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PublicMethodWithoutLoggingVisitor();
  }

  private class PublicMethodWithoutLoggingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      //no drilldown
      if (method.getNameIdentifier() == null) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      if (method.isConstructor()) {
        return;
      }
      if (PropertyUtil.isSimpleGetter(method) || PropertyUtil.isSimpleSetter(method)) {
        return;
      }
      if (containsLoggingCall(body)) {
        return;
      }
      registerMethodError(method);
    }

    private boolean containsLoggingCall(PsiCodeBlock block) {
      final ContainsLoggingCallVisitor visitor = new ContainsLoggingCallVisitor();
      block.accept(visitor);
      return visitor.containsLoggingCall();
    }
  }

  private class ContainsLoggingCallVisitor extends JavaRecursiveElementVisitor {

    private boolean containsLoggingCall = false;

    @Override
    public void visitElement(@Nonnull PsiElement element) {
      if (containsLoggingCall) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
      if (containsLoggingCall) {
        return;
      }
      super.visitMethodCallExpression(expression);
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String containingClassName = containingClass.getQualifiedName();
      if (containingClassName == null) {
        return;
      }
      if (loggerClassNames.contains(containingClassName)) {
        containsLoggingCall = true;
      }
    }

    public boolean containsLoggingCall() {
      return containsLoggingCall;
    }
  }
}
