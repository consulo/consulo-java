/*
 * Copyright 2008-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.fixes;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.application.util.query.Query;
import consulo.content.scope.SearchScope;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public class ExtractParameterAsLocalVariableFix extends InspectionGadgetsFix {
  @Nonnull
  public LocalizeValue getName() {
    return InspectionGadgetsLocalize.extractParameterAsLocalVariableQuickfix();
  }

  @Override
  public void doFix(Project project, ProblemDescriptor descriptor)
    throws IncorrectOperationException {
    PsiReferenceExpression parameterReference = (PsiReferenceExpression)descriptor.getPsiElement();
    PsiElement target = parameterReference.resolve();
    if (!(target instanceof PsiParameter)) {
      return;
    }
    PsiParameter parameter = (PsiParameter)target;
    PsiElement declarationScope = parameter.getDeclarationScope();
    PsiElement body;
    if (declarationScope instanceof PsiMethod method) {
      body = method.getBody();
    }
    else if (declarationScope instanceof PsiCatchSection) {
      PsiCatchSection catchSection = (PsiCatchSection)declarationScope;
      body = catchSection.getCatchBlock();
    }
    else if (declarationScope instanceof PsiLoopStatement) {
      PsiLoopStatement forStatement = (PsiLoopStatement)declarationScope;
      PsiStatement forBody = forStatement.getBody();
      if (forBody instanceof PsiBlockStatement) {
        PsiBlockStatement blockStatement = (PsiBlockStatement)forBody;
        body = blockStatement.getCodeBlock();
      }
      else {
        body = forBody;
      }
    }
    else {
      return;
    }
    if (body == null) {
      return;
    }
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    String parameterName = parameterReference.getText();
    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
    String variableName = javaCodeStyleManager.suggestUniqueVariableName(parameterName, parameterReference, true);
    SearchScope scope = parameter.getUseScope();
    Query<PsiReference> search = ReferencesSearch.search(parameter, scope);
    PsiReference reference = search.findFirst();
    if (reference == null) {
      return;
    }
    PsiElement element = reference.getElement();
    if (!(element instanceof PsiReferenceExpression)) {
      return;
    }
    PsiReferenceExpression firstReference = (PsiReferenceExpression)element;
    PsiElement[] children = body.getChildren();
    int startIndex;
    int endIndex;
    if (body instanceof PsiCodeBlock) {
      startIndex = 1;
      endIndex = children.length - 1;
    }
    else {
      startIndex = 0;
      endIndex = children.length;
    }
    boolean newDeclarationCreated = false;
    StringBuilder buffer = new StringBuilder();
    for (int i = startIndex; i < endIndex; i++) {
      PsiElement child = children[i];
      newDeclarationCreated |= replaceVariableName(child, firstReference, variableName, parameterName, buffer);
    }
    String replacementText;
    if (newDeclarationCreated) {
      replacementText = "{" + buffer + '}';
    }
    else {
      PsiType type = parameterReference.getType();
      if (type == null) {
        return;
      }
      String className = type.getCanonicalText();
      replacementText = '{' + className + ' ' + variableName + " = " + parameterName + ';' + buffer + '}';
    }
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiCodeBlock block = elementFactory.createCodeBlockFromText(replacementText, null);
    body.replace(block);
    codeStyleManager.reformat(declarationScope);
  }

  /**
   * @return true, if a declaration was introduced, false otherwise
   */
  private static boolean replaceVariableName(
    PsiElement element, PsiReferenceExpression firstReference,
    String newName, String originalName, StringBuilder out) {
    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
      if (element.equals(firstReference) && isLeftSideOfSimpleAssignment(referenceExpression)) {
        PsiType type = firstReference.getType();
        if (type != null) {
          out.append(type.getCanonicalText());
          out.append(' ');
          out.append(newName);
          return true;
        }
      }
      String text = element.getText();
      if (text.equals(originalName)) {
        out.append(newName);
        return false;
      }
    }
    PsiElement[] children = element.getChildren();
    if (children.length == 0) {
      String text = element.getText();
      out.append(text);
    }
    else {
      boolean result = false;
      for (PsiElement child : children) {
        if (result) {
          out.append(child.getText());
        }
        else {
          result = replaceVariableName(child, firstReference, newName, originalName, out);
        }
      }
      return result;
    }
    return false;
  }

  private static boolean isLeftSideOfSimpleAssignment(PsiReferenceExpression reference) {
    if (reference == null) {
      return false;
    }
    PsiElement parent = reference.getParent();
    if (!(parent instanceof PsiAssignmentExpression)) {
      return false;
    }
    PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
    IElementType tokenType = assignmentExpression.getOperationTokenType();
    if (!JavaTokenType.EQ.equals(tokenType)) {
      return false;
    }
    PsiExpression lExpression = assignmentExpression.getLExpression();
    if (!reference.equals(lExpression)) {
      return false;
    }
    PsiExpression rExpression = assignmentExpression.getRExpression();
    if (rExpression instanceof PsiAssignmentExpression) {
      return false;
    }
    PsiElement grandParent = parent.getParent();
    return grandParent instanceof PsiExpressionStatement;
  }
}