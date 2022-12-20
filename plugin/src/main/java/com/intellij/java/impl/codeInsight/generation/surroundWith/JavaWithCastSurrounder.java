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
package com.intellij.java.impl.codeInsight.generation.surroundWith;

import java.util.LinkedHashSet;
import java.util.Set;

import org.jetbrains.annotations.NonNls;
import consulo.language.editor.CodeInsightBundle;
import com.intellij.java.analysis.codeInsight.guess.GuessManager;
import consulo.language.editor.completion.lookup.LookupElement;
import com.intellij.java.impl.codeInsight.lookup.PsiTypeLookupItem;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import com.intellij.java.language.impl.codeInsight.template.macro.PsiTypeResult;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateManager;
import consulo.codeEditor.Editor;
import consulo.document.RangeMarker;
import consulo.codeEditor.ScrollType;
import consulo.project.Project;
import consulo.document.util.TextRange;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiConditionalExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.impl.refactoring.introduceField.ElementToWorkOn;
import consulo.language.util.IncorrectOperationException;

public class JavaWithCastSurrounder extends JavaExpressionSurrounder {
  @NonNls private static final String TYPE_TEMPLATE_VARIABLE = "type";

  @Override
  public boolean isApplicable(PsiExpression expr) {
    return true;
  }

  @Override
  public TextRange surroundExpression(final Project project, final Editor editor, PsiExpression expr) throws IncorrectOperationException {
    assert expr.isValid();
    PsiType[] types = GuessManager.getInstance(project).guessTypeToCast(expr);
    final boolean parenthesesNeeded = expr instanceof PsiPolyadicExpression ||
                                      expr instanceof PsiConditionalExpression ||
                                      expr instanceof PsiAssignmentExpression;
    String exprText = parenthesesNeeded ? "(" + expr.getText() + ")" : expr.getText();
    final Template template = generateTemplate(project, exprText, types);
    TextRange range;
    if (expr.isPhysical()) {
      range = expr.getTextRange();
    } else {
      final RangeMarker rangeMarker = expr.getUserData(ElementToWorkOn.TEXT_RANGE);
      if (rangeMarker == null) return null;
      range = new TextRange(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
    }
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    editor.getCaretModel().moveToOffset(range.getStartOffset());
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    TemplateManager.getInstance(project).startTemplate(editor, template);
    return null;
  }

  private static Template generateTemplate(Project project, String exprText, final PsiType[] suggestedTypes) {
    final TemplateManager templateManager = TemplateManager.getInstance(project);
    final Template template = templateManager.createTemplate("", "");
    template.setToReformat(true);

    Set<LookupElement> itemSet = new LinkedHashSet<LookupElement>();
    for (PsiType type : suggestedTypes) {
      itemSet.add(PsiTypeLookupItem.createLookupItem(type, null));
    }
    final LookupElement[] lookupItems = itemSet.toArray(new LookupElement[itemSet.size()]);

    final Result result = suggestedTypes.length > 0 ? new PsiTypeResult(suggestedTypes[0], project) : null;

    Expression expr = new Expression() {
      @Override
      public LookupElement[] calculateLookupItems(ExpressionContext context) {
        return lookupItems.length > 1 ? lookupItems : null;
      }

      @Override
      public Result calculateResult(ExpressionContext context) {
        return result;
      }

      @Override
      public Result calculateQuickResult(ExpressionContext context) {
        return null;
      }
    };
    template.addTextSegment("((");
    template.addVariable(TYPE_TEMPLATE_VARIABLE, expr, expr, true);
    template.addTextSegment(")" + exprText + ")");
    template.addEndVariable();

    return template;
  }

  @Override
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.cast.template");
  }
}
