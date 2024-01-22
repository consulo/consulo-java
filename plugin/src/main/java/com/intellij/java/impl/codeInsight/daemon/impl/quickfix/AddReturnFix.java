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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class AddReturnFix implements SyntheticIntentionAction {
  private static final Logger LOG = Logger.getInstance(AddReturnFix.class);
  private final PsiMethod myMethod;

  public AddReturnFix(PsiMethod method) {
    myMethod = method;
  }

  @Override
  @Nonnull
  public String getText() {
    return JavaQuickFixBundle.message("add.return.statement.text");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myMethod != null
        && myMethod.isValid()
        && myMethod.getManager().isInProject(myMethod)
        && myMethod.getBody() != null
        && myMethod.getBody().getRBrace() != null
        ;
  }

  @Override
  public void invoke(@jakarta.annotation.Nonnull Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(myMethod.getContainingFile())) return;

    try {
      String value = suggestReturnValue();
      PsiElementFactory factory = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory();
      PsiReturnStatement returnStatement = (PsiReturnStatement) factory.createStatementFromText("return " + value+";", myMethod);
      PsiCodeBlock body = myMethod.getBody();
      returnStatement = (PsiReturnStatement) body.addBefore(returnStatement, body.getRBrace());

      MethodReturnTypeFix.selectReturnValueInEditor(returnStatement, editor);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private String suggestReturnValue() {
    PsiType type = myMethod.getReturnType();
    // first try to find suitable local variable
    PsiVariable[] variables = getDeclaredVariables(myMethod);
    for (PsiVariable variable : variables) {
      PsiType varType = variable.getType();
      if (varType.equals(type)) {
        return variable.getName();
      }
    }
    return PsiTypesUtil.getDefaultValueOfType(type);
  }

  private static PsiVariable[] getDeclaredVariables(PsiMethod method) {
    List<PsiVariable> variables = new ArrayList<PsiVariable>();
    PsiStatement[] statements = method.getBody().getStatements();
    for (PsiStatement statement : statements) {
      if (statement instanceof PsiDeclarationStatement) {
        PsiElement[] declaredElements = ((PsiDeclarationStatement)statement).getDeclaredElements();
        for (PsiElement declaredElement : declaredElements) {
          if (declaredElement instanceof PsiLocalVariable) variables.add((PsiVariable)declaredElement);
        }
      }
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    ContainerUtil.addAll(variables, parameters);
    return variables.toArray(new PsiVariable[variables.size()]);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

}
