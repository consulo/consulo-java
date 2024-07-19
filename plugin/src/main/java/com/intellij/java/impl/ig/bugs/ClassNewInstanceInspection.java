/*
 * Copyright 2009-2010 Bas Leijdekkers
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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class ClassNewInstanceInspection extends BaseInspection {

  @Override
  @Nls
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.classNewInstanceDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.classNewInstanceProblemDescriptor().get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ClassNewInstanceFix();
  }

  private static class ClassNewInstanceFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.classNewInstanceQuickfix().get();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = (PsiReferenceExpression)parent;
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiElement parentOfType = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiTryStatement.class);
      if (parentOfType instanceof PsiTryStatement) {
        final PsiTryStatement tryStatement =
          (PsiTryStatement)parentOfType;
        addCatchBlock(
            tryStatement,
            "java.lang.NoSuchMethodException",
            "java.lang.reflect.InvocationTargetException"
        );
      }
      else {
        final PsiMethod method = (PsiMethod)parentOfType;
        addThrowsClause(
            method,
            "java.lang.NoSuchMethodException",
            "java.lang.reflect.InvocationTargetException"
        );
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      @NonNls final String newExpression = qualifier.getText() + ".getConstructor().newInstance()";
      replaceExpression(methodCallExpression, newExpression);
    }

    private static void addThrowsClause(PsiMethod method, String... exceptionNames) {
      final PsiReferenceList throwsList = method.getThrowsList();
      final PsiClassType[] referencedTypes = throwsList.getReferencedTypes();
      final Set<String> presentExceptionNames = new HashSet();
      for (PsiClassType referencedType : referencedTypes) {
        final String exceptionName = referencedType.getCanonicalText();
        presentExceptionNames.add(exceptionName);
      }
      final Project project = method.getProject();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      final GlobalSearchScope scope = method.getResolveScope();
      for (String exceptionName : exceptionNames) {
        if (presentExceptionNames.contains(exceptionName)) {
          continue;
        }
        final PsiJavaCodeReferenceElement throwsReference = factory.createReferenceElementByFQClassName(exceptionName, scope);
        final PsiElement element = throwsList.add(throwsReference);
        codeStyleManager.shortenClassReferences(element);
      }
    }

    protected static void addCatchBlock(PsiTryStatement tryStatement, String... exceptionNames)
      throws IncorrectOperationException {
      final Project project = tryStatement.getProject();
      final PsiParameter[] parameters = tryStatement.getCatchBlockParameters();
      final Set<String> presentExceptionNames = new HashSet();
      for (PsiParameter parameter : parameters) {
        final PsiType type = parameter.getType();
        final String exceptionName = type.getCanonicalText();
        presentExceptionNames.add(exceptionName);
      }
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      final String name = codeStyleManager.suggestUniqueVariableName("e", tryStatement.getTryBlock(), false);
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      for (String exceptionName : exceptionNames) {
        if (presentExceptionNames.contains(exceptionName)) {
          continue;
        }
        final PsiClassType type = (PsiClassType)factory.createTypeFromText(exceptionName, tryStatement);
        final PsiCatchSection section = factory.createCatchSection(type, name, tryStatement);
        final PsiCatchSection element = (PsiCatchSection)tryStatement.add(section);
        codeStyleManager.shortenClassReferences(element);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassNewInstanceVisitor();
  }

  private static class ClassNewInstanceVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"newInstance".equals(methodName)) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiType qualifierType = qualifier.getType();
      if (!(qualifierType instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)qualifierType;
      final PsiClass aClass = classType.resolve();
      if (aClass == null) {
        return;
      }
      final String className = aClass.getQualifiedName();
      if (!JavaClassNames.JAVA_LANG_CLASS.equals(className)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
