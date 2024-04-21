/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.component.util.Iconable;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author nik
 */
public class JavaLookupElementBuilder {
  private JavaLookupElementBuilder() {
  }

  public static LookupElementBuilder forField(@Nonnull PsiField field) {
    return forField(field, field.getName(), null);
  }

  public static LookupElementBuilder forField(@Nonnull PsiField field,
                                              final String lookupString,
                                              final @Nullable PsiClass qualifierClass) {
    final LookupElementBuilder builder = LookupElementBuilder.create(field, lookupString).withIcon(
      IconDescriptorUpdaters.getIcon(field, Iconable.ICON_FLAG_VISIBILITY));
    return setBoldIfInClass(field, qualifierClass, builder);
  }

  public static LookupElementBuilder forMethod(@Nonnull PsiMethod method, final PsiSubstitutor substitutor) {
    return forMethod(method, method.getName(), substitutor, null);
  }

  public static LookupElementBuilder forMethod(@Nonnull PsiMethod method,
                                               @Nonnull String lookupString, final @Nonnull PsiSubstitutor substitutor,
                                               @Nullable PsiClass qualifierClass) {
    LookupElementBuilder builder = LookupElementBuilder.create(method, lookupString)
      .withIcon(IconDescriptorUpdaters.getIcon(method, Iconable.ICON_FLAG_VISIBILITY))
      .withPresentableText(method.getName())
      .withTailText(PsiFormatUtil.formatMethod(method, substitutor,
                                               PsiFormatUtilBase.SHOW_PARAMETERS,
                                               PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE));
    final PsiType returnType = method.getReturnType();
    if (returnType != null) {
      builder = builder.withTypeText(substitutor.substitute(returnType).getPresentableText());
    }
    builder = setBoldIfInClass(method, qualifierClass, builder);
    return builder;
  }

  private static LookupElementBuilder setBoldIfInClass(@Nonnull PsiMember member, @Nullable PsiClass psiClass, @Nonnull LookupElementBuilder builder) {
    if (psiClass != null && member.getManager().areElementsEquivalent(member.getContainingClass(), psiClass)) {
      return builder.bold();
    }
    return builder;
  }

  public static LookupElementBuilder forClass(@Nonnull PsiClass psiClass) {
    return forClass(psiClass, psiClass.getName());
  }

  public static LookupElementBuilder forClass(@Nonnull PsiClass psiClass,
                                              final String lookupString) {
    return forClass(psiClass, lookupString, false);
  }

  public static LookupElementBuilder forClass(@Nonnull PsiClass psiClass,
                                              final String lookupString,
                                              final boolean withLocation) {
    LookupElementBuilder builder =
      LookupElementBuilder.create(psiClass, lookupString).withIcon(IconDescriptorUpdaters.getIcon(psiClass, Iconable.ICON_FLAG_VISIBILITY));
    String name = psiClass.getName();
    if (StringUtil.isNotEmpty(name)) {
      builder = builder.withLookupString(name);
    }
    if (withLocation) {                                                              
      return builder.withTailText(" " + PsiFormatUtil.getPackageDisplayName(psiClass), true);
    }
    return builder;
  }
}
