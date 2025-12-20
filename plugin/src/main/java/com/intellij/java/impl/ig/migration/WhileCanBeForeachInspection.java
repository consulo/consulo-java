/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.ig.psiutils.StringUtils;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class WhileCanBeForeachInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "WhileLoopReplaceableByForEach";
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.whileCanBeForeachDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.whileCanBeForeachProblemDescriptor().get();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new WhileCanBeForeachFix();
  }

  private static class WhileCanBeForeachFix extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.foreachReplaceQuickfix();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      PsiElement whileElement = descriptor.getPsiElement();
      PsiWhileStatement whileStatement = (PsiWhileStatement)whileElement.getParent();
      replaceWhileWithForEach(whileStatement);
    }

    private static void replaceWhileWithForEach(@Nonnull PsiWhileStatement whileStatement) {
      PsiStatement body = whileStatement.getBody();
      if (body == null) {
        return;
      }
      PsiStatement initialization = getPreviousStatement(whileStatement);
      PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
      if (declaration == null) {
        return;
      }
      PsiElement declaredElement = declaration.getDeclaredElements()[0];
      if (!(declaredElement instanceof PsiLocalVariable)) {
        return;
      }
      PsiLocalVariable iterator = (PsiLocalVariable)declaredElement;
      PsiMethodCallExpression initializer = (PsiMethodCallExpression)iterator.getInitializer();
      if (initializer == null) {
        return;
      }
      PsiReferenceExpression methodExpression = initializer.getMethodExpression();
      PsiExpression collection = methodExpression.getQualifierExpression();
      if (collection == null) {
        return;
      }
      PsiType contentType = getContentType(collection.getType(), collection);
      if (contentType == null) {
        return;
      }
      Project project = whileStatement.getProject();
      PsiStatement firstStatement = getFirstStatement(body);
      boolean isDeclaration = isIteratorNextDeclaration(firstStatement, iterator, contentType);
      PsiStatement statementToSkip;
      @NonNls String contentVariableName;
      if (isDeclaration) {
        PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
        if (declarationStatement == null) {
          return;
        }
        PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        PsiLocalVariable localVariable = (PsiLocalVariable)declaredElements[0];
        contentVariableName = localVariable.getName();
        statementToSkip = declarationStatement;
      }
      else {
        if (collection instanceof PsiReferenceExpression) {
          PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)collection;
          String collectionName = referenceElement.getReferenceName();
          contentVariableName = createNewVariableName(whileStatement, contentType, collectionName);
        }
        else {
          contentVariableName = createNewVariableName(whileStatement, contentType, null);
        }
        statementToSkip = null;
      }
      JavaCodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
      @NonNls StringBuilder out = new StringBuilder();
      out.append("for(");
      if (codeStyleSettings.GENERATE_FINAL_PARAMETERS) {
        out.append("final ");
      }
      PsiType iteratorContentType = getContentType(iterator.getType(), iterator);
      if (iteratorContentType == null) {
        return;
      }
      out.append(iteratorContentType.getCanonicalText()).append(' ').append(contentVariableName).append(": ");
      if (!TypeConversionUtil.isAssignable(iteratorContentType, contentType)) {
        out.append("(java.lang.Iterable<").append(iteratorContentType.getCanonicalText()).append(">)");
      }
      out.append(collection.getText()).append(')');

      replaceIteratorNext(body, contentVariableName, iterator, contentType, statementToSkip, out);
      Query<PsiReference> query = ReferencesSearch.search(iterator, iterator.getUseScope());
      boolean deleteIterator = true;
      for (PsiReference usage : query) {
        PsiElement element = usage.getElement();
        if (PsiTreeUtil.isAncestor(whileStatement, element, true)) {
          continue;
        }
        PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class);
        if (assignment == null) {
          // iterator is read after while loop,
          // so cannot be deleted
          deleteIterator = false;
          break;
        }
        PsiExpression expression = assignment.getRExpression();
        initializer.delete();
        iterator.setInitializer(expression);
        PsiElement statement = assignment.getParent();
        PsiElement lastChild = statement.getLastChild();
        if (lastChild instanceof PsiComment) {
          iterator.add(lastChild);
        }
        statement.replace(iterator);
        break;
      }
      if (deleteIterator) {
        iterator.delete();
      }
      String result = out.toString();
      replaceStatementAndShortenClassNames(whileStatement, result);
    }

    @Nullable
    private static PsiType getContentType(PsiType type, PsiElement context) {
      if (!(type instanceof PsiClassType)) {
        return null;
      }
      PsiClassType classType = (PsiClassType)type;
      PsiType[] parameters = classType.getParameters();
      if (parameters.length == 1) {
        PsiType parameterType = parameters[0];
        if (parameterType instanceof PsiCapturedWildcardType) {
          PsiCapturedWildcardType wildcardType = (PsiCapturedWildcardType)parameterType;
          PsiType bound = wildcardType.getUpperBound();
          if (bound != null) {
            return bound;
          }
        }
        else if (parameterType != null) {
          return parameterType;
        }
      }
      return TypeUtils.getObjectType(context);
    }

    private static void replaceIteratorNext(@Nonnull PsiElement element, String contentVariableName, PsiVariable iterator,
                                            PsiType contentType, PsiElement childToSkip, StringBuilder out) {
      if (isIteratorNext(element, iterator, contentType)) {
        out.append(contentVariableName);
      }
      else {
        PsiElement[] children = element.getChildren();
        if (children.length == 0) {
          out.append(element.getText());
        }
        else {
          boolean skippingWhiteSpace = false;
          for (PsiElement child : children) {
            if (shouldSkip(iterator, contentType, child)) {
              skippingWhiteSpace = true;
            }
            else if (child.equals(childToSkip)) {
              skippingWhiteSpace = true;
            }
            else if (!(child instanceof PsiWhiteSpace) || !skippingWhiteSpace) {
              skippingWhiteSpace = false;
              replaceIteratorNext(child, contentVariableName, iterator, contentType, childToSkip, out);
            }
          }
        }
      }
    }

    private static boolean shouldSkip(PsiVariable iterator, PsiType contentType, PsiElement child) {
      if (!(child instanceof PsiExpressionStatement)) {
        return false;
      }
      PsiExpressionStatement expressionStatement = (PsiExpressionStatement)child;
      PsiExpression expression = expressionStatement.getExpression();
      return isIteratorNext(expression, iterator, contentType);
    }

    private static boolean isIteratorNextDeclaration(PsiStatement statement, PsiVariable iterator, PsiType contentType) {
      if (!(statement instanceof PsiDeclarationStatement)) {
        return false;
      }
      PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
      PsiElement[] elements = declarationStatement.getDeclaredElements();
      if (elements.length != 1) {
        return false;
      }
      PsiElement element = elements[0];
      if (!(element instanceof PsiVariable)) {
        return false;
      }
      PsiVariable variable = (PsiVariable)element;
      PsiExpression initializer = variable.getInitializer();
      return isIteratorNext(initializer, iterator, contentType);
    }

    private static boolean isIteratorNext(PsiElement element, PsiVariable iterator, PsiType contentType) {
      if (element == null) {
        return false;
      }
      if (element instanceof PsiTypeCastExpression) {
        PsiTypeCastExpression castExpression = (PsiTypeCastExpression)element;
        PsiType type = castExpression.getType();
        if (type == null) {
          return false;
        }
        if (!type.equals(contentType)) {
          return false;
        }
        PsiExpression operand = castExpression.getOperand();
        return isIteratorNext(operand, iterator, contentType);
      }
      if (!(element instanceof PsiMethodCallExpression)) {
        return false;
      }
      PsiMethodCallExpression callExpression = (PsiMethodCallExpression)element;
      PsiExpressionList argumentList = callExpression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 0) {
        return false;
      }
      PsiReferenceExpression reference = callExpression.getMethodExpression();
      @NonNls String referenceName = reference.getReferenceName();
      if (!HardcodedMethodConstants.NEXT.equals(referenceName)) {
        return false;
      }
      PsiExpression expression = reference.getQualifierExpression();
      if (!(expression instanceof PsiReferenceExpression)) {
        return false;
      }
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      PsiElement target = referenceExpression.resolve();
      return iterator.equals(target);
    }

    private static String createNewVariableName(@Nonnull PsiWhileStatement scope, PsiType type, String containerName) {
      Project project = scope.getProject();
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      @NonNls String baseName;
      if (containerName != null) {
        baseName = StringUtils.createSingularFromName(containerName);
      }
      else {
        SuggestedNameInfo suggestions = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, type);
        String[] names = suggestions.names;
        if (names != null && names.length > 0) {
          baseName = names[0];
        }
        else {
          baseName = "value";
        }
      }
      if (baseName == null || baseName.length() == 0) {
        baseName = "value";
      }
      return codeStyleManager.suggestUniqueVariableName(baseName, scope, true);
    }

    @Nullable
    private static PsiStatement getFirstStatement(@Nonnull PsiStatement body) {
      if (body instanceof PsiBlockStatement) {
        PsiBlockStatement block = (PsiBlockStatement)body;
        PsiCodeBlock codeBlock = block.getCodeBlock();
        return ArrayUtil.getFirstElement(codeBlock.getStatements());
      }
      else {
        return body;
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new WhileCanBeForeachVisitor();
  }

  private static class WhileCanBeForeachVisitor extends BaseInspectionVisitor {

    @Override
    public void visitWhileStatement(@Nonnull PsiWhileStatement whileStatement) {
      super.visitWhileStatement(whileStatement);
      if (!PsiUtil.isLanguageLevel5OrHigher(whileStatement)) {
        return;
      }
      if (!isCollectionLoopStatement(whileStatement)) {
        return;
      }
      registerStatementError(whileStatement);
    }

    private static boolean isCollectionLoopStatement(PsiWhileStatement whileStatement) {
      PsiStatement initialization = getPreviousStatement(whileStatement);
      if (!(initialization instanceof PsiDeclarationStatement)) {
        return false;
      }
      PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
      PsiElement[] declaredElements = declaration.getDeclaredElements();
      if (declaredElements.length != 1) {
        return false;
      }
      PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiVariable)) {
        return false;
      }
      PsiVariable variable = (PsiVariable)declaredElement;
      if (!TypeUtils.variableHasTypeOrSubtype(variable, CommonClassNames.JAVA_UTIL_ITERATOR, "java.util.ListIterator")) {
        return false;
      }
      PsiExpression initialValue = variable.getInitializer();
      if (initialValue == null) {
        return false;
      }
      if (!(initialValue instanceof PsiMethodCallExpression)) {
        return false;
      }
      PsiMethodCallExpression initialCall = (PsiMethodCallExpression)initialValue;
      PsiExpressionList argumentList = initialCall.getArgumentList();
      PsiExpression[] argument = argumentList.getExpressions();
      if (argument.length != 0) {
        return false;
      }
      PsiReferenceExpression initialMethodExpression = initialCall.getMethodExpression();
      @NonNls String initialCallName = initialMethodExpression.getReferenceName();
      if (!"iterator".equals(initialCallName) && !"listIterator".equals(initialCallName)) {
        return false;
      }
      PsiExpression qualifier = initialMethodExpression.getQualifierExpression();
      if (qualifier == null) {
        return false;
      }
      PsiType qualifierType = qualifier.getType();
      if (!(qualifierType instanceof PsiClassType)) {
        return false;
      }
      PsiClass qualifierClass = ((PsiClassType)qualifierType).resolve();
      if (qualifierClass == null) {
        return false;
      }
      if (!InheritanceUtil.isInheritor(qualifierClass, CommonClassNames.JAVA_LANG_ITERABLE) &&
          !InheritanceUtil.isInheritor(qualifierClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
        return false;
      }
      PsiExpression condition = whileStatement.getCondition();
      if (!isHasNextCalled(variable, condition)) {
        return false;
      }
      PsiStatement body = whileStatement.getBody();
      if (body == null) {
        return false;
      }
      if (calculateCallsToIteratorNext(variable, body) != 1) {
        return false;
      }
      if (isIteratorRemoveCalled(variable, body)) {
        return false;
      }
      //noinspection SimplifiableIfStatement
      if (isIteratorHasNextCalled(variable, body)) {
        return false;
      }
      if (VariableAccessUtils.variableIsAssigned(variable, body)) {
        return false;
      }
      if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, body)) {
        return false;
      }
      PsiElement nextSibling = whileStatement.getNextSibling();
      while (nextSibling != null) {
        if (VariableAccessUtils.variableValueIsUsed(variable, nextSibling)) {
          return false;
        }
        nextSibling = nextSibling.getNextSibling();
      }
      return true;
    }

    private static boolean isHasNextCalled(PsiVariable iterator, PsiExpression condition) {
      if (!(condition instanceof PsiMethodCallExpression)) {
        return false;
      }
      PsiMethodCallExpression call = (PsiMethodCallExpression)condition;
      PsiExpressionList argumentList = call.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 0) {
        return false;
      }
      PsiReferenceExpression methodExpression = call.getMethodExpression();
      @NonNls String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.HAS_NEXT.equals(methodName)) {
        return false;
      }
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return true;
      }
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return false;
      }
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      PsiElement target = referenceExpression.resolve();
      return iterator.equals(target);
    }

    private static int calculateCallsToIteratorNext(PsiVariable iterator, PsiElement context) {
      NumCallsToIteratorNextVisitor visitor = new NumCallsToIteratorNextVisitor(iterator);
      context.accept(visitor);
      return visitor.getNumCallsToIteratorNext();
    }

    private static boolean isIteratorRemoveCalled(PsiVariable iterator, PsiElement context) {
      IteratorMethodCallVisitor visitor = new IteratorMethodCallVisitor(iterator);
      context.accept(visitor);
      return visitor.isMethodCalled();
    }

    private static boolean isIteratorHasNextCalled(PsiVariable iterator, PsiElement context) {
      IteratorHasNextVisitor visitor = new IteratorHasNextVisitor(iterator);
      context.accept(visitor);
      return visitor.isHasNextCalled();
    }
  }

  @Nullable
  public static PsiStatement getPreviousStatement(PsiElement context) {
    PsiElement prevStatement = PsiTreeUtil.skipSiblingsBackward(context, PsiWhiteSpace.class, PsiComment.class);
    if (!(prevStatement instanceof PsiStatement)) {
      return null;
    }
    return (PsiStatement)prevStatement;
  }

  private static class NumCallsToIteratorNextVisitor extends JavaRecursiveElementVisitor {

    private int numCallsToIteratorNext = 0;
    private final PsiVariable iterator;

    private NumCallsToIteratorNextVisitor(PsiVariable iterator) {
      this.iterator = iterator;
    }

    @Override
    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression callExpression) {
      super.visitMethodCallExpression(callExpression);
      PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
      @NonNls String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.NEXT.equals(methodName)) {
        return;
      }
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      PsiElement target = referenceExpression.resolve();
      if (!iterator.equals(target)) {
        return;
      }
      numCallsToIteratorNext++;
    }

    public int getNumCallsToIteratorNext() {
      return numCallsToIteratorNext;
    }
  }

  private static class IteratorMethodCallVisitor extends JavaRecursiveElementVisitor {

    private boolean methodCalled = false;
    private final PsiVariable iterator;

    IteratorMethodCallVisitor(PsiVariable iterator) {
      this.iterator = iterator;
    }

    @Override
    public void visitElement(@Nonnull PsiElement element) {
      if (!methodCalled) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
      if (methodCalled) {
        return;
      }
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      String name = methodExpression.getReferenceName();
      if (HardcodedMethodConstants.NEXT.equals(name)) {
        return;
      }
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      PsiElement target = referenceExpression.resolve();
      if (iterator.equals(target)) {
        methodCalled = true;
      }
    }

    public boolean isMethodCalled() {
      return methodCalled;
    }
  }

  private static class IteratorHasNextVisitor extends JavaRecursiveElementVisitor {

    private boolean hasNextCalled = false;
    private final PsiVariable iterator;

    private IteratorHasNextVisitor(PsiVariable iterator) {
      this.iterator = iterator;
    }

    @Override
    public void visitElement(@Nonnull PsiElement element) {
      if (!hasNextCalled) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls String name = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.HAS_NEXT.equals(name)) {
        return;
      }
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      PsiElement target = referenceExpression.resolve();
      if (iterator.equals(target)) {
        hasNextCalled = true;
      }
    }

    public boolean isHasNextCalled() {
      return hasNextCalled;
    }
  }
}