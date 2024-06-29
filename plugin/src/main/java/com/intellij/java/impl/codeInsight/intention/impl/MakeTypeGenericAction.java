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
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;

/**
 *  @author dsl
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.MakeTypeGenericAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class MakeTypeGenericAction extends PsiElementBaseIntentionAction {
  private String variableName;
  private String newTypeName;

  @Override
  @Nonnull
  public String getText() {
    return variableName == null || newTypeName == null
      ? CodeInsightLocalize.intentionMakeTypeGenericFamily().get()
      : CodeInsightLocalize.intentionMakeTypeGenericText(variableName, newTypeName).get();
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    return PsiUtil.isLanguageLevel5OrHigher(element) && element.isWritable() && findVariable(element) != null;
  }

  @RequiredReadAction
  private Pair<PsiVariable,PsiType> findVariable(final PsiElement element) {
    PsiVariable variable = null;
    PsiElement elementParent = element.getParent();
    if (element instanceof PsiIdentifier) {
      if (elementParent instanceof PsiVariable psiVariable) {
        variable = psiVariable;
      }
    }
    else if (element instanceof PsiJavaToken token) {
      if (token.getTokenType() != JavaTokenType.EQ) return null;
      if (elementParent instanceof PsiVariable psiVariable) {
        variable = psiVariable;
      }
    }
    if (variable == null) return null;
    variableName = variable.getName();
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return null;
    final PsiType variableType = variable.getType();
    final PsiType initializerType = initializer.getType();
    if (!(variableType instanceof PsiClassType)) return null;
    final PsiClassType variableClassType = (PsiClassType) variableType;
    if (!variableClassType.isRaw()) return null;
    if (!(initializerType instanceof PsiClassType)) return null;
    final PsiClassType initializerClassType = (PsiClassType) initializerType;
    if (initializerClassType.isRaw()) return null;
    final PsiClassType.ClassResolveResult variableResolveResult = variableClassType.resolveGenerics();
    final PsiClassType.ClassResolveResult initializerResolveResult = initializerClassType.resolveGenerics();
    if (initializerResolveResult.getElement() == null) return null;
    PsiClass variableResolved = variableResolveResult.getElement();
    PsiSubstitutor targetSubstitutor = TypeConversionUtil.getClassSubstitutor(variableResolved, initializerResolveResult.getElement(), initializerResolveResult.getSubstitutor());
    if (targetSubstitutor == null) return null;
    PsiType type = JavaPsiFacade.getInstance(variable.getProject()).getElementFactory().createType(variableResolved, targetSubstitutor);
    if (variableType.equals(type)) return null;
    newTypeName = type.getCanonicalText();
    return Pair.create(variable, type);
  }

  @Override
  @RequiredReadAction
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    Pair<PsiVariable, PsiType> pair = findVariable(element);
    if (pair == null) return;
    PsiVariable variable = pair.getFirst();
    PsiType type = pair.getSecond();

    variable.getTypeElement().replace(JavaPsiFacade.getInstance(variable.getProject()).getElementFactory().createTypeElement(type));
  }
}
