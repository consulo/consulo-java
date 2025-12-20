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
package com.intellij.refactoring;

import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.content.bundle.Sdk;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

/**
 * User: anna
 */
public abstract class AbstractJavaInplaceIntroduceTest extends AbstractInplaceIntroduceTest {

  @Nullable
  protected static PsiExpression getExpressionFromEditor() {
    PsiExpression expression = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
    if (expression instanceof PsiReferenceExpression && expression.getParent() instanceof PsiMethodCallExpression) {
      return (PsiExpression)expression.getParent();
    }
    return expression;
  }

  protected static PsiLocalVariable getLocalVariableFromEditor() {
    PsiLocalVariable localVariable = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()),
                                                                       PsiLocalVariable.class);
    assertNotNull(localVariable);
    return localVariable;
  }

  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17();
  }

  @Override
  protected String getExtension() {
    return ".java";
  }

  protected abstract MyIntroduceHandler createIntroduceHandler();

  @Override
  protected AbstractInplaceIntroducer invokeRefactoring() {
    MyIntroduceHandler introduceHandler = createIntroduceHandler();
    PsiExpression expression = getExpressionFromEditor();
    if (expression != null) {
      introduceHandler.invokeImpl(LightPlatformTestCase.getProject(), expression, getEditor());
    } else {
      PsiLocalVariable localVariable = getLocalVariableFromEditor();
      introduceHandler.invokeImpl(LightPlatformTestCase.getProject(), localVariable, getEditor());
    }
    return introduceHandler.getInplaceIntroducer();
  }

  public interface MyIntroduceHandler {
    boolean invokeImpl(Project project, @Nonnull PsiExpression selectedExpr, Editor editor);
    boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor);
    AbstractInplaceIntroducer getInplaceIntroducer();
  }
}
