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
import com.siyeh.ig.psiutils.ParenthesesUtils;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author Pavel.Dolgov
 */
public class ConvertCollectionToArrayFix implements SyntheticIntentionAction {
  private final PsiExpression myCollectionExpression;
  private final String myNewArrayText;

  public ConvertCollectionToArrayFix(@Nonnull PsiExpression collectionExpression, @Nonnull PsiArrayType arrayType) {
    myCollectionExpression = collectionExpression;

    PsiType componentType = arrayType.getComponentType();
    myNewArrayText = componentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ? "" : "new " + getArrayTypeText(componentType);
  }

  @Nonnull
  @Override
  public LocalizeValue getText() {
    return JavaQuickFixLocalize.collectionToArrayText(myNewArrayText);
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myCollectionExpression.isValid() && PsiManager.getInstance(project).isInProject(myCollectionExpression);
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    String replacement =
      ParenthesesUtils.getText(myCollectionExpression, ParenthesesUtils.POSTFIX_PRECEDENCE) + ".toArray(" + myNewArrayText + ")";
    myCollectionExpression.replace(factory.createExpressionFromText(replacement, myCollectionExpression));
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Nonnull
  private static String getArrayTypeText(PsiType componentType) {
    if (componentType instanceof PsiArrayType) {
      return getArrayTypeText(((PsiArrayType)componentType).getComponentType()) + "[]";
    }
    if (componentType instanceof PsiClassType) {
      return ((PsiClassType)componentType).rawType().getCanonicalText() + "[0]";
    }
    return componentType.getCanonicalText() + "[0]";
  }
}
