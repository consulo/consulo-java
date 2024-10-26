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

import com.intellij.java.impl.codeInsight.template.macro.IterableComponentTypeMacro;
import com.intellij.java.impl.codeInsight.template.macro.SuggestVariableNameMacro;
import com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.java.language.psi.PsiExpression;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.editor.postfixTemplate.PostfixTemplate;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateManager;
import consulo.language.editor.template.TextExpression;
import consulo.language.editor.template.VariableNode;
import consulo.language.editor.template.macro.MacroCallNode;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public class ForeachPostfixTemplate extends PostfixTemplate {
  public ForeachPostfixTemplate() {
    super("for", "for (T item : collection)");
  }

  @Override
  public boolean isApplicable(@Nonnull PsiElement context, @Nonnull Document copyDocument, int newOffset) {
    PsiExpression expr = JavaPostfixTemplatesUtils.getTopmostExpression(context);
    return expr != null && (JavaPostfixTemplatesUtils.isArray(expr.getType()) || JavaPostfixTemplatesUtils.isIterable(expr.getType()));
  }

  @Override
  public void expand(@Nonnull PsiElement context, @Nonnull Editor editor) {
    PsiExpression expr = JavaPostfixTemplatesUtils.getTopmostExpression(context);
    if (expr == null) return;
    Project project = context.getProject();

    Document document = editor.getDocument();
    document.deleteString(expr.getTextRange().getStartOffset(), expr.getTextRange().getEndOffset());
    TemplateManager manager = TemplateManager.getInstance(project);

    Template template = manager.createTemplate("", "");
    template.setToReformat(true);
    template.addTextSegment("for (");
    MacroCallNode type = new MacroCallNode(new IterableComponentTypeMacro());

    String variable = "variable";
    type.addParameter(new VariableNode(variable, null));
    MacroCallNode name = new MacroCallNode(new SuggestVariableNameMacro());

    template.addVariable("type", type, type, false);
    template.addTextSegment(" ");
    template.addVariable("name", name, name, true);

    template.addTextSegment(" : ");
    template.addVariable(variable, new TextExpression(expr.getText()), false);
    template.addTextSegment(") {\n");
    template.addEndVariable();
    template.addTextSegment("\n}");

    manager.startTemplate(editor, template);
  }
}