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

import com.intellij.java.language.JavaLanguage;
import consulo.ide.impl.idea.codeInsight.CodeInsightUtilBase;
import consulo.language.Language;
import consulo.language.editor.template.EmptyExpression;
import consulo.language.editor.template.Template;
import consulo.language.editor.impl.internal.template.TemplateBuilderImpl;
import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.refactoring.introduceField.BaseExpressionToFieldHandler;
import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;

import javax.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
public class JavaCreateFieldFromUsageHelper implements CreateFieldFromUsageHelper {

  @Override
  public Template setupTemplateImpl(PsiField field,
                                    Object expectedTypes,
                                    PsiClass targetClass,
                                    Editor editor,
                                    PsiElement context,
                                    boolean createConstantField,
                                    PsiSubstitutor substitutor) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(field.getProject());

    field = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(field);
    TemplateBuilderImpl builder = new TemplateBuilderImpl(field);
    if (!(expectedTypes instanceof ExpectedTypeInfo[])) {
      expectedTypes = ExpectedTypeInfo.EMPTY_ARRAY;
    }
    new GuessTypeParameters(factory).setupTypeElement(field.getTypeElement(), (ExpectedTypeInfo[]) expectedTypes, substitutor, builder,
        context, targetClass);

    if (createConstantField) {
      field.setInitializer(factory.createExpressionFromText("0", null));
      builder.replaceElement(field.getInitializer(), new EmptyExpression());
      PsiIdentifier identifier = field.getNameIdentifier();
      builder.setEndVariableAfter(identifier);
      field = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(field);
    }
    editor.getCaretModel().moveToOffset(field.getTextRange().getStartOffset());
    Template template = builder.buildInlineTemplate();
    if (((ExpectedTypeInfo[]) expectedTypes).length > 1) template.setToShortenLongNames(false);
    return template;
  }

  @Override
  public PsiField insertFieldImpl(@Nonnull PsiClass targetClass, @Nonnull PsiField field, @Nonnull PsiElement place) {
    PsiMember enclosingContext = null;
    PsiClass parentClass;
    do {
      enclosingContext = PsiTreeUtil.getParentOfType(enclosingContext == null ? place : enclosingContext, PsiMethod.class, PsiField.class, PsiClassInitializer.class);
      parentClass = enclosingContext == null ? null : enclosingContext.getContainingClass();
    }
    while (parentClass instanceof PsiAnonymousClass);

    return BaseExpressionToFieldHandler.ConvertToFieldRunnable.appendField(targetClass, field, enclosingContext, null);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
