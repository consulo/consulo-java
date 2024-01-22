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
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

/**
 * @author ven
 */
public class AddNewArrayExpressionFix implements SyntheticIntentionAction {
  private final PsiArrayInitializerExpression myInitializer;

  public AddNewArrayExpressionFix(PsiArrayInitializerExpression initializer) {
    myInitializer = initializer;
  }

  @Override
  @Nonnull
  public String getText() {
    PsiType type = getType();
    return JavaQuickFixBundle.message("add.new.array.text", type.getPresentableText());
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!myInitializer.isValid() || !myInitializer.getManager().isInProject(myInitializer)) return false;
    return getType() != null;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(myInitializer, file)) return;
    PsiManager manager = file.getManager();
    PsiType type = getType();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    @NonNls String text = "new " + type.getPresentableText() + "[]{}";
    PsiNewExpression newExpr = (PsiNewExpression) factory.createExpressionFromText(text, null);
    newExpr.getArrayInitializer().replace(myInitializer);
    newExpr = (PsiNewExpression) CodeStyleManager.getInstance(manager.getProject()).reformat(newExpr);
    myInitializer.replace(newExpr);
  }

  private PsiType getType() {
    final PsiExpression[] initializers = myInitializer.getInitializers();
    final PsiElement parent = myInitializer.getParent();
    if (!(parent instanceof PsiAssignmentExpression)) {
      if (initializers.length <= 0) return null;
      return initializers[0].getType();
    }
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
    final PsiType type = assignmentExpression.getType();
    if (!(type instanceof PsiArrayType)) {
      if (initializers.length <= 0) return null;
      return initializers[0].getType();
    }
    return ((PsiArrayType)type).getComponentType();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
