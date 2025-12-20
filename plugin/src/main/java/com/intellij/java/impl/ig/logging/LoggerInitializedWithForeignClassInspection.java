/*
 * Copyright 2008-2012 Bas Leijdekkers
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
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.awt.table.ListTable;
import consulo.ui.ex.awt.table.ListWrappingTableModel;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@ExtensionImpl
public class LoggerInitializedWithForeignClassInspection extends BaseInspection {

  @NonNls private static final String DEFAULT_LOGGER_CLASS_NAMES =
    "org.apache.log4j.Logger,org.slf4j.LoggerFactory,org.apache.commons.logging.LogFactory,java.util.logging.Logger";
  @NonNls private static final String DEFAULT_FACTORY_METHOD_NAMES = "getLogger,getLogger,getLog,getLogger";

  @SuppressWarnings({"PublicField"})
  public String loggerClassName = DEFAULT_LOGGER_CLASS_NAMES;
  private final List<String> loggerFactoryClassNames = new ArrayList();

  @SuppressWarnings({"PublicField"})
  public String loggerFactoryMethodName = DEFAULT_FACTORY_METHOD_NAMES;
  private final List<String> loggerFactoryMethodNames = new ArrayList();

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.loggerInitializedWithForeignClassDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.loggerInitializedWithForeignClassProblemDescriptor().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    ListTable table = new ListTable(new ListWrappingTableModel(
      Arrays.asList(loggerFactoryClassNames, loggerFactoryMethodNames),
      InspectionGadgetsLocalize.loggerFactoryClassName().get(),
      InspectionGadgetsLocalize.loggerFactoryMethodName().get()
    ));
    return UiUtils.createAddRemovePanel(table);
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new LoggerInitializedWithForeignClassFix((String)infos[0]);
  }

  private static class LoggerInitializedWithForeignClassFix extends InspectionGadgetsFix {

    private final String newClassName;

    private LoggerInitializedWithForeignClassFix(String newClassName) {
      this.newClassName = newClassName;
    }

    @Override
    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.loggerInitializedWithForeignClassQuickfix(newClassName);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiClassObjectAccessExpression)) {
        return;
      }
      PsiClassObjectAccessExpression classObjectAccessExpression = (PsiClassObjectAccessExpression)element;
      replaceExpression(classObjectAccessExpression, newClassName + ".class");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LoggerInitializedWithForeignClassVisitor();
  }

  private class LoggerInitializedWithForeignClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
      super.visitClassObjectAccessExpression(expression);
      PsiElement parent = expression.getParent();
      if (parent instanceof PsiReferenceExpression) {
        PsiReferenceExpression referenceExpression = (PsiReferenceExpression)parent;
        if (!expression.equals(referenceExpression.getQualifierExpression())) {
          return;
        }
        String name = referenceExpression.getReferenceName();
        if (!"getName".equals(name)) {
          return;
        }
        PsiElement grandParent = referenceExpression.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression)) {
          return;
        }
        PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
        PsiExpressionList list = methodCallExpression.getArgumentList();
        if (list.getExpressions().length != 0) {
          return;
        }
        parent = methodCallExpression.getParent();
      }
      if (!(parent instanceof PsiExpressionList)) {
        return;
      }
      PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length != 1) {
        return;
      }
      PsiClass containingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
      if (containingClass == null) {
        return;
      }
      String containingClassName = containingClass.getName();
      if (containingClassName == null) {
        return;
      }
      PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return;
      }
      PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      String className = aClass.getQualifiedName();
      int index = loggerFactoryClassNames.indexOf(className);
      if (index < 0) {
        return;
      }
      PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      String referenceName = methodExpression.getReferenceName();
      String loggerFactoryMethodName = loggerFactoryMethodNames.get(index);
      if (!loggerFactoryMethodName.equals(referenceName)) {
        return;
      }
      PsiTypeElement operand = expression.getOperand();
      PsiType type = operand.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      PsiClassType classType = (PsiClassType)type;
      PsiClass initializerClass = classType.resolve();
      if (initializerClass == null) {
        return;
      }
      if (containingClass.equals(initializerClass)) {
        return;
      }
      registerError(expression, containingClassName);
    }
  }

  @Override
  public void readSettings(@Nonnull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(loggerClassName, loggerFactoryClassNames);
    parseString(loggerFactoryMethodName, loggerFactoryMethodNames);
    if (loggerFactoryClassNames.size() != loggerFactoryMethodNames.size()) {
      parseString(DEFAULT_LOGGER_CLASS_NAMES, loggerFactoryClassNames);
      parseString(DEFAULT_FACTORY_METHOD_NAMES, loggerFactoryMethodNames);
    }
  }

  @Override
  public void writeSettings(@Nonnull Element element) throws WriteExternalException {
    loggerClassName = formatString(loggerFactoryClassNames);
    loggerFactoryMethodName = formatString(loggerFactoryMethodNames);
    super.writeSettings(element);
  }}
