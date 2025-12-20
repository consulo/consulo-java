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
package com.intellij.java.impl.ig.migration;

import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.intellij.java.impl.ig.psiutils.StringUtils;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

@ExtensionImpl
public class ForCanBeForeachInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean REPORT_INDEXED_LOOP = true;
  @SuppressWarnings("PublicField")
  public boolean ignoreUntypedCollections = false;

  @Override
  @Nonnull
  public String getID() {
    return "ForLoopReplaceableByForEach";
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.forCanBeForeachDisplayName();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.forCanBeForeachProblemDescriptor().get();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ForCanBeForeachFix();
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsLocalize.forCanBeForeachOption().get(), "REPORT_INDEXED_LOOP");
    panel.addCheckbox(InspectionGadgetsLocalize.forCanBeForeachOption2().get(), "ignoreUntypedCollections");
    return panel;
  }

  private class ForCanBeForeachFix extends InspectionGadgetsFix {
    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.foreachReplaceQuickfix();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      PsiElement forElement = descriptor.getPsiElement();
      PsiElement parent = forElement.getParent();
      if (!(parent instanceof PsiForStatement)) {
        return;
      }
      PsiForStatement forStatement = (PsiForStatement)parent;
      String newExpression;
      if (isArrayLoopStatement(forStatement)) {
        newExpression = createArrayIterationText(forStatement);
      }
      else if (isCollectionLoopStatement(forStatement, ignoreUntypedCollections)) {
        newExpression = createCollectionIterationText(forStatement);
      }
      else if (isIndexedListLoopStatement(forStatement, ignoreUntypedCollections)) {
        newExpression = createListIterationText(forStatement);
      }
      else {
        return;
      }
      if (newExpression == null) {
        return;
      }
      replaceStatementAndShortenClassNames(forStatement, newExpression);
    }

    @Nullable
    private String createListIterationText(
      @Nonnull PsiForStatement forStatement) {
      PsiBinaryExpression condition = (PsiBinaryExpression)ParenthesesUtils.stripParentheses(forStatement.getCondition());
      if (condition == null) {
        return null;
      }
      PsiExpression lhs = ParenthesesUtils.stripParentheses(condition.getLOperand());
      if (lhs == null) {
        return null;
      }
      PsiExpression rhs = ParenthesesUtils.stripParentheses(condition.getROperand());
      if (rhs == null) {
        return null;
      }
      IElementType tokenType = condition.getOperationTokenType();
      String indexName;
      PsiExpression collectionSize;
      if (JavaTokenType.LT.equals(tokenType)) {
        indexName = lhs.getText();
        collectionSize = rhs;
      }
      else if (JavaTokenType.GT.equals(tokenType)) {
        indexName = rhs.getText();
        collectionSize = lhs;
      }
      else {
        return null;
      }
      if (collectionSize instanceof PsiReferenceExpression) {
        PsiReferenceExpression referenceExpression = (PsiReferenceExpression)collectionSize;
        PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiVariable) {
          PsiVariable variable = (PsiVariable)target;
          collectionSize = ParenthesesUtils.stripParentheses(variable.getInitializer());
        }
      }
      if (!(collectionSize instanceof PsiMethodCallExpression)) {
        return null;
      }
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)ParenthesesUtils.stripParentheses(collectionSize);
      if (methodCallExpression == null) {
        return null;
      }
      PsiReferenceExpression listLengthExpression = methodCallExpression.getMethodExpression();
      PsiExpression qualifier = listLengthExpression.getQualifierExpression();
      PsiReferenceExpression listReference;
      if (qualifier instanceof PsiReferenceExpression) {
        listReference = (PsiReferenceExpression)qualifier;
      }
      else {
        listReference = null;
      }
      PsiType parameterType;
      if (listReference == null) {
        parameterType = extractListTypeFromContainingClass(forStatement);
      }
      else {
        PsiType type = listReference.getType();
        if (type == null) {
          return null;
        }
        parameterType = extractContentTypeFromType(type);
      }
      if (parameterType == null) {
        parameterType = TypeUtils.getObjectType(forStatement);
      }
      String typeString = parameterType.getCanonicalText();
      PsiVariable listVariable;
      if (listReference == null) {
        listVariable = null;
      }
      else {
        PsiElement target = listReference.resolve();
        if (!(target instanceof PsiVariable)) {
          return null;
        }
        listVariable = (PsiVariable)target;
      }
      PsiStatement body = forStatement.getBody();
      PsiStatement firstStatement = getFirstStatement(body);
      boolean isDeclaration = isListElementDeclaration(firstStatement, listVariable, indexName, parameterType);
      String contentVariableName;
      @NonNls String finalString;
      PsiStatement statementToSkip;
      if (isDeclaration) {
        PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
        assert declarationStatement != null;
        PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable)) {
          return null;
        }
        PsiVariable variable = (PsiVariable)declaredElement;
        contentVariableName = variable.getName();
        statementToSkip = declarationStatement;
        if (variable.hasModifierProperty(PsiModifier.FINAL)) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
      }
      else {
        String collectionName;
        if (listReference == null) {
          collectionName = null;
        }
        else {
          collectionName = listReference.getReferenceName();
        }
        contentVariableName = createNewVariableName(forStatement, parameterType, collectionName);
        finalString = "";
        statementToSkip = null;
      }
      @NonNls StringBuilder out = new StringBuilder();
      out.append("for(");
      out.append(finalString);
      out.append(typeString);
      out.append(' ');
      out.append(contentVariableName);
      out.append(": ");
      @NonNls String listName;
      if (listReference == null) {
        listName = "this";
      }
      else {
        listName = listReference.getText();
      }
      out.append(listName);
      out.append(')');
      if (body != null) {
        replaceCollectionGetAccess(body, contentVariableName, listVariable, indexName, statementToSkip, out);
      }
      return out.toString();
    }

    @Nullable
    private PsiType extractContentTypeFromType(
      PsiType collectionType) {
      if (!(collectionType instanceof PsiClassType)) {
        return null;
      }
      PsiClassType classType = (PsiClassType)collectionType;
      PsiType[] parameterTypes = classType.getParameters();
      if (parameterTypes.length == 0) {
        return null;
      }
      PsiType parameterType = parameterTypes[0];
      if (parameterType == null) {
        return null;
      }
      if (parameterType instanceof PsiWildcardType) {
        PsiWildcardType wildcardType =
          (PsiWildcardType)parameterType;
        return wildcardType.getExtendsBound();
      }
      else if (parameterType instanceof PsiCapturedWildcardType) {
        PsiCapturedWildcardType capturedWildcardType =
          (PsiCapturedWildcardType)parameterType;
        PsiWildcardType wildcardType =
          capturedWildcardType.getWildcard();
        return wildcardType.getExtendsBound();
      }
      return parameterType;
    }

    @Nullable
    private PsiType extractListTypeFromContainingClass(
      PsiElement element) {
      PsiClass listClass = PsiTreeUtil.getParentOfType(element,
                                                       PsiClass.class);
      if (listClass == null) {
        return null;
      }
      PsiMethod[] getMethods =
        listClass.findMethodsByName("get", true);
      if (getMethods.length == 0) {
        return null;
      }
      PsiType type = getMethods[0].getReturnType();
      if (!(type instanceof PsiClassType)) {
        return null;
      }
      PsiClassType classType = (PsiClassType)type;
      PsiClass parameterClass = classType.resolve();
      if (parameterClass == null) {
        return null;
      }
      PsiClass subClass = null;
      while (listClass != null && !listClass.hasTypeParameters()) {
        subClass = listClass;
        listClass = listClass.getSuperClass();
      }
      if (listClass == null || subClass == null) {
        return TypeUtils.getObjectType(element);
      }
      PsiTypeParameter[] typeParameters =
        listClass.getTypeParameters();
      if (!parameterClass.equals(typeParameters[0])) {
        return TypeUtils.getObjectType(element);
      }
      PsiReferenceList extendsList = subClass.getExtendsList();
      if (extendsList == null) {
        return null;
      }
      PsiJavaCodeReferenceElement[] referenceElements =
        extendsList.getReferenceElements();
      if (referenceElements.length == 0) {
        return null;
      }
      PsiType[] types =
        referenceElements[0].getTypeParameters();
      if (types.length == 0) {
        return TypeUtils.getObjectType(element);
      }
      return types[0];
    }

    @Nullable
    private String createCollectionIterationText(
      @Nonnull PsiForStatement forStatement)
      throws IncorrectOperationException {
      PsiStatement body = forStatement.getBody();
      PsiStatement firstStatement = getFirstStatement(body);
      PsiStatement initialization =
        forStatement.getInitialization();
      if (!(initialization instanceof PsiDeclarationStatement)) {
        return null;
      }
      PsiDeclarationStatement declaration =
        (PsiDeclarationStatement)initialization;
      PsiElement declaredIterator =
        declaration.getDeclaredElements()[0];
      if (!(declaredIterator instanceof PsiVariable)) {
        return null;
      }
      PsiVariable iteratorVariable = (PsiVariable)declaredIterator;
      PsiMethodCallExpression initializer =
        (PsiMethodCallExpression)iteratorVariable.getInitializer();
      if (initializer == null) {
        return null;
      }
      PsiType iteratorType = initializer.getType();
      if (iteratorType == null) {
        return null;
      }
      PsiType iteratorContentType =
        extractContentTypeFromType(iteratorType);
      PsiType iteratorVariableType = iteratorVariable.getType();
      PsiType contentType;
      PsiClassType javaLangObject = TypeUtils.getObjectType(forStatement);
      if (iteratorContentType == null) {
        PsiType iteratorVariableContentType =
          extractContentTypeFromType(iteratorVariableType);
        if (iteratorVariableContentType == null) {
          contentType = javaLangObject;
        }
        else {
          contentType = iteratorVariableContentType;
        }
      }
      else {
        contentType = iteratorContentType;
      }
      PsiReferenceExpression methodExpression =
        initializer.getMethodExpression();
      PsiExpression collection =
        methodExpression.getQualifierExpression();
      String iteratorName = iteratorVariable.getName();
      boolean isDeclaration =
        isIteratorNextDeclaration(firstStatement, iteratorName,
                                  contentType);
      PsiStatement statementToSkip;
      @NonNls String finalString;
      String contentVariableName;
      if (isDeclaration) {
        PsiDeclarationStatement declarationStatement =
          (PsiDeclarationStatement)firstStatement;
        assert declarationStatement != null;
        PsiElement[] declaredElements =
          declarationStatement.getDeclaredElements();
        PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable)) {
          return null;
        }
        PsiVariable variable = (PsiVariable)declaredElement;
        contentVariableName = variable.getName();
        statementToSkip = declarationStatement;
        if (variable.hasModifierProperty(PsiModifier.FINAL)) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
      }
      else {
        if (collection instanceof PsiReferenceExpression) {
          PsiReferenceExpression referenceExpression =
            (PsiReferenceExpression)collection;
          String collectionName =
            referenceExpression.getReferenceName();
          contentVariableName = createNewVariableName(forStatement,
                                                      contentType, collectionName);
        }
        else {
          contentVariableName = createNewVariableName(forStatement,
                                                      contentType, null);
        }
        Project project = forStatement.getProject();
        JavaCodeStyleSettings codeStyleSettings =
          CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
        if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
        statementToSkip = null;
      }
      String contentTypeString = contentType.getCanonicalText();
      @NonNls StringBuilder out = new StringBuilder();
      out.append("for(");
      out.append(finalString);
      out.append(contentTypeString);
      out.append(' ');
      out.append(contentVariableName);
      out.append(": ");
      if (!contentType.equals(javaLangObject)) {
        @NonNls String iterableTypeString =
          "java.lang.Iterable<" + contentTypeString + '>';
        if (iteratorContentType == null) {
          out.append('(');
          out.append(iterableTypeString);
          out.append(')');
        }
      }
      if (collection == null) {
        out.append("this");
      }
      else {
        out.append(collection.getText());
      }
      out.append(')');
      replaceIteratorNext(body, contentVariableName, iteratorName,
                          statementToSkip, out, contentType);
      return out.toString();
    }

    @Nullable
    private String createArrayIterationText(@Nonnull PsiForStatement forStatement) {
      PsiExpression condition = forStatement.getCondition();
      PsiBinaryExpression strippedCondition = (PsiBinaryExpression)ParenthesesUtils.stripParentheses(condition);
      if (strippedCondition == null) {
        return null;
      }
      PsiExpression lhs = ParenthesesUtils.stripParentheses(strippedCondition.getLOperand());
      if (lhs == null) {
        return null;
      }
      PsiExpression rhs = ParenthesesUtils.stripParentheses(strippedCondition.getROperand());
      if (rhs == null) {
        return null;
      }
      IElementType tokenType = strippedCondition.getOperationTokenType();
      PsiReferenceExpression arrayLengthExpression;
      String indexName;
      if (tokenType.equals(JavaTokenType.LT)) {
        arrayLengthExpression = (PsiReferenceExpression)ParenthesesUtils.stripParentheses(rhs);
        indexName = lhs.getText();
      }
      else if (tokenType.equals(JavaTokenType.GT)) {
        arrayLengthExpression = (PsiReferenceExpression)ParenthesesUtils.stripParentheses(lhs);
        indexName = rhs.getText();
      }
      else {
        return null;
      }
      if (arrayLengthExpression == null) {
        return null;
      }
      PsiReferenceExpression arrayReference = (PsiReferenceExpression)arrayLengthExpression.getQualifierExpression();
      if (arrayReference == null) {
        PsiElement target = arrayLengthExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return null;
        }
        PsiVariable variable = (PsiVariable)target;
        PsiExpression initializer = variable.getInitializer();
        if (!(initializer instanceof PsiReferenceExpression)) {
          return null;
        }
        PsiReferenceExpression referenceExpression = (PsiReferenceExpression)initializer;
        arrayReference = (PsiReferenceExpression)referenceExpression.getQualifierExpression();
        if (arrayReference == null) {
          return null;
        }
      }
      PsiArrayType arrayType = (PsiArrayType)arrayReference.getType();
      if (arrayType == null) {
        return null;
      }
      PsiType componentType = arrayType.getComponentType();
      String typeText = componentType.getCanonicalText();
      PsiElement target = arrayReference.resolve();
      if (!(target instanceof PsiVariable)) {
        return null;
      }
      PsiVariable arrayVariable = (PsiVariable)target;
      PsiStatement body = forStatement.getBody();
      PsiStatement firstStatement = getFirstStatement(body);
      boolean isDeclaration = isArrayElementDeclaration(firstStatement, arrayVariable, indexName);
      String contentVariableName;
      @NonNls String finalString;
      PsiStatement statementToSkip;
      if (isDeclaration) {
        PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
        assert declarationStatement != null;
        PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable)) {
          return null;
        }
        PsiVariable variable = (PsiVariable)declaredElement;
        if (VariableAccessUtils.variableIsAssigned(variable, forStatement)) {
          String collectionName = arrayReference.getReferenceName();
          contentVariableName = createNewVariableName(forStatement, componentType, collectionName);
          Project project = forStatement.getProject();
          JavaCodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
          if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
            finalString = "final ";
          }
          else {
            finalString = "";
          }
          statementToSkip = null;
        }
        else {
          contentVariableName = variable.getName();
          statementToSkip = declarationStatement;
          if (variable.hasModifierProperty(PsiModifier.FINAL)) {
            finalString = "final ";
          }
          else {
            finalString = "";
          }
        }
      }
      else {
        String collectionName = arrayReference.getReferenceName();
        contentVariableName = createNewVariableName(forStatement, componentType, collectionName);
        Project project = forStatement.getProject();
        JavaCodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
        if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
          finalString = "final ";
        }
        else {
          finalString = "";
        }
        statementToSkip = null;
      }
      @NonNls StringBuilder out = new StringBuilder();
      out.append("for(");
      out.append(finalString);
      out.append(typeText);
      out.append(' ');
      out.append(contentVariableName);
      out.append(": ");
      String arrayName = arrayReference.getText();
      out.append(arrayName);
      out.append(')');
      if (body != null) {
        replaceArrayAccess(body, contentVariableName, arrayVariable, indexName, statementToSkip, out);
      }
      return out.toString();
    }

    private void replaceArrayAccess(
      PsiElement element, String contentVariableName,
      PsiVariable arrayVariable, String indexName,
      PsiElement childToSkip, StringBuilder out) {
      if (isArrayLookup(element, indexName, arrayVariable)) {
        out.append(contentVariableName);
      }
      else {
        PsiElement[] children = element.getChildren();
        if (children.length == 0) {
          String text = element.getText();
          if (PsiKeyword.INSTANCEOF.equals(text) &&
              out.charAt(out.length() - 1) != ' ') {
            out.append(' ');
          }
          out.append(text);
        }
        else {
          boolean skippingWhiteSpace = false;
          for (PsiElement child : children) {
            if (child.equals(childToSkip)) {
              skippingWhiteSpace = true;
            }
            else if (child instanceof PsiWhiteSpace &&
                     skippingWhiteSpace) {
              //don't do anything
            }
            else {
              skippingWhiteSpace = false;
              replaceArrayAccess(child, contentVariableName,
                                 arrayVariable, indexName,
                                 childToSkip, out);
            }
          }
        }
      }
    }

    private void replaceCollectionGetAccess(
      PsiElement element, String contentVariableName,
      PsiVariable listVariable, String indexName,
      PsiElement childToSkip, StringBuilder out) {
      if (isListGetLookup(element, indexName, listVariable)) {
        out.append(contentVariableName);
      }
      else {
        PsiElement[] children = element.getChildren();
        if (children.length == 0) {
          String text = element.getText();
          if (PsiKeyword.INSTANCEOF.equals(text) &&
              out.charAt(out.length() - 1) != ' ') {
            out.append(' ');
          }
          out.append(text);
        }
        else {
          boolean skippingWhiteSpace = false;
          for (PsiElement child : children) {
            if (child.equals(childToSkip)) {
              skippingWhiteSpace = true;
            }
            else if (child instanceof PsiWhiteSpace &&
                     skippingWhiteSpace) {
              //don't do anything
            }
            else {
              skippingWhiteSpace = false;
              replaceCollectionGetAccess(child,
                                         contentVariableName,
                                         listVariable, indexName,
                                         childToSkip, out);
            }
          }
        }
      }
    }

    private boolean isListGetLookup(PsiElement element,
                                    String indexName,
                                    PsiVariable listVariable) {
      if (!(element instanceof PsiExpression)) {
        return false;
      }
      PsiExpression expression = (PsiExpression)element;
      if (!expressionIsListGetLookup(expression)) {
        return false;
      }
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)
          ParenthesesUtils.stripParentheses(expression);
      if (methodCallExpression == null) {
        return false;
      }
      PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      PsiExpression qualifierExpression =
        methodExpression.getQualifierExpression();

      PsiExpressionList argumentList =
        methodCallExpression.getArgumentList();
      PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length != 1) {
        return false;
      }
      if (!indexName.equals(expressions[0].getText())) {
        return false;
      }
      if (qualifierExpression == null ||
          qualifierExpression instanceof PsiThisExpression ||
          qualifierExpression instanceof PsiSuperExpression) {
        return listVariable == null;
      }
      if (!(qualifierExpression instanceof PsiReferenceExpression)) {
        return false;
      }
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)qualifierExpression;
      PsiExpression qualifier =
        referenceExpression.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression) &&
          !(qualifier instanceof PsiSuperExpression)) {
        return false;
      }
      PsiElement target = referenceExpression.resolve();
      return listVariable.equals(target);
    }

    private void replaceIteratorNext(
      PsiElement element, String contentVariableName,
      String iteratorName, PsiElement childToSkip,
      StringBuilder out, PsiType contentType) {
      if (isIteratorNext(element, iteratorName, contentType)) {
        out.append(contentVariableName);
      }
      else {
        PsiElement[] children = element.getChildren();
        if (children.length == 0) {
          String text = element.getText();
          if (PsiKeyword.INSTANCEOF.equals(text) &&
              out.charAt(out.length() - 1) != ' ') {
            out.append(' ');
          }
          out.append(text);
        }
        else {
          boolean skippingWhiteSpace = false;
          for (PsiElement child : children) {
            if (child.equals(childToSkip)) {
              skippingWhiteSpace = true;
            }
            else if (child instanceof PsiWhiteSpace &&
                     skippingWhiteSpace) {
              //don't do anything
            }
            else {
              skippingWhiteSpace = false;
              replaceIteratorNext(child, contentVariableName,
                                  iteratorName, childToSkip, out, contentType);
            }
          }
        }
      }
    }

    private boolean isArrayElementDeclaration(
      PsiStatement statement, PsiVariable arrayVariable,
      String indexName) {
      if (!(statement instanceof PsiDeclarationStatement)) {
        return false;
      }
      PsiDeclarationStatement declarationStatement =
        (PsiDeclarationStatement)statement;
      PsiElement[] declaredElements =
        declarationStatement.getDeclaredElements();
      if (declaredElements.length != 1) {
        return false;
      }
      PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiVariable)) {
        return false;
      }
      PsiVariable variable = (PsiVariable)declaredElement;
      PsiExpression initializer = variable.getInitializer();
      return isArrayLookup(initializer, indexName, arrayVariable);
    }

    private boolean isListElementDeclaration(
      PsiStatement statement, PsiVariable listVariable,
      String indexName, PsiType type) {
      if (!(statement instanceof PsiDeclarationStatement)) {
        return false;
      }
      PsiDeclarationStatement declarationStatement =
        (PsiDeclarationStatement)statement;
      PsiElement[] declaredElements =
        declarationStatement.getDeclaredElements();
      if (declaredElements.length != 1) {
        return false;
      }
      PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiVariable)) {
        return false;
      }
      PsiVariable variable = (PsiVariable)declaredElement;
      PsiExpression initializer = variable.getInitializer();
      if (!isListGetLookup(initializer, indexName, listVariable)) {
        return false;
      }
      return type != null && type.equals(variable.getType());
    }

    private boolean isIteratorNextDeclaration(
      PsiStatement statement, String iteratorName,
      PsiType contentType) {
      if (!(statement instanceof PsiDeclarationStatement)) {
        return false;
      }
      PsiDeclarationStatement declarationStatement =
        (PsiDeclarationStatement)statement;
      PsiElement[] declaredElements =
        declarationStatement.getDeclaredElements();
      if (declaredElements.length != 1) {
        return false;
      }
      PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiVariable)) {
        return false;
      }
      PsiVariable variable = (PsiVariable)declaredElement;
      PsiExpression initializer = variable.getInitializer();
      return isIteratorNext(initializer, iteratorName, contentType);
    }

    private boolean isArrayLookup(
      PsiElement element, String indexName, PsiVariable arrayVariable) {
      if (element == null) {
        return false;
      }
      if (!(element instanceof PsiArrayAccessExpression)) {
        return false;
      }
      PsiArrayAccessExpression arrayAccess =
        (PsiArrayAccessExpression)element;
      PsiExpression indexExpression =
        arrayAccess.getIndexExpression();
      if (indexExpression == null) {
        return false;
      }
      if (!indexName.equals(indexExpression.getText())) {
        return false;
      }
      PsiExpression arrayExpression =
        arrayAccess.getArrayExpression();
      if (!(arrayExpression instanceof PsiReferenceExpression)) {
        return false;
      }
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)arrayExpression;
      PsiExpression qualifier =
        referenceExpression.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression) &&
          !(qualifier instanceof PsiSuperExpression)) {
        return false;
      }
      PsiElement target = referenceExpression.resolve();
      return arrayVariable.equals(target);
    }

    private boolean isIteratorNext(
      PsiElement element, String iteratorName, PsiType contentType) {
      if (element == null) {
        return false;
      }
      if (element instanceof PsiTypeCastExpression) {
        PsiTypeCastExpression castExpression =
          (PsiTypeCastExpression)element;
        PsiType type = castExpression.getType();
        if (type == null) {
          return false;
        }
        if (!type.equals(contentType)) {
          return false;
        }
        PsiExpression operand =
          castExpression.getOperand();
        return isIteratorNext(operand, iteratorName, contentType);
      }
      if (!(element instanceof PsiMethodCallExpression)) {
        return false;
      }
      PsiMethodCallExpression callExpression =
        (PsiMethodCallExpression)element;
      PsiExpressionList argumentList =
        callExpression.getArgumentList();
      PsiExpression[] args = argumentList.getExpressions();
      if (args.length != 0) {
        return false;
      }
      PsiReferenceExpression reference =
        callExpression.getMethodExpression();
      PsiExpression qualifier = reference.getQualifierExpression();
      if (qualifier == null) {
        return false;
      }
      if (!iteratorName.equals(qualifier.getText())) {
        return false;
      }
      String referenceName = reference.getReferenceName();
      return HardcodedMethodConstants.NEXT.equals(referenceName);
    }

    private String createNewVariableName(
      @Nonnull PsiForStatement scope, PsiType type,
      @Nullable String containerName) {
      Project project = scope.getProject();
      JavaCodeStyleManager codeStyleManager =
        JavaCodeStyleManager.getInstance(project);
      @NonNls String baseName;
      if (containerName != null) {
        baseName = StringUtils.createSingularFromName(containerName);
      }
      else {
        SuggestedNameInfo suggestions =
          codeStyleManager.suggestVariableName(
            VariableKind.LOCAL_VARIABLE, null, null, type);
        String[] names = suggestions.names;
        if (names != null && names.length > 0) {
          baseName = names[0];
        }
        else {
          baseName = "value";
        }
      }
      if (baseName == null || baseName.isEmpty()) {
        baseName = "value";
      }
      return codeStyleManager.suggestUniqueVariableName(baseName, scope,
                                                        true);
    }

    @Nullable
    private PsiStatement getFirstStatement(PsiStatement body) {
      if (!(body instanceof PsiBlockStatement)) {
        return body;
      }
      PsiBlockStatement block = (PsiBlockStatement)body;
      PsiCodeBlock codeBlock = block.getCodeBlock();
      PsiStatement[] statements = codeBlock.getStatements();
      if (statements.length <= 0) {
        return null;
      }
      return statements[0];
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ForCanBeForeachVisitor();
  }

  private class ForCanBeForeachVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(
      @Nonnull PsiForStatement forStatement) {
      super.visitForStatement(forStatement);
      if (!PsiUtil.isLanguageLevel5OrHigher(forStatement)) {
        return;
      }
      if (isArrayLoopStatement(forStatement)
          || isCollectionLoopStatement(forStatement,
                                       ignoreUntypedCollections)
          || (REPORT_INDEXED_LOOP &&
              isIndexedListLoopStatement(forStatement,
                                         ignoreUntypedCollections))) {
        registerStatementError(forStatement);
      }
    }
  }

  private static boolean isIndexedListLoopStatement(PsiForStatement forStatement, boolean ignoreUntypedCollections) {
    PsiStatement initialization = forStatement.getInitialization();
    if (!(initialization instanceof PsiDeclarationStatement)) {
      return false;
    }
    PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
    PsiElement[] declaredElements = declaration.getDeclaredElements();
    PsiElement secondDeclaredElement;
    if (declaredElements.length == 1) {
      secondDeclaredElement = null;
    }
    else if (declaredElements.length == 2) {
      secondDeclaredElement = declaredElements[1];
    }
    else {
      return false;
    }
    PsiElement declaredElement = declaredElements[0];
    if (!(declaredElement instanceof PsiVariable)) {
      return false;
    }
    PsiVariable indexVariable = (PsiVariable)declaredElement;
    PsiExpression initialValue = indexVariable.getInitializer();
    if (initialValue == null) {
      return false;
    }
    Object constant = ExpressionUtils.computeConstantExpression(initialValue);
    if (!(constant instanceof Number)) {
      return false;
    }
    Number number = (Number)constant;
    if (number.intValue() != 0) {
      return false;
    }
    PsiExpression condition = forStatement.getCondition();
    Holder collectionHolder = getCollectionFromSizeComparison(condition, indexVariable, secondDeclaredElement);
    if (collectionHolder == null) {
      return false;
    }
    PsiStatement update = forStatement.getUpdate();
    if (!VariableAccessUtils.variableIsIncremented(indexVariable, update)) {
      return false;
    }
    PsiStatement body = forStatement.getBody();
    if (!isIndexVariableOnlyUsedAsListIndex(collectionHolder, indexVariable, body)) {
      return false;
    }
    if (collectionHolder != Holder.DUMMY) {
      PsiVariable collection = collectionHolder.getVariable();
      PsiClassType collectionType = (PsiClassType)collection.getType();
      PsiType[] parameters = collectionType.getParameters();
      if (ignoreUntypedCollections && parameters.length == 0) {
        return false;
      }
      return !VariableAccessUtils.variableIsAssigned(collection, body);
    }
    return true;
  }

  static boolean isArrayLoopStatement(PsiForStatement forStatement) {
    PsiStatement initialization = forStatement.getInitialization();
    if (!(initialization instanceof PsiDeclarationStatement)) {
      return false;
    }
    PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
    PsiElement[] declaredElements = declaration.getDeclaredElements();
    PsiElement secondDeclaredElement;
    if (declaredElements.length == 1) {
      secondDeclaredElement = null;
    }
    else if (declaredElements.length == 2) {
      secondDeclaredElement = declaredElements[1];
    }
    else {
      return false;
    }
    PsiElement declaredElement = declaredElements[0];
    if (!(declaredElement instanceof PsiVariable)) {
      return false;
    }
    PsiVariable indexVariable = (PsiVariable)declaredElement;
    PsiExpression initialValue = indexVariable.getInitializer();
    if (initialValue == null) {
      return false;
    }
    Object constant =
      ExpressionUtils.computeConstantExpression(initialValue);
    if (!(constant instanceof Integer)) {
      return false;
    }
    Integer integer = (Integer)constant;
    if (integer.intValue() != 0) {
      return false;
    }
    PsiStatement update = forStatement.getUpdate();
    if (!VariableAccessUtils.variableIsIncremented(indexVariable, update)) {
      return false;
    }
    PsiExpression condition = forStatement.getCondition();
    PsiReferenceExpression arrayReference = getVariableReferenceFromCondition(condition, indexVariable, secondDeclaredElement);
    if (arrayReference == null) {
      return false;
    }
    PsiElement element = arrayReference.resolve();
    if (!(element instanceof PsiVariable)) {
      return false;
    }
    PsiVariable arrayVariable = (PsiVariable)element;
    PsiStatement body = forStatement.getBody();
    return body == null ||
           isIndexVariableOnlyUsedAsIndex(arrayVariable, indexVariable, body) &&
           !VariableAccessUtils.variableIsAssigned(arrayVariable, body) &&
           !VariableAccessUtils.arrayContentsAreAssigned(arrayVariable, body);
  }

  private static boolean isIndexVariableOnlyUsedAsIndex(
    @Nonnull PsiVariable arrayVariable,
    @Nonnull PsiVariable indexVariable,
    @Nullable PsiStatement body) {
    if (body == null) {
      return true;
    }
    VariableOnlyUsedAsIndexVisitor visitor =
      new VariableOnlyUsedAsIndexVisitor(arrayVariable, indexVariable);
    body.accept(visitor);
    return visitor.isIndexVariableUsedOnlyAsIndex();
  }

  private static boolean isIndexVariableOnlyUsedAsListIndex(
    Holder collectionHolder, PsiVariable indexVariable,
    PsiStatement body) {
    if (body == null) {
      return true;
    }
    VariableOnlyUsedAsListIndexVisitor visitor =
      new VariableOnlyUsedAsListIndexVisitor(collectionHolder,
                                             indexVariable);
    body.accept(visitor);
    return visitor.isIndexVariableUsedOnlyAsIndex();
  }

  static boolean isCollectionLoopStatement(
    PsiForStatement forStatement, boolean ignoreUntypedCollections) {
    PsiStatement initialization = forStatement.getInitialization();
    if (!(initialization instanceof PsiDeclarationStatement)) {
      return false;
    }
    PsiDeclarationStatement declaration =
      (PsiDeclarationStatement)initialization;
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
    PsiMethodCallExpression initialCall =
      (PsiMethodCallExpression)initialValue;
    PsiReferenceExpression initialMethodExpression =
      initialCall.getMethodExpression();
    @NonNls String initialCallName =
      initialMethodExpression.getReferenceName();
    if (!HardcodedMethodConstants.ITERATOR.equals(initialCallName) &&
        !"listIterator".equals(initialCallName)) {
      return false;
    }
    PsiExpressionList argumentList = initialCall.getArgumentList();
    PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length != 0) {
      return false;
    }
    PsiExpression qualifier =
      initialMethodExpression.getQualifierExpression();
    PsiClass qualifierClass;
    if (qualifier == null) {
      qualifierClass =
        ClassUtils.getContainingClass(initialMethodExpression);
      if (ignoreUntypedCollections) {
        PsiClassType type = (PsiClassType)variable.getType();
        PsiType[] parameters = type.getParameters();
        if (parameters.length == 0) {
          return false;
        }
      }
    }
    else {
      PsiType qualifierType = qualifier.getType();
      if (!(qualifierType instanceof PsiClassType)) {
        return false;
      }
      PsiClassType classType = (PsiClassType)qualifierType;
      qualifierClass = classType.resolve();
      if (ignoreUntypedCollections) {
        PsiClassType type = (PsiClassType)variable.getType();
        PsiType[] parameters = type.getParameters();
        PsiType[] parameters1 = classType.getParameters();
        if (parameters.length == 0 && parameters1.length == 0) {
          return false;
        }
      }
    }
    if (qualifierClass == null) {
      return false;
    }
    if (!InheritanceUtil.isInheritor(qualifierClass, CommonClassNames.JAVA_LANG_ITERABLE)
        && !InheritanceUtil.isInheritor(qualifierClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
      return false;
    }
    PsiExpression condition = forStatement.getCondition();
    if (!isHasNext(condition, variable)) {
      return false;
    }
    PsiStatement update = forStatement.getUpdate();
    if (update != null && !(update instanceof PsiEmptyStatement)) {
      return false;
    }
    PsiStatement body = forStatement.getBody();
    if (body == null) {
      return false;
    }
    if (calculateCallsToIteratorNext(variable, body) != 1) {
      return false;
    }
    if (isIteratorMethodCalled(variable, body)) {
      return false;
    }
    return !VariableAccessUtils.variableIsReturned(variable, body) &&
           !VariableAccessUtils.variableIsAssigned(variable, body) &&
           !VariableAccessUtils.variableIsPassedAsMethodArgument(variable, body);
  }

  private static int calculateCallsToIteratorNext(PsiVariable iterator,
                                                  PsiStatement body) {
    if (body == null) {
      return 0;
    }
    NumCallsToIteratorNextVisitor visitor =
      new NumCallsToIteratorNextVisitor(iterator);
    body.accept(visitor);
    return visitor.getNumCallsToIteratorNext();
  }

  private static boolean isIteratorMethodCalled(PsiVariable iterator,
                                                PsiStatement body) {
    IteratorMethodCallVisitor visitor =
      new IteratorMethodCallVisitor(iterator);
    body.accept(visitor);
    return visitor.isMethodCalled();
  }

  private static boolean isHasNext(PsiExpression condition,
                                   PsiVariable iterator) {
    if (!(condition instanceof PsiMethodCallExpression)) {
      return false;
    }
    PsiMethodCallExpression call =
      (PsiMethodCallExpression)condition;
    PsiExpressionList argumentList = call.getArgumentList();
    PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length != 0) {
      return false;
    }
    PsiReferenceExpression methodExpression =
      call.getMethodExpression();
    String methodName = methodExpression.getReferenceName();
    if (!HardcodedMethodConstants.HAS_NEXT.equals(methodName)) {
      return false;
    }
    PsiExpression qualifier =
      methodExpression.getQualifierExpression();
    if (qualifier == null) {
      return true;
    }
    if (!(qualifier instanceof PsiReferenceExpression)) {
      return false;
    }
    PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)qualifier;
    PsiElement target = referenceExpression.resolve();
    return iterator.equals(target);
  }

  @Nullable
  private static PsiReferenceExpression getVariableReferenceFromCondition(PsiExpression condition,
                                                                          PsiVariable variable,
                                                                          PsiElement secondDeclaredElement) {
    condition = ParenthesesUtils.stripParentheses(condition);
    if (!(condition instanceof PsiBinaryExpression)) {
      return null;
    }
    PsiBinaryExpression binaryExpression = (PsiBinaryExpression)condition;
    IElementType tokenType = binaryExpression.getOperationTokenType();
    PsiExpression lhs = ParenthesesUtils.stripParentheses(binaryExpression.getLOperand());
    PsiExpression rhs = ParenthesesUtils.stripParentheses(binaryExpression.getROperand());
    if (rhs == null) {
      return null;
    }
    PsiReferenceExpression referenceExpression;
    if (tokenType.equals(JavaTokenType.LT)) {
      if (!VariableAccessUtils.evaluatesToVariable(lhs, variable) || !(rhs instanceof PsiReferenceExpression)) {
        return null;
      }
      referenceExpression = (PsiReferenceExpression)rhs;
    }
    else if (tokenType.equals(JavaTokenType.GT)) {
      if (!VariableAccessUtils.evaluatesToVariable(rhs, variable) || !(lhs instanceof PsiReferenceExpression)) {
        return null;
      }
      referenceExpression = (PsiReferenceExpression)lhs;
    }
    else {
      return null;
    }
    if (!expressionIsArrayLengthLookup(referenceExpression)) {
      PsiElement target = referenceExpression.resolve();
      if (secondDeclaredElement != null && !secondDeclaredElement.equals(target)) {
        return null;
      }
      if (target instanceof PsiVariable) {
        PsiVariable maxVariable = (PsiVariable)target;
        PsiCodeBlock context = PsiTreeUtil.getParentOfType(maxVariable, PsiCodeBlock.class);
        if (context == null) {
          return null;
        }
        if (VariableAccessUtils.variableIsAssigned(maxVariable, context)) {
          return null;
        }
        PsiExpression expression = ParenthesesUtils.stripParentheses(maxVariable.getInitializer());
        if (!(expression instanceof PsiReferenceExpression)) {
          return null;
        }
        referenceExpression = (PsiReferenceExpression)expression;
        if (!expressionIsArrayLengthLookup(referenceExpression)) {
          return null;
        }
      }
    }
    else {
      if (secondDeclaredElement != null) {
        return null;
      }
    }
    PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
    if (qualifierExpression instanceof PsiReferenceExpression) {
      return (PsiReferenceExpression)qualifierExpression;
    }
    else if (qualifierExpression instanceof PsiThisExpression ||
             qualifierExpression instanceof PsiSuperExpression ||
             qualifierExpression == null) {
      return referenceExpression;
    }
    else {
      return null;
    }
  }

  @Nullable
  private static Holder getCollectionFromSizeComparison(PsiExpression condition, PsiVariable variable, PsiElement secondDeclaredElement) {
    condition = ParenthesesUtils.stripParentheses(condition);
    if (!(condition instanceof PsiBinaryExpression)) {
      return null;
    }
    PsiBinaryExpression binaryExpression = (PsiBinaryExpression)condition;
    IElementType tokenType = binaryExpression.getOperationTokenType();
    PsiExpression rhs = binaryExpression.getROperand();
    PsiExpression lhs = binaryExpression.getLOperand();
    if (tokenType.equals(JavaTokenType.LT)) {
      if (!VariableAccessUtils.evaluatesToVariable(lhs, variable)) {
        return null;
      }
      return getCollectionFromListMethodCall(rhs, HardcodedMethodConstants.SIZE, secondDeclaredElement);
    }
    else if (tokenType.equals(JavaTokenType.GT)) {
      if (!VariableAccessUtils.evaluatesToVariable(rhs, variable)) {
        return null;
      }
      return getCollectionFromListMethodCall(lhs, HardcodedMethodConstants.SIZE, secondDeclaredElement);
    }
    return null;
  }

  static boolean expressionIsListGetLookup(PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiMethodCallExpression)) {
      return false;
    }
    PsiMethodCallExpression reference =
      (PsiMethodCallExpression)expression;
    PsiReferenceExpression methodExpression =
      reference.getMethodExpression();
    PsiElement resolved = methodExpression.resolve();
    if (!(resolved instanceof PsiMethod)) {
      return false;
    }
    PsiMethod method = (PsiMethod)resolved;
    if (!HardcodedMethodConstants.GET.equals(method.getName())) {
      return false;
    }
    PsiClass aClass = method.getContainingClass();
    return InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_LIST);
  }

  @Nullable
  private static Holder getCollectionFromListMethodCall(PsiExpression expression, String methodName, PsiElement secondDeclaredElement) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      PsiElement target = referenceExpression.resolve();
      if (secondDeclaredElement != null && !secondDeclaredElement.equals(target)) {
        return null;
      }
      if (!(target instanceof PsiVariable)) {
        return null;
      }
      PsiVariable variable = (PsiVariable)target;
      PsiCodeBlock context = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (context == null) {
        return null;
      }
      if (VariableAccessUtils.variableIsAssigned(variable, context)) {
        return null;
      }
      expression = ParenthesesUtils.stripParentheses(variable.getInitializer());
    }
    else if (secondDeclaredElement !=  null) {
      return null;
    }
    if (!(expression instanceof PsiMethodCallExpression)) {
      return null;
    }
    PsiMethodCallExpression methodCallExpression =
      (PsiMethodCallExpression)expression;
    PsiReferenceExpression methodExpression =
      methodCallExpression.getMethodExpression();
    String referenceName = methodExpression.getReferenceName();
    if (!methodName.equals(referenceName)) {
      return null;
    }
    PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return null;
    }
    PsiClass containingClass = method.getContainingClass();
    if (!InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_LIST)) {
      return null;
    }
    PsiExpression qualifierExpression =
      ParenthesesUtils.stripParentheses(
        methodExpression.getQualifierExpression());
    if (qualifierExpression == null ||
        qualifierExpression instanceof PsiThisExpression ||
        qualifierExpression instanceof PsiSuperExpression) {
      return Holder.DUMMY;
    }
    if (!(qualifierExpression instanceof PsiReferenceExpression)) {
      return null;
    }
    PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)qualifierExpression;
    PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiVariable)) {
      return null;
    }
    PsiVariable variable = (PsiVariable)target;
    return new Holder(variable);
  }

  private static boolean expressionIsArrayLengthLookup(PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiReferenceExpression)) {
      return false;
    }
    PsiReferenceExpression reference =
      (PsiReferenceExpression)expression;
    String referenceName = reference.getReferenceName();
    if (!HardcodedMethodConstants.LENGTH.equals(referenceName)) {
      return false;
    }
    PsiExpression qualifier = reference.getQualifierExpression();
    if (!(qualifier instanceof PsiReferenceExpression)) {
      return false;
    }
    PsiType type = qualifier.getType();
    return type != null && type.getArrayDimensions() > 0;
  }

  private static class NumCallsToIteratorNextVisitor
    extends JavaRecursiveElementVisitor {

    private int numCallsToIteratorNext = 0;
    private final PsiVariable iterator;

    NumCallsToIteratorNextVisitor(PsiVariable iterator) {
      this.iterator = iterator;
    }

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression callExpression) {
      super.visitMethodCallExpression(callExpression);
      PsiReferenceExpression methodExpression =
        callExpression.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.NEXT.equals(methodName)) {
        return;
      }
      PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)qualifier;
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

  private static class IteratorMethodCallVisitor
    extends JavaRecursiveElementVisitor {

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
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      if (methodCalled) {
        return;
      }
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      String name = methodExpression.getReferenceName();
      if (HardcodedMethodConstants.NEXT.equals(name)) {
        return;
      }
      PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)qualifier;
      PsiElement target = referenceExpression.resolve();
      if (iterator.equals(target)) {
        methodCalled = true;
      }
    }

    public boolean isMethodCalled() {
      return methodCalled;
    }
  }

  private static class VariableOnlyUsedAsIndexVisitor
    extends JavaRecursiveElementVisitor {

    private boolean indexVariableUsedOnlyAsIndex = true;
    private final PsiVariable arrayVariable;
    private final PsiVariable indexVariable;

    VariableOnlyUsedAsIndexVisitor(PsiVariable arrayVariable,
                                   PsiVariable indexVariable) {
      this.arrayVariable = arrayVariable;
      this.indexVariable = indexVariable;
    }

    @Override
    public void visitElement(@Nonnull PsiElement element) {
      if (indexVariableUsedOnlyAsIndex) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitReferenceExpression(
      @Nonnull PsiReferenceExpression reference) {
      if (!indexVariableUsedOnlyAsIndex) {
        return;
      }
      super.visitReferenceExpression(reference);
      PsiElement element = reference.resolve();
      if (!indexVariable.equals(element)) {
        return;
      }
      PsiElement parent = reference.getParent();
      if (!(parent instanceof PsiArrayAccessExpression)) {
        indexVariableUsedOnlyAsIndex = false;
        return;
      }
      PsiArrayAccessExpression arrayAccessExpression =
        (PsiArrayAccessExpression)parent;
      PsiExpression arrayExpression =
        arrayAccessExpression.getArrayExpression();
      if (!(arrayExpression instanceof PsiReferenceExpression)) {
        indexVariableUsedOnlyAsIndex = false;
        return;
      }
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)arrayExpression;
      PsiExpression qualifier =
        referenceExpression.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression)
          && !(qualifier instanceof PsiSuperExpression)) {
        indexVariableUsedOnlyAsIndex = false;
        return;
      }
      PsiElement target = referenceExpression.resolve();
      if (!arrayVariable.equals(target)) {
        indexVariableUsedOnlyAsIndex = false;
        return;
      }
      PsiElement arrayExpressionContext =
        arrayAccessExpression.getParent();
      if (arrayExpressionContext instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression assignment =
          (PsiAssignmentExpression)arrayExpressionContext;
        PsiExpression lhs = assignment.getLExpression();
        if (lhs.equals(arrayAccessExpression)) {
          indexVariableUsedOnlyAsIndex = false;
        }
      }
    }

    public boolean isIndexVariableUsedOnlyAsIndex() {
      return indexVariableUsedOnlyAsIndex;
    }
  }

  private static class VariableOnlyUsedAsListIndexVisitor
    extends JavaRecursiveElementVisitor {

    private boolean indexVariableUsedOnlyAsIndex = true;
    private final PsiVariable indexVariable;
    private final Holder collection;

    VariableOnlyUsedAsListIndexVisitor(
      @Nonnull Holder collection,
      @Nonnull PsiVariable indexVariable) {
      this.collection = collection;
      this.indexVariable = indexVariable;
    }

    @Override
    public void visitElement(@Nonnull PsiElement element) {
      if (indexVariableUsedOnlyAsIndex) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitReferenceExpression(
      @Nonnull PsiReferenceExpression reference) {
      if (!indexVariableUsedOnlyAsIndex) {
        return;
      }
      super.visitReferenceExpression(reference);
      PsiElement element = reference.resolve();
      if (indexVariable.equals(element)) {
        if (!isListIndexExpression(reference)) {
          indexVariableUsedOnlyAsIndex = false;
        }
      }
      else if (collection == Holder.DUMMY) {
        if (isListNonGetMethodCall(reference)) {
          indexVariableUsedOnlyAsIndex = false;
        }
      }
      else if (collection.getVariable().equals(element) &&
               !isListReferenceInIndexExpression(reference)) {
        indexVariableUsedOnlyAsIndex = false;
      }
    }

    public boolean isIndexVariableUsedOnlyAsIndex() {
      return indexVariableUsedOnlyAsIndex;
    }

    private boolean isListNonGetMethodCall(
      PsiReferenceExpression reference) {
      PsiElement parent = reference.getParent();
      if (!(parent instanceof PsiMethodCallExpression)) {
        return false;
      }
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)parent;
      PsiMethod method =
        methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      PsiClass parentClass = PsiTreeUtil.getParentOfType(
        methodCallExpression, PsiClass.class);
      PsiClass containingClass = method.getContainingClass();
      if (!InheritanceUtil.isInheritorOrSelf(parentClass,
                                             containingClass, true)) {
        return false;
      }
      return !isListGetExpression(methodCallExpression);
    }

    private boolean isListIndexExpression(PsiReferenceExpression reference) {
      PsiElement referenceParent = reference.getParent();
      if (!(referenceParent instanceof PsiExpressionList)) {
        return false;
      }
      PsiExpressionList expressionList =
        (PsiExpressionList)referenceParent;
      PsiElement parent = expressionList.getParent();
      if (!(parent instanceof PsiMethodCallExpression)) {
        return false;
      }
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)parent;
      return isListGetExpression(methodCallExpression);
    }

    private boolean isListReferenceInIndexExpression(
      PsiReferenceExpression reference) {
      PsiElement parent = reference.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        return false;
      }
      PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return false;
      }
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)grandParent;
      PsiElement greatGrandParent =
        methodCallExpression.getParent();
      if (greatGrandParent instanceof PsiExpressionStatement) {
        return false;
      }
      return isListGetExpression(methodCallExpression);
    }

    private boolean isListGetExpression(
      PsiMethodCallExpression methodCallExpression) {
      if (methodCallExpression == null) {
        return false;
      }
      PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      PsiExpression qualifierExpression =
        methodExpression.getQualifierExpression();
      if (!(qualifierExpression instanceof PsiReferenceExpression)) {
        if (collection == Holder.DUMMY &&
            (qualifierExpression == null ||
             qualifierExpression instanceof PsiThisExpression ||
             qualifierExpression instanceof PsiSuperExpression)) {
          return expressionIsListGetLookup(methodCallExpression);
        }
        return false;
      }
      PsiReferenceExpression reference =
        (PsiReferenceExpression)qualifierExpression;
      PsiExpression qualifier = reference.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression)
          && !(qualifier instanceof PsiSuperExpression)) {
        return false;
      }
      PsiElement target = reference.resolve();
      if (collection == Holder.DUMMY ||
          !collection.getVariable().equals(target)) {
        return false;
      }
      return expressionIsListGetLookup(methodCallExpression);
    }
  }

  private static class Holder {

    public static final Holder DUMMY = new Holder();

    private final PsiVariable variable;

    public Holder(@Nonnull PsiVariable variable) {
      this.variable = variable;
    }

    private Holder() {
      variable = null;
    }

    public PsiVariable getVariable() {
      return variable;
    }
  }
}
