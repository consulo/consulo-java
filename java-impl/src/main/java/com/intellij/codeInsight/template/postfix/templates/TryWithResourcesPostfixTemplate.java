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
package com.intellij.codeInsight.template.postfix.templates;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.codeInsight.template.macro.SuggestVariableNameMacro;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import consulo.java.module.util.JavaClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;


public class TryWithResourcesPostfixTemplate extends PostfixTemplate {
  protected TryWithResourcesPostfixTemplate() {
    super("twr", "try(Type f = new Type()) {} catch (Ex e) {}");
  }

  @Override
  public boolean isApplicable(@Nonnull PsiElement element, @Nonnull Document copyDocument, int newOffset) {
    if (!PsiUtil.isLanguageLevel7OrHigher(element)) return false;

    PsiExpression initializer = JavaPostfixTemplatesUtils.getTopmostExpression(element);

    if (initializer == null) return false;

    final PsiType type = initializer.getType();
    if (!(type instanceof PsiClassType)) return false;
    final PsiClass aClass = ((PsiClassType)type).resolve();
    Project project = element.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass autoCloseable = facade.findClass(JavaClassNames.JAVA_LANG_AUTO_CLOSEABLE, ProjectScope.getLibrariesScope(project));
    if (!InheritanceUtil.isInheritorOrSelf(aClass, autoCloseable, true)) return false;

    return true;
  }

  @Override
  public void expand(@Nonnull PsiElement context, @Nonnull Editor editor) {
    PsiExpression expression = JavaPostfixTemplatesUtils.getTopmostExpression(context);
    assert expression != null;

    Project project = context.getProject();

    editor.getDocument().deleteString(expression.getTextRange().getStartOffset(), expression.getTextRange().getEndOffset());

    TemplateManager manager = TemplateManager.getInstance(project);
    Template template = manager.createTemplate("", "");
    template.setToReformat(true);
    template.addTextSegment("try (");
    MacroCallNode name = new MacroCallNode(new SuggestVariableNameMacro());

    template.addVariable("type", new TypeExpression(project, new PsiType[]{expression.getType()}), false);
    template.addTextSegment(" ");
    template.addVariable("name", name, name, true);
    template.addTextSegment(" = ");
    template.addVariable("variable", new TextExpression(expression.getText()), false);
    template.addTextSegment(") {\n");
    template.addEndVariable();
    template.addTextSegment("\n}");

    Collection<PsiClassType> unhandled = getUnhandled(expression);
    for (PsiClassType exception : unhandled) {
      MacroCallNode variable = new MacroCallNode(new SuggestVariableNameMacro());
      template.addTextSegment("catch(");
      template.addVariable("type " + exception.getClassName(), new TypeExpression(project, new PsiType[]{exception}), false);
      template.addTextSegment(" ");
      template.addVariable("name " + exception.getClassName(), variable, variable, false);
      template.addTextSegment(") {}");
    }

    manager.startTemplate(editor, template);
  }

  @Nonnull
  private static Collection<PsiClassType> getUnhandled(@Nonnull PsiExpression expression) {
    assert expression.getType() != null;
    PsiMethod methodCloser = PsiUtil.getResourceCloserMethodForType((PsiClassType)expression.getType());
    PsiSubstitutor substitutor = PsiUtil.resolveGenericsClassInType(expression.getType()).getSubstitutor();

    return methodCloser != null
           ? ExceptionUtil.getUnhandledExceptions(methodCloser, expression, null, substitutor)
           : Collections.<PsiClassType>emptyList();
  }
}
