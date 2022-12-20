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
package com.intellij.java.impl.codeInsight.template.macro;

import com.intellij.java.language.impl.codeInsight.template.macro.MacroUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
@ExtensionImpl
public class IterableVariableMacro extends VariableTypeMacroBase {
  private static final Logger LOG = Logger.getInstance(IterableVariableMacro.class);

  @Override
  public String getName() {
    return "iterableVariable";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightBundle.message("macro.iterable.variable");
  }

  @Override
  @Nullable
  protected PsiElement[] getVariables(Expression[] params, final ExpressionContext context) {
    if (params.length != 0) {
      return null;
    }

    final List<PsiElement> result = new ArrayList<>();


    Project project = context.getProject();
    final int offset = context.getStartOffset();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    assert file != null;
    PsiElement place = file.findElementAt(offset);
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    final GlobalSearchScope scope = file.getResolveScope();

    PsiType iterableType = elementFactory.createTypeByFQClassName("java.lang.Iterable", scope);
    PsiType mapType = elementFactory.createTypeByFQClassName(JavaClassNames.JAVA_UTIL_MAP, scope);

    PsiVariable[] variables = MacroUtil.getVariablesVisibleAt(place, "");
    for (PsiVariable var : variables) {
      final PsiElement parent = var.getParent();
      if (parent instanceof PsiForeachStatement && parent == PsiTreeUtil.getParentOfType(place, PsiForeachStatement.class)) {
        continue;
      }

      PsiType type = VariableTypeCalculator.getVarTypeAt(var, place);
      if (type instanceof PsiArrayType || iterableType.isAssignableFrom(type)) {
        result.add(var);
      } else if (mapType.isAssignableFrom(type)) {
        try {
          result.add(elementFactory.createExpressionFromText(var.getName() + ".keySet()", var.getParent()));
          result.add(elementFactory.createExpressionFromText(var.getName() + ".values()", var.getParent()));
          result.add(elementFactory.createExpressionFromText(var.getName() + ".entrySet()", var.getParent()));
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
    return PsiUtilCore.toPsiElementArray(result);
  }
}
