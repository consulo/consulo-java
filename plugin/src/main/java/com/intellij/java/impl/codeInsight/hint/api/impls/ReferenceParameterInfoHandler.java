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
package com.intellij.java.impl.codeInsight.hint.api.impls;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.parameterInfo.*;
import consulo.language.psi.PsiElement;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;

/**
 * @author Maxim.Mossienko
 */
@ExtensionImpl
public class ReferenceParameterInfoHandler implements ParameterInfoHandler<PsiReferenceParameterList, PsiTypeParameter> {
  @Override
  public Object[] getParametersForLookup(LookupElement item, ParameterInfoContext context) {
    return null;
  }

  @Override
  public Object[] getParametersForDocumentation(PsiTypeParameter p, ParameterInfoContext context) {
    return new Object[]{p};
  }

  @Override
  public boolean couldShowInLookup() {
    return false;
  }

  @Override
  public PsiReferenceParameterList findElementForParameterInfo(CreateParameterInfoContext context) {
    PsiReferenceParameterList referenceParameterList =
        ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PsiReferenceParameterList.class);

    if (referenceParameterList != null) {
      if (!(referenceParameterList.getParent() instanceof PsiJavaCodeReferenceElement)) return null;
      PsiJavaCodeReferenceElement ref = ((PsiJavaCodeReferenceElement) referenceParameterList.getParent());
      PsiElement psiElement = ref.resolve();
      if (!(psiElement instanceof PsiTypeParameterListOwner)) return null;

      PsiTypeParameter[] typeParams = ((PsiTypeParameterListOwner) psiElement).getTypeParameters();
      if (typeParams.length == 0) return null;

      context.setItemsToShow(typeParams);
      return referenceParameterList;
    }

    return null;
  }

  @Override
  public void showParameterInfo(@Nonnull PsiReferenceParameterList element, CreateParameterInfoContext context) {
    context.showHint(element, element.getTextRange().getStartOffset() + 1, this);
  }

  @Override
  public PsiReferenceParameterList findElementForUpdatingParameterInfo(UpdateParameterInfoContext context) {
    return ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PsiReferenceParameterList.class);
  }

  @Override
  public void updateParameterInfo(@Nonnull PsiReferenceParameterList o, UpdateParameterInfoContext context) {
    int index = ParameterInfoUtils.getCurrentParameterIndex(o.getNode(), context.getOffset(), JavaTokenType.COMMA);
    context.setCurrentParameter(index);
    Object[] objectsToView = context.getObjectsToView();
    context.setHighlightedParameter(index < objectsToView.length && index >= 0 ? (PsiElement) objectsToView[index] : null);
  }

  @Override
  @Nonnull
  public String getParameterCloseChars() {
    return ",>";
  }

  @Override
  public boolean tracksParameterIndex() {
    return true;
  }

  @Override
  public void updateUI(PsiTypeParameter o, ParameterInfoUIContext context) {
    updateTypeParameter(o, context);
  }

  private static void updateTypeParameter(PsiTypeParameter typeParameter, ParameterInfoUIContext context) {
    @NonNls StringBuffer buffer = new StringBuffer();
    buffer.append(typeParameter.getName());
    int highlightEndOffset = buffer.length();
    buffer.append(" extends ");
    buffer.append(StringUtil.join(
        Arrays.asList(typeParameter.getSuperTypes()),
        t -> t.getPresentableText(), ", "));

    context.setupUIComponentPresentation(buffer.toString(), 0, highlightEndOffset, false, false, false,
        context.getDefaultParameterColor());
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
