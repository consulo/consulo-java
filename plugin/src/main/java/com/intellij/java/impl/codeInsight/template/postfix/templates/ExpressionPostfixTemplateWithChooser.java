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
package com.intellij.java.impl.codeInsight.template.postfix.templates;

import consulo.ide.impl.idea.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import consulo.language.editor.postfixTemplate.PostfixTemplate;
import consulo.language.editor.postfixTemplate.PostfixTemplatesUtils;
import consulo.language.editor.refactoring.unwrap.ScopeHighlighter;
import com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import consulo.application.ApplicationManager;
import consulo.undoRedo.CommandProcessor;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.util.lang.function.Condition;
import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.IntroduceTargetChooser;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author ignatov
 */
public abstract class ExpressionPostfixTemplateWithChooser extends PostfixTemplate {
  protected ExpressionPostfixTemplateWithChooser(@Nonnull String name, @Nonnull String example) {
    super(name, example);
  }

  protected ExpressionPostfixTemplateWithChooser(@Nonnull String name,
                                                 @Nonnull String key,
                                                 @Nonnull String example) {
    super(name, key, example);
  }

  @Override
  public boolean isApplicable(@Nonnull PsiElement context, @Nonnull Document copyDocument, int newOffset) {
    return !getExpressions(context, copyDocument, newOffset).isEmpty();
  }

  @Override
  public void expand(@Nonnull PsiElement context, @Nonnull final Editor editor) {
    List<PsiExpression> expressions = getExpressions(context, editor.getDocument(), editor.getCaretModel().getOffset());

    if (expressions.isEmpty()) {
      PostfixTemplatesUtils.showErrorHint(context.getProject(), editor);
    } else if (expressions.size() == 1) {
      doIt(editor, expressions.get(0));
    } else {
      IntroduceTargetChooser.showChooser(
          editor, expressions,
          new Consumer<PsiExpression>() {
            public void accept(@Nonnull final PsiExpression e) {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  CommandProcessor.getInstance().executeCommand(e.getProject(), new Runnable() {
                    public void run() {
                      doIt(editor, e);
                    }
                  }, "Expand postfix template", PostfixLiveTemplate.POSTFIX_TEMPLATE_ID);
                }
              });
            }
          },
          new PsiExpressionTrimRenderer.RenderFunction(),
          "Expressions", 0, ScopeHighlighter.NATURAL_RANGER
      );
    }
  }

  @Nonnull
  protected List<PsiExpression> getExpressions(@jakarta.annotation.Nonnull PsiElement context, @Nonnull Document document, final int offset) {
    List<PsiExpression> expressions = ContainerUtil.filter(IntroduceVariableBase.collectExpressions(context.getContainingFile(), document,
        Math.max(offset - 1, 0), false),
        new Condition<PsiExpression>() {
          @Override
          public boolean value(PsiExpression expression) {
            return expression.getTextRange().getEndOffset() == offset;
          }
        }
    );
    return ContainerUtil.filter(expressions.isEmpty() ? maybeTopmostExpression(context) : expressions, getTypeCondition());
  }

  @Nonnull
  @SuppressWarnings("unchecked")
  protected Condition<PsiExpression> getTypeCondition() {
    return Condition.TRUE;
  }

  @Nonnull
  private static List<PsiExpression> maybeTopmostExpression(@jakarta.annotation.Nonnull PsiElement context) {
    PsiExpression expression = JavaPostfixTemplatesUtils.getTopmostExpression(context);
    PsiType type = expression != null ? expression.getType() : null;
    if (type == null || PsiType.VOID.equals(type)) return List.of();
    return ContainerUtil.createMaybeSingletonList(expression);
  }

  protected abstract void doIt(@Nonnull Editor editor, @Nonnull PsiExpression expression);
}
