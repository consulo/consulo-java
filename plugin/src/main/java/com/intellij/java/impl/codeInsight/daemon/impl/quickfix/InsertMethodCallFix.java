/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;

public class InsertMethodCallFix implements SyntheticIntentionAction, LowPriorityAction {
  private final PsiMethodCallExpression myCall;
  private final String myMethodName;

  public InsertMethodCallFix(PsiMethodCallExpression call, PsiMethod method) {
    myCall = call;
    myMethodName = method.getName();
  }

  @Nls
  @Nonnull
  @Override
  public String getText() {
    return JavaQuickFixBundle.message("insert.sam.method.call.fix.name", myMethodName);
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myCall.isValid() && PsiManager.getInstance(project).isInProject(myCall);
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiExpression methodExpression = myCall.getMethodExpression();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    String replacement = methodExpression.getText() + "." + myMethodName;
    methodExpression.replace(factory.createExpressionFromText(replacement, methodExpression));
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
