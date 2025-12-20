/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.ig.visibility;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class AnonymousClassVariableHidesOuterClassVariableVisitor
  extends BaseInspectionVisitor {

  @Override
  public void visitAnonymousClass(PsiAnonymousClass aClass) {
    super.visitAnonymousClass(aClass);
    PsiCodeBlock codeBlock =
      PsiTreeUtil.getParentOfType(aClass, PsiCodeBlock.class);
    if (codeBlock == null) {
      return;
    }
    VariableCollector collector = new VariableCollector();
    aClass.acceptChildren(collector);
    PsiStatement[] statements = codeBlock.getStatements();
    int offset = aClass.getTextOffset();
    for (PsiStatement statement : statements) {
      if (statement.getTextOffset() >= offset) {
        break;
      }
      if (!(statement instanceof PsiDeclarationStatement)) {
        continue;
      }
      PsiDeclarationStatement declarationStatement =
        (PsiDeclarationStatement)statement;
      PsiElement[] declaredElements =
        declarationStatement.getDeclaredElements();
      for (PsiElement declaredElement : declaredElements) {
        if (!(declaredElement instanceof PsiLocalVariable)) {
          continue;
        }
        PsiLocalVariable localVariable =
          (PsiLocalVariable)declaredElement;
        String name = localVariable.getName();
        PsiVariable[] variables =
          collector.getVariables(name);
        for (PsiVariable variable : variables) {
          registerVariableError(variable, variable);
        }
      }
    }
    PsiMethod containingMethod =
      PsiTreeUtil.getParentOfType(codeBlock, PsiMethod.class);
    if (containingMethod == null) {
      return;
    }
    PsiParameterList parameterList =
      containingMethod.getParameterList();
    PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      String name = parameter.getName();
      PsiVariable[] variables = collector.getVariables(name);
      for (PsiVariable variable : variables) {
        registerVariableError(variable, variable);
      }
    }
  }

  private static class VariableCollector extends JavaRecursiveElementVisitor {

    private static final PsiVariable[] EMPTY_VARIABLE_LIST = {};

    private final Map<String, List<PsiVariable>> variableMap = new HashMap();

    @Override
    public void visitVariable(PsiVariable variable) {
      super.visitVariable(variable);
      String name = variable.getName();
      List<PsiVariable> variableList = variableMap.get(name);
      if (variableList == null) {
        List<PsiVariable> list = new ArrayList();
        list.add(variable);
        variableMap.put(name, list);
      }
      else {
        variableList.add(variable);
      }
    }

    @Override
    public void visitClass(PsiClass aClass) {
      // don't drill down in classes
    }

    public PsiVariable[] getVariables(String name) {
      List<PsiVariable> variableList = variableMap.get(name);
      if (variableList == null) {
        return EMPTY_VARIABLE_LIST;
      }
      else {
        return variableList.toArray(
          new PsiVariable[variableList.size()]);
      }
    }
  }
}
