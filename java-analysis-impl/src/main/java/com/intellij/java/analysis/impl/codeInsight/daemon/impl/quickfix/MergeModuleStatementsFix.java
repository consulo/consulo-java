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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import consulo.codeEditor.Editor;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Pavel.Dolgov
 */
public abstract class MergeModuleStatementsFix<T extends PsiElement> extends LocalQuickFixAndIntentionActionOnPsiElement {

  protected MergeModuleStatementsFix(@jakarta.annotation.Nonnull PsiJavaModule javaModule) {
    super(javaModule);
  }

  @Override
  public boolean isAvailable(@jakarta.annotation.Nonnull Project project, @jakarta.annotation.Nonnull PsiFile file, @jakarta.annotation.Nonnull PsiElement startElement, @jakarta.annotation.Nonnull PsiElement endElement) {
    return PsiUtil.isLanguageLevel9OrHigher(file);
  }

  @Override
  public void invoke(@jakarta.annotation.Nonnull Project project, @jakarta.annotation.Nonnull PsiFile file, @jakarta.annotation.Nullable Editor editor, @Nonnull PsiElement startElement, @jakarta.annotation.Nonnull PsiElement endElement) {
    if (startElement instanceof PsiJavaModule) {
      final PsiJavaModule javaModule = (PsiJavaModule) startElement;
      final List<T> statementsToMerge = getStatementsToMerge(javaModule);
      LOG.assertTrue(!statementsToMerge.isEmpty());

      final String tempModuleText = PsiKeyword.MODULE + " " + javaModule.getName() + " {" + getReplacementText(statementsToMerge) + "}";
      final PsiJavaModule tempModule = JavaPsiFacade.getInstance(project).getElementFactory().createModuleFromText(tempModuleText);

      final List<T> tempStatements = getStatementsToMerge(tempModule);
      LOG.assertTrue(!tempStatements.isEmpty());
      final T replacement = tempStatements.get(0);

      final T firstStatement = statementsToMerge.get(0);
      final CommentTracker commentTracker = new CommentTracker();
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      final PsiElement resultingStatement = codeStyleManager.reformat(commentTracker.replace(firstStatement, replacement));

      for (int i = 1; i < statementsToMerge.size(); i++) {
        T statement = statementsToMerge.get(i);
        commentTracker.delete(statement);
      }
      commentTracker.insertCommentsBefore(resultingStatement);

      if (editor != null) {
        final int offset = resultingStatement.getTextRange().getEndOffset();
        editor.getCaretModel().moveToOffset(offset);
      }
    }
  }

  @jakarta.annotation.Nonnull
  protected abstract String getReplacementText(List<T> statementsToMerge);

  @Nonnull
  protected abstract List<T> getStatementsToMerge(@jakarta.annotation.Nonnull PsiJavaModule javaModule);

  @jakarta.annotation.Nonnull
  protected static String joinUniqueNames(@jakarta.annotation.Nonnull List<String> names) {
    final Set<String> unique = new HashSet<>();
    return names.stream().filter(name -> unique.add(name)).collect(Collectors.joining(","));
  }

  @jakarta.annotation.Nullable
  public static MergeModuleStatementsFix createFix(@Nullable PsiElement statement) {
    if (statement instanceof PsiPackageAccessibilityStatement) {
      return MergePackageAccessibilityStatementsFix.createFix((PsiPackageAccessibilityStatement) statement);
    } else if (statement instanceof PsiProvidesStatement) {
      return MergeProvidesStatementsFix.createFix((PsiProvidesStatement) statement);
    }
    return null;
  }
}
