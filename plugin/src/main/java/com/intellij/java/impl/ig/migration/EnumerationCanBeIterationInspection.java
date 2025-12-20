/*
 * Copyright 2007-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.migration;

import com.intellij.java.impl.ig.psiutils.PsiElementOrderComparator;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ExtensionImpl
public class EnumerationCanBeIterationInspection extends BaseInspection {

  @NonNls
  static final String ITERATOR_TEXT = "iterator()";

  @NonNls
  static final String KEY_SET_ITERATOR_TEXT = "keySet().iterator()";

  @NonNls
  static final String VALUES_ITERATOR_TEXT = "values().iterator()";

  private static final int KEEP_NOTHING = 0;

  private static final int KEEP_INITIALIZATION = 1;

  private static final int KEEP_DECLARATION = 2;

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.enumerationCanBeIterationDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.enumerationCanBeIterationProblemDescriptor(infos[0]).get();
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new EnumerationCanBeIterationFix();
  }

  private static class EnumerationCanBeIterationFix
    extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.enumerationCanBeIterationQuickfix();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      PsiElement element = descriptor.getPsiElement();
      PsiReferenceExpression methodExpression =
        (PsiReferenceExpression)element.getParent();
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)methodExpression.getParent();
      PsiElement parent =
        methodCallExpression.getParent();
      PsiVariable variable;
      if (parent instanceof PsiVariable) {
        variable = (PsiVariable)parent;
      }
      else if (parent instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression assignmentExpression =
          (PsiAssignmentExpression)parent;
        PsiExpression lhs = assignmentExpression.getLExpression();
        if (!(lhs instanceof PsiReferenceExpression)) {
          return;
        }
        PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)lhs;
        PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        variable = (PsiVariable)target;
      }
      else {
        return;
      }
      String variableName = createVariableName(element);
      PsiStatement statement =
        PsiTreeUtil.getParentOfType(element, PsiStatement.class);
      if (statement == null) {
        return;
      }
      int result = replaceMethodCalls(variable,
                                            statement.getTextOffset(), variableName);
      PsiType variableType = variable.getType();
      if (!(variableType instanceof PsiClassType)) {
        return;
      }
      PsiClassType classType = (PsiClassType)variableType;
      PsiType[] parameterTypes = classType.getParameters();
      PsiType parameterType;
      if (parameterTypes.length > 0) {
        parameterType = parameterTypes[0];
      }
      else {
        parameterType = null;
      }
      PsiStatement newStatement =
        createDeclaration(methodCallExpression, variableName,
                          parameterType);
      if (newStatement == null) {
        return;
      }
      if (parent.equals(variable)) {
        if (result == KEEP_NOTHING) {
          statement.replace(newStatement);
        }
        else {
          insertNewStatement(statement, newStatement);
          if (result != KEEP_INITIALIZATION) {
            PsiExpression initializer =
              variable.getInitializer();
            if (initializer != null) {
              initializer.delete();
            }
          }
        }
      }
      else {
        if (result == KEEP_NOTHING || result == KEEP_DECLARATION) {
          statement.replace(newStatement);
        }
        else {
          insertNewStatement(statement, newStatement);
        }
      }
    }

    private static void insertNewStatement(PsiStatement anchor,
                                           PsiStatement newStatement)
      throws IncorrectOperationException {
      PsiElement statementParent = anchor.getParent();
      if (statementParent instanceof PsiForStatement) {
        PsiElement statementGrandParent =
          statementParent.getParent();
        statementGrandParent.addBefore(newStatement,
                                       statementParent);
      }
      else {
        statementParent.addAfter(newStatement, anchor);
      }
    }

    @Nullable
    private static PsiStatement createDeclaration(
      PsiMethodCallExpression methodCallExpression,
      String variableName, PsiType parameterType)
      throws IncorrectOperationException {
      @NonNls StringBuilder newStatementText = new StringBuilder();
      Project project = methodCallExpression.getProject();
      JavaCodeStyleSettings codeStyleSettings =
        CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
      if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
        newStatementText.append("final ");
      }
      newStatementText.append(CommonClassNames.JAVA_UTIL_ITERATOR);
      if (parameterType != null) {
        String typeText = parameterType.getCanonicalText();
        newStatementText.append('<');
        newStatementText.append(typeText);
        newStatementText.append('>');
      }
      newStatementText.append(' ');
      newStatementText.append(variableName);
      newStatementText.append('=');
      PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      String qualifierText;
      if (qualifier == null) {
        qualifierText = "";
      }
      else {
        qualifierText = qualifier.getText() + '.';
      }
      newStatementText.append(qualifierText);
      @NonNls String methodName =
        methodExpression.getReferenceName();
      if ("elements".equals(methodName)) {
        if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                 "java.util.Vector")) {
          newStatementText.append(ITERATOR_TEXT);
        }
        else if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                      "java.util.Hashtable")) {
          newStatementText.append(VALUES_ITERATOR_TEXT);
        }
        else {
          return null;
        }
      }
      else if ("keys".equals(methodName)) {
        if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                 "java.util.Hashtable")) {
          newStatementText.append(KEY_SET_ITERATOR_TEXT);
        }
        else {
          return null;
        }
      }
      else {
        return null;
      }
      newStatementText.append(';');
      PsiElementFactory factory =
        JavaPsiFacade.getElementFactory(project);
      PsiStatement statement =
        factory.createStatementFromText(newStatementText.toString(),
                                        methodExpression);
      JavaCodeStyleManager styleManager =
        JavaCodeStyleManager.getInstance(project);
      styleManager.shortenClassReferences(statement);
      return statement;
    }

    /**
     * @return true if the initialization of the Enumeration variable can
     *         be deleted.
     */
    private static int replaceMethodCalls(
      PsiVariable enumerationVariable,
      int startOffset,
      String newVariableName)
      throws IncorrectOperationException {
      PsiManager manager = enumerationVariable.getManager();
      Project project = manager.getProject();
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiElementFactory factory = facade.getElementFactory();
      Query<PsiReference> query = ReferencesSearch.search(
        enumerationVariable);
      List<PsiElement> referenceElements = new ArrayList();
      for (PsiReference reference : query) {
        PsiElement referenceElement = reference.getElement();
        referenceElements.add(referenceElement);
      }
      Collections.sort(referenceElements,
                       PsiElementOrderComparator.getInstance());
      int result = 0;
      for (PsiElement referenceElement : referenceElements) {
        if (!(referenceElement instanceof PsiReferenceExpression)) {
          result = KEEP_DECLARATION;
          continue;
        }
        if (referenceElement.getTextOffset() <= startOffset) {
          result = KEEP_DECLARATION;
          continue;
        }
        PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)referenceElement;
        PsiElement referenceParent =
          referenceExpression.getParent();
        if (!(referenceParent instanceof PsiReferenceExpression)) {
          if (referenceParent instanceof PsiAssignmentExpression) {
            result = KEEP_DECLARATION;
            break;
          }
          result = KEEP_INITIALIZATION;
          continue;
        }
        PsiElement referenceGrandParent =
          referenceParent.getParent();
        if (!(referenceGrandParent instanceof PsiMethodCallExpression)) {
          result = KEEP_INITIALIZATION;
          continue;
        }
        PsiMethodCallExpression callExpression =
          (PsiMethodCallExpression)referenceGrandParent;
        PsiReferenceExpression foundReferenceExpression =
          callExpression.getMethodExpression();
        @NonNls String foundName =
          foundReferenceExpression.getReferenceName();
        @NonNls String newExpressionText;
        if ("hasMoreElements".equals(foundName)) {
          newExpressionText = newVariableName + ".hasNext()";
        }
        else if ("nextElement".equals(foundName)) {
          newExpressionText = newVariableName + ".next()";
        }
        else {
          result = KEEP_INITIALIZATION;
          continue;
        }
        PsiExpression newExpression =
          factory.createExpressionFromText(newExpressionText,
                                           callExpression);
        callExpression.replace(newExpression);
      }
      return result;
    }

    @NonNls
    private static String createVariableName(PsiElement context) {
      Project project = context.getProject();
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiElementFactory factory = facade.getElementFactory();
      GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      PsiClass iteratorClass = facade.findClass(CommonClassNames.JAVA_UTIL_ITERATOR, scope);
      if (iteratorClass == null) {
        return "iterator";
      }
      JavaCodeStyleManager codeStyleManager =
        JavaCodeStyleManager.getInstance(project);
      PsiType iteratorType = factory.createType(iteratorClass);
      SuggestedNameInfo baseNameInfo =
        codeStyleManager.suggestVariableName(
          VariableKind.LOCAL_VARIABLE, null, null,
          iteratorType);
      SuggestedNameInfo nameInfo =
        codeStyleManager.suggestUniqueVariableName(baseNameInfo,
                                                   context, true);
      if (nameInfo.names.length <= 0) {
        return "iterator";
      }
      return nameInfo.names[0];
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EnumerationCanBeIterationVisitor();
  }

  private static class EnumerationCanBeIterationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls String methodName =
        methodExpression.getReferenceName();
      boolean isElements;
      if ("elements".equals(methodName)) {
        isElements = true;
      }
      else if ("keys".equals(methodName)) {
        isElements = false;
      }
      else {
        return;
      }
      if (!TypeUtils.expressionHasTypeOrSubtype(expression,
                                                "java.util.Enumeration")) {
        return;
      }
      PsiElement parent = expression.getParent();
      PsiVariable variable;
      if (parent instanceof PsiLocalVariable) {
        variable = (PsiVariable)parent;
      }
      else if (parent instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression assignmentExpression =
          (PsiAssignmentExpression)parent;
        PsiExpression lhs = assignmentExpression.getLExpression();
        if (!(lhs instanceof PsiReferenceExpression)) {
          return;
        }
        PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)lhs;
        PsiElement element = referenceExpression.resolve();
        if (!(element instanceof PsiVariable)) {
          return;
        }
        variable = (PsiVariable)element;
      }
      else {
        return;
      }
      PsiMethod containingMethod = PsiTreeUtil.getParentOfType(
        expression, PsiMethod.class);
      if (containingMethod == null) {
        return;
      }
      if (!isEnumerationMethodCalled(variable, containingMethod)) {
        return;
      }
      if (isElements) {
        PsiMethod method = expression.resolveMethod();
        if (method == null) {
          return;
        }
        PsiClass containingClass = method.getContainingClass();
        if (InheritanceUtil.isInheritor(containingClass,
                                        "java.util.Vector")) {
          registerMethodCallError(expression, ITERATOR_TEXT);
        }
        else if (InheritanceUtil.isInheritor(containingClass,
                                             "java.util.Hashtable")) {
          registerMethodCallError(expression, VALUES_ITERATOR_TEXT);
        }
      }
      else {
        PsiMethod method = expression.resolveMethod();
        if (method == null) {
          return;
        }
        PsiClass containingClass = method.getContainingClass();
        if (InheritanceUtil.isInheritor(containingClass,
                                        "java.util.Hashtable")) {
          registerMethodCallError(expression, KEY_SET_ITERATOR_TEXT);
        }
      }
    }

    private static boolean isEnumerationMethodCalled(
      @Nonnull PsiVariable variable, @Nonnull PsiElement context) {
      EnumerationMethodCalledVisitor visitor =
        new EnumerationMethodCalledVisitor(variable);
      context.accept(visitor);
      return visitor.isEnumerationMethodCalled();
    }

    private static class EnumerationMethodCalledVisitor
      extends JavaRecursiveElementVisitor {

      private final PsiVariable variable;
      private boolean enumerationMethodCalled = false;

      EnumerationMethodCalledVisitor(@Nonnull PsiVariable variable) {
        this.variable = variable;
      }

      @Override
      public void visitMethodCallExpression(
        PsiMethodCallExpression expression) {
        if (enumerationMethodCalled) {
          return;
        }
        super.visitMethodCallExpression(expression);
        PsiReferenceExpression methodExpression =
          expression.getMethodExpression();
        @NonNls String methodName =
          methodExpression.getReferenceName();
        if (!"hasMoreElements".equals(methodName) &&
            !"nextElement".equals(methodName)) {
          return;
        }
        PsiExpression qualifierExpression =
          methodExpression.getQualifierExpression();
        if (!(qualifierExpression instanceof PsiReferenceExpression)) {
          return;
        }
        PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)qualifierExpression;
        PsiElement element = referenceExpression.resolve();
        if (!(element instanceof PsiVariable)) {
          return;
        }
        PsiVariable variable = (PsiVariable)element;
        enumerationMethodCalled = this.variable.equals(variable);
      }

      public boolean isEnumerationMethodCalled() {
        return enumerationMethodCalled;
      }
    }
  }
}