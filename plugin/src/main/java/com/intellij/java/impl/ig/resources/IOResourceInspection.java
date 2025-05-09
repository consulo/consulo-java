/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.resources;

import com.intellij.java.impl.ig.ui.UiUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.CheckBox;
import consulo.ide.impl.idea.codeInspection.ui.ListTable;
import consulo.ide.impl.idea.codeInspection.ui.ListWrappingTableModel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
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
public class IOResourceInspection extends ResourceInspection {

  private static final String[] IO_TYPES = {
    "java.io.InputStream", "java.io.OutputStream",
    "java.io.Reader", "java.io.Writer",
    "java.io.RandomAccessFile", "java.util.zip.ZipFile"};

  @NonNls
  @SuppressWarnings({"PublicField"})
  public String ignoredTypesString = "java.io.ByteArrayOutputStream" +
                                     ',' + "java.io.ByteArrayInputStream" +
                                     ',' + "java.io.StringBufferInputStream" +
                                     ',' + "java.io.CharArrayWriter" +
                                     ',' + "java.io.CharArrayReader" +
                                     ',' + "java.io.StringWriter" +
                                     ',' + "java.io.StringReader";
  final List<String> ignoredTypes = new ArrayList();

  @SuppressWarnings({"PublicField"})
  public boolean insideTryAllowed = false;

  public IOResourceInspection() {
    parseString(ignoredTypesString, ignoredTypes);
  }

  @Override
  @Nonnull
  public String getID() {
    return "IOResourceOpenedButNotSafelyClosed";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.iOResourceOpenedNotClosedDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final PsiType type = expression.getType();
    assert type != null;
    final String text = type.getPresentableText();
    return InspectionGadgetsLocalize.resourceOpenedNotClosedProblemDescriptor(text).get();
  }

  @Override
  public JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new BorderLayout());
    final ListTable table =
      new ListTable(new ListWrappingTableModel(ignoredTypes, InspectionGadgetsLocalize.ignoredIoResourceTypes().get()));
    JPanel tablePanel =
      UiUtils.createAddRemoveTreeClassChooserPanel(table, InspectionGadgetsLocalize.chooseIoResourceTypeToIgnore().get(), IO_TYPES);
    final CheckBox checkBox =
      new CheckBox(InspectionGadgetsLocalize.allowResourceToBeOpenedInsideATryBlock().get(), this, "insideTryAllowed");
    panel.add(tablePanel, BorderLayout.CENTER);
    panel.add(checkBox, BorderLayout.SOUTH);
    return panel;
  }

  @Override
  public void readSettings(@Nonnull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(ignoredTypesString, ignoredTypes);
  }

  @Override
  public void writeSettings(@Nonnull Element element) throws WriteExternalException {
    ignoredTypesString = formatString(ignoredTypes);
    super.writeSettings(element);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IOResourceVisitor();
  }

  private class IOResourceVisitor extends BaseInspectionVisitor {

    IOResourceVisitor() {
    }

    @Override
    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
      if (!isIOResourceFactoryMethodCall(expression)) {
        return;
      }
      checkExpression(expression);
    }

    @Override
    public void visitNewExpression(@Nonnull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!isIOResource(expression)) {
        return;
      }
      checkExpression(expression);
    }

    private void checkExpression(PsiExpression expression) {
      final PsiElement parent = getExpressionParent(expression);
      if (parent instanceof PsiReturnStatement || parent instanceof PsiResourceVariable) {
        return;
      }
      if (parent instanceof PsiExpressionList) {
        PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiAnonymousClass) {
          grandParent = grandParent.getParent();
        }
        if (grandParent instanceof PsiNewExpression && isIOResource((PsiNewExpression)grandParent)) {
          return;
        }
      }
      final PsiVariable boundVariable = getVariable(parent);
      final PsiElement containingBlock = PsiTreeUtil.getParentOfType(expression, PsiCodeBlock.class);
      if (containingBlock == null) {
        return;
      }
      if (isArgumentOfResourceCreation(boundVariable, containingBlock)) {
        return;
      }
      if (isSafelyClosed(boundVariable, expression, insideTryAllowed)) {
        return;
      }
      if (isResourceEscapedFromMethod(boundVariable, expression)) {
        return;
      }
      registerError(expression, expression);
    }
  }

  public static boolean isIOResourceFactoryMethodCall(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!"getResourceAsStream".equals(methodName)) {
      return false;
    }
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier == null) {
      return false;
    }
    return TypeUtils.expressionHasTypeOrSubtype(qualifier, CommonClassNames.JAVA_LANG_CLASS, "java.lang.ClassLoader") != null;
  }

  public boolean isIOResource(PsiExpression expression) {
    return TypeUtils.expressionHasTypeOrSubtype(expression, IO_TYPES) != null && !isIgnoredType(expression);
  }

  private boolean isIgnoredType(PsiExpression expression) {
    return TypeUtils.expressionHasTypeOrSubtype(expression, ignoredTypes);
  }

  private boolean isArgumentOfResourceCreation(
    PsiVariable boundVariable, PsiElement scope) {
    final UsedAsIOResourceArgumentVisitor visitor =
      new UsedAsIOResourceArgumentVisitor(boundVariable);
    scope.accept(visitor);
    return visitor.isUsedAsArgumentToResourceCreation();
  }

  private class UsedAsIOResourceArgumentVisitor
    extends JavaRecursiveElementVisitor {

    private boolean usedAsArgToResourceCreation = false;
    private final PsiVariable ioResource;

    UsedAsIOResourceArgumentVisitor(PsiVariable ioResource) {
      this.ioResource = ioResource;
    }

    @Override
    public void visitNewExpression(
      @Nonnull PsiNewExpression expression) {
      if (usedAsArgToResourceCreation) {
        return;
      }
      super.visitNewExpression(expression);
      if (!isIOResource(expression)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression argument = arguments[0];
      if (!(argument instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReference reference = (PsiReference)argument;
      final PsiElement target = reference.resolve();
      if (target == null || !target.equals(ioResource)) {
        return;
      }
      usedAsArgToResourceCreation = true;
    }

    public boolean isUsedAsArgumentToResourceCreation() {
      return usedAsArgToResourceCreation;
    }
  }
}
