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
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

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
    PsiType type = getType(myInitializer);
    return JavaQuickFixBundle.message("add.new.array.text", type.getPresentableText());
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@Nonnull PsiFile currentFile) {
    return myInitializer;
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!myInitializer.isValid() || !myInitializer.getManager().isInProject(myInitializer)) return false;
    return getType(myInitializer) != null;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    doFix(myInitializer);
  }

  public static void doFix(@Nonnull PsiArrayInitializerExpression initializer) {
    PsiType type = getType(initializer);
    if (type == null) {
      return;
    }

    doFix(type, initializer);
  }

  private static void doFix(@Nonnull PsiType type, PsiArrayInitializerExpression initializer) {
    Project project = initializer.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    String text = "new " + type.getPresentableText() + "[]{}";
    PsiNewExpression newExpr = (PsiNewExpression)factory.createExpressionFromText(text, null);
    newExpr.getArrayInitializer().replace(initializer);
    newExpr = (PsiNewExpression)CodeStyleManager.getInstance(project).reformat(newExpr);
    initializer.replace(newExpr);
  }

  @RequiredReadAction
  private static PsiType getType(PsiArrayInitializerExpression initializer) {
    final PsiExpression[] initializers = initializer.getInitializers();
    final PsiElement parent = initializer.getParent();
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
