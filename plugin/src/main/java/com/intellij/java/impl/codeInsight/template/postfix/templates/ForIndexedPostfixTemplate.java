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

import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateManager;
import consulo.language.editor.template.macro.MacroCallNode;
import consulo.language.editor.postfixTemplate.PostfixTemplate;
import consulo.language.editor.postfixTemplate.PostfixTemplatesUtils;
import com.intellij.java.impl.codeInsight.template.macro.SuggestVariableNameMacro;
import com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class ForIndexedPostfixTemplate extends PostfixTemplate {
  protected ForIndexedPostfixTemplate(@Nonnull String key, @Nonnull String example) {
    super(key, example);
  }

  @Override
  public boolean isApplicable(@Nonnull PsiElement context, @Nonnull Document copyDocument, int newOffset) {
    PsiExpression expr = JavaPostfixTemplatesUtils.getTopmostExpression(context);
    return expr != null && (JavaPostfixTemplatesUtils.isNumber(expr.getType()) ||
        JavaPostfixTemplatesUtils.isArray(expr.getType()) ||
        JavaPostfixTemplatesUtils.isIterable(expr.getType()));
  }

  @Override
  public void expand(@Nonnull PsiElement context, @Nonnull Editor editor) {
    PsiExpression expr = JavaPostfixTemplatesUtils.getTopmostExpression(context);
    if (expr == null) {
      PostfixTemplatesUtils.showErrorHint(context.getProject(), editor);
      return;
    }

    Pair<String, String> bounds = calculateBounds(expr);
    if (bounds == null) {
      PostfixTemplatesUtils.showErrorHint(context.getProject(), editor);
      return;
    }
    Project project = context.getProject();

    Document document = editor.getDocument();
    document.deleteString(expr.getTextRange().getStartOffset(), expr.getTextRange().getEndOffset());
    TemplateManager manager = TemplateManager.getInstance(project);

    Template template = manager.createTemplate("", "");
    template.setToReformat(true);
    template.addTextSegment("for (" + suggestIndexType(expr) + " ");
    MacroCallNode index = new MacroCallNode(new SuggestVariableNameMacro());
    String indexVariable = "index";
    template.addVariable(indexVariable, index, index, true);
    template.addTextSegment(" = " + bounds.first + "; ");
    template.addVariableSegment(indexVariable);
    template.addTextSegment(getComparativeSign(expr));
    template.addTextSegment(bounds.second);
    template.addTextSegment("; ");
    template.addVariableSegment(indexVariable);
    template.addTextSegment(getOperator());
    template.addTextSegment(") {\n");
    template.addEndVariable();
    template.addTextSegment("\n}");

    manager.startTemplate(editor, template);
  }

  @Nonnull
  protected abstract String getComparativeSign(@Nonnull PsiExpression expr);

  @Nullable
  protected abstract Pair<String, String> calculateBounds(@Nonnull PsiExpression expression);

  @Nonnull
  protected abstract String getOperator();

  @Nullable
  protected static String getExpressionBound(@Nonnull PsiExpression expr) {
    PsiType type = expr.getType();
    if (JavaPostfixTemplatesUtils.isNumber(type)) {
      return expr.getText();
    } else if (JavaPostfixTemplatesUtils.isArray(type)) {
      return expr.getText() + ".length";
    } else if (JavaPostfixTemplatesUtils.isIterable(type)) {
      return expr.getText() + ".size()";
    }
    return null;
  }

  @Nonnull
  private static String suggestIndexType(@Nonnull PsiExpression expr) {
    PsiType type = expr.getType();
    if (JavaPostfixTemplatesUtils.isNumber(type)) {
      return type.getCanonicalText();
    }
    return "int";
  }
}