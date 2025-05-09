/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

/**
 * @author Dmitry Batkovich
 */
public class SurroundWithQuotesAnnotationParameterValueFix implements SyntheticIntentionAction {
  private final PsiAnnotationMemberValue myValue;
  private final PsiType myExpectedType;

  public SurroundWithQuotesAnnotationParameterValueFix(final PsiAnnotationMemberValue value,
                                                       final PsiType expectedType) {
    myValue = value;
    myExpectedType = expectedType;
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!myValue.isValid() || !myExpectedType.isValid() || !(myExpectedType instanceof PsiClassType)) {
      return false;
    }
    final PsiClass resolvedType = ((PsiClassType)myExpectedType).resolve();
    return resolvedType != null && CommonClassNames.JAVA_LANG_STRING.equals(resolvedType.getQualifiedName()) &&
      myValue instanceof PsiLiteralExpression;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    String newText = myValue.getText();
    newText = StringUtil.stripQuotesAroundValue(newText);
    newText = "\"" + newText + "\"";
    PsiElement newToken = JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(newText,
                                                                                                          null);
    final PsiElement newElement = myValue.replace(newToken);
    editor.getCaretModel().moveToOffset(newElement.getTextOffset() + newElement.getTextLength());
  }

  @Nonnull
  @Override
  public String getText() {
    return "Surround annotation parameter value with quotes";
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
