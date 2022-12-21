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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Danila Ponomarenko
 */
public class ReplaceWithListAccessFix implements SyntheticIntentionAction {
  private final PsiArrayAccessExpression myArrayAccessExpression;

  public ReplaceWithListAccessFix(PsiArrayAccessExpression arrayAccessExpression) {
    myArrayAccessExpression = arrayAccessExpression;
  }

  @Nonnull
  @Override
  public String getText() {
    return JavaQuickFixBundle.message("replace.with.list.access.text");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!myArrayAccessExpression.isValid()) return false;
    if (!TypeConversionUtil.areTypesAssignmentCompatible(PsiType.INT, myArrayAccessExpression.getIndexExpression())) {
      return false;
    }
    final PsiElement parent = myArrayAccessExpression.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      final PsiExpression lExpression = ((PsiAssignmentExpression)parent).getLExpression();
      if (lExpression.equals(myArrayAccessExpression) && !(parent.getParent() instanceof PsiExpressionStatement)) {
        return false;
      }
    }

    final PsiExpression arrayExpression = myArrayAccessExpression.getArrayExpression();
    final PsiType type = arrayExpression.getType();
    final PsiType listType = createUtilListType(project);

    if (type == null || listType == null) return false;


    return listType.isAssignableFrom(type);
  }

  @Nullable
  private PsiType createUtilListType(@Nonnull Project project) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass listClass = JavaPsiFacade.getInstance(project).findClass(JavaClassNames.JAVA_UTIL_LIST, myArrayAccessExpression.getResolveScope());

    if (listClass == null) return null;

    final PsiElementFactory factory = facade.getElementFactory();
    return factory.createType(listClass);
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    final PsiExpression arrayExpression = myArrayAccessExpression.getArrayExpression();
    final PsiExpression indexExpression = myArrayAccessExpression.getIndexExpression();

    if (indexExpression == null) return;

    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    final PsiElement parent = myArrayAccessExpression.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;

      final PsiExpression lExpression = assignmentExpression.getLExpression();
      final PsiExpression rExpression = assignmentExpression.getRExpression();
      if (lExpression.equals(myArrayAccessExpression) && parent.getParent() instanceof PsiExpressionStatement && rExpression != null) {
        replaceWithSet(factory, codeStyleManager, arrayExpression, indexExpression, rExpression, assignmentExpression);
        return;
      }
    }
    replaceWithGet(factory, codeStyleManager, arrayExpression, indexExpression, myArrayAccessExpression);
  }

  @Nonnull
  private static PsiElement replaceWithGet(@Nonnull PsiElementFactory factory,
                                           @Nonnull CodeStyleManager codeStyleManager,
                                           @Nonnull PsiExpression arrayExpression,
                                           @Nonnull PsiExpression indexExpression,
                                           @Nonnull PsiElement anchor) {

    final PsiElement listAccess = factory.createExpressionFromText(
      arrayExpression.getText() + ".get(" + indexExpression.getText() + ")",
      anchor);
    return anchor.replace(codeStyleManager.reformat(listAccess));
  }

  private static PsiElement replaceWithSet(@Nonnull PsiElementFactory factory,
                                           @Nonnull CodeStyleManager codeStyleManager,
                                           @Nonnull PsiExpression arrayExpression,
                                           @Nonnull PsiExpression indexExpression,
                                           @Nonnull PsiExpression expression,
                                           @Nonnull PsiElement anchor) {
    final PsiElement listAccess = factory.createExpressionFromText(
      arrayExpression.getText() + ".set(" + indexExpression.getText() + "," + expression.getText() + ")",
      anchor
    );
    return anchor.replace(codeStyleManager.reformat(listAccess));
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
