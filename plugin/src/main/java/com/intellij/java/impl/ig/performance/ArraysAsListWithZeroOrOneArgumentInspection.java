/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

/**
 * @author Bas Leijdekkers
 */
@ExtensionImpl
public class ArraysAsListWithZeroOrOneArgumentInspection extends BaseInspection {

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsLocalize.arraysAsListWithZeroOrOneArgumentDisplayName().get();
  }

  @Nonnull
  @Override
  protected String buildErrorString(Object... infos) {
    final Boolean isEmpty = (Boolean)infos[0];
    return isEmpty
      ? InspectionGadgetsLocalize.arraysAsListWithZeroArgumentsProblemDescriptor().get()
      : InspectionGadgetsLocalize.arraysAsListWithOneArgumentProblemDescriptor().get();
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final Boolean isEmpty = (Boolean)infos[0];
    return new ArraysAsListWithOneArgumentFix(isEmpty);
  }

  private static class ArraysAsListWithOneArgumentFix extends InspectionGadgetsFix {

    private final boolean myEmpty;

    private ArraysAsListWithOneArgumentFix(boolean isEmpty) {
      myEmpty = isEmpty;
    }

    @Nonnull
    @Override
    public String getName() {
      return myEmpty
        ? InspectionGadgetsLocalize.arraysAsListWithZeroArgumentsQuickfix().get()
        : InspectionGadgetsLocalize.arraysAsListWithOneArgumentQuickfix().get();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement().getParent().getParent();
      if (!(element instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
      if (myEmpty) {
        replaceExpressionAndShorten(methodCallExpression, "java.util.Collections.emptyList()");
      }
      else {
        final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        replaceExpressionAndShorten(methodCallExpression, "java.util.Collections.singletonList" + argumentList.getText());
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArrayAsListWithOneArgumentVisitor();
  }

  private static class ArrayAsListWithOneArgumentVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!"asList".equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 1) {
        final PsiExpression argument = arguments[0];
        final PsiType type = argument.getType();
        if (type instanceof PsiArrayType) {
          return;
        }
      }
      else if (arguments.length != 0) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      if (!JavaClassNames.JAVA_UTIL_ARRAYS.equals(className)) {
        return;
      }
      registerMethodCallError(expression, Boolean.valueOf(arguments.length == 0));
    }
  }
}
