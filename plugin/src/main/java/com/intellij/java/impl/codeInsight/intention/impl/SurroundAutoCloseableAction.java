/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.util.function.Processor;
import consulo.codeEditor.Editor;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.util.collection.SmartList;

import javax.annotation.Nonnull;
import java.util.List;

public class SurroundAutoCloseableAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@Nonnull final Project project, final Editor editor, @Nonnull final PsiElement element) {
    if (!element.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return false;
    if (!PsiUtil.getLanguageLevel(element).isAtLeast(LanguageLevel.JDK_1_7)) return false;

    final PsiLocalVariable variable = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class);
    if (variable == null) return false;
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return false;
    final PsiElement declaration = variable.getParent();
    if (!(declaration instanceof PsiDeclarationStatement)) return false;
    final PsiElement codeBlock = declaration.getParent();
    if (!(codeBlock instanceof PsiCodeBlock)) return false;

    final PsiType type = variable.getType();
    if (!(type instanceof PsiClassType)) return false;
    final PsiClass aClass = ((PsiClassType)type).resolve();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass autoCloseable = facade.findClass(JavaClassNames.JAVA_LANG_AUTO_CLOSEABLE, (GlobalSearchScope) ProjectScopes.getLibrariesScope(project));
    if (!InheritanceUtil.isInheritorOrSelf(aClass, autoCloseable, true)) return false;

    return true;
  }

  @Override
  public void invoke(@Nonnull final Project project, final Editor editor, @Nonnull final PsiElement element) throws IncorrectOperationException {
    final PsiLocalVariable variable = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class);
    if (variable == null) return;
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return;
    final PsiElement declaration = variable.getParent();
    if (!(declaration instanceof PsiDeclarationStatement)) return;
    final PsiElement codeBlock = declaration.getParent();
    if (!(codeBlock instanceof PsiCodeBlock)) return;

    final LocalSearchScope scope = new LocalSearchScope(codeBlock);
    PsiElement last = null;
    for (PsiReference reference : ReferencesSearch.search(variable, scope).findAll()) {
      final PsiElement usage = PsiTreeUtil.findPrevParent(codeBlock, reference.getElement());
      if ((last == null || usage.getTextOffset() > last.getTextOffset())) {
        last = usage;
      }
    }

    final String text = "try (" + variable.getTypeElement().getText() + " " + variable.getName() + " = " + initializer.getText() + ") {}";
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiTryStatement armStatement = (PsiTryStatement)declaration.replace(factory.createStatementFromText(text, codeBlock));

    List<PsiElement> toFormat = null;
    if (last != null) {
      final PsiElement first = armStatement.getNextSibling();
      if (first != null) {
        toFormat = moveStatements(first, last, armStatement);
      }
    }

    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    final PsiElement formattedElement = codeStyleManager.reformat(armStatement);
    if (toFormat != null) {
      for (PsiElement psiElement : toFormat) {
        codeStyleManager.reformat(psiElement);
      }
    }

    if (last == null) {
      final PsiCodeBlock tryBlock = ((PsiTryStatement)formattedElement).getTryBlock();
      if (tryBlock != null) {
        final PsiJavaToken brace = tryBlock.getLBrace();
        if (brace != null) {
          editor.getCaretModel().moveToOffset(brace.getTextOffset() + 1);
        }
      }
    }
  }

  private static List<PsiElement> moveStatements(@Nonnull PsiElement first, PsiElement last, PsiTryStatement statement) {
    PsiCodeBlock tryBlock = statement.getTryBlock();
    assert tryBlock != null : statement.getText();
    PsiElement parent = statement.getParent();

    List<PsiElement> toFormat = new SmartList<PsiElement>();
    PsiElement stopAt = last.getNextSibling();
    for (PsiElement child = first; child != null && child != stopAt; child = child.getNextSibling()) {
      if (!(child instanceof PsiDeclarationStatement)) continue;

      PsiElement anchor = child;
      for (PsiElement declared : ((PsiDeclarationStatement)child).getDeclaredElements()) {
        if (!(declared instanceof PsiLocalVariable)) continue;

        final int endOffset = last.getTextRange().getEndOffset();
        boolean contained = ReferencesSearch.search(declared, new LocalSearchScope(parent)).forEach(new Processor<PsiReference>() {
          @Override
          public boolean process(PsiReference reference) {
            return reference.getElement().getTextOffset() <= endOffset;
          }
        });

        if (!contained) {
          PsiLocalVariable var = (PsiLocalVariable)declared;
          PsiElementFactory factory = JavaPsiFacade.getElementFactory(statement.getProject());

          String name = var.getName();
          assert name != null : child.getText();
          toFormat.add(parent.addBefore(factory.createVariableDeclarationStatement(name, var.getType(), null), statement));

          PsiExpression varInit = var.getInitializer();
          assert varInit != null : child.getText();
          String varAssignText = name + " = " + varInit.getText() + ";";
          anchor = parent.addAfter(factory.createStatementFromText(varAssignText, parent), anchor);

          var.delete();
        }
      }

      if (child == last && !child.isValid()) {
        last = anchor;
      }
    }

    tryBlock.addRangeBefore(first, last, tryBlock.getRBrace());
    parent.deleteChildRange(first, last);

    return toFormat;
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.surround.resource.with.ARM.block");
  }

  @Nonnull
  @Override
  public String getText() {
    return getFamilyName();
  }
}
