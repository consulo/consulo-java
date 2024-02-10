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

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.component.extension.ExtensionPointCacheKey;
import consulo.language.Language;
import consulo.language.editor.template.Template;
import consulo.language.extension.ByLanguageValue;
import consulo.language.extension.LanguageExtension;
import consulo.language.extension.LanguageOneToOne;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Max Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface CreateFieldFromUsageHelper extends LanguageExtension {
  ExtensionPointCacheKey<CreateFieldFromUsageHelper, ByLanguageValue<CreateFieldFromUsageHelper>> KEY = ExtensionPointCacheKey.create("CreateFieldFromUsageHelper", LanguageOneToOne.build());

  @Nullable
  static CreateFieldFromUsageHelper forLanguage(@Nonnull Language language) {
    return Application.get().getExtensionPoint(CreateFieldFromUsageHelper.class).getOrBuildCache(KEY).get(language);
  }

  @Nonnull
  public static Template setupTemplate(
      PsiField field,
      Object expectedTypes,
      PsiClass targetClass,
      Editor editor,
      PsiElement context,
      boolean createConstantField) {
    CreateFieldFromUsageHelper helper = forLanguage(field.getLanguage());
    if (helper == null) {
      throw new IllegalArgumentException("CreateFieldFromUsageHelper is not found for language: " + field.getLanguage());
    }
    return helper.setupTemplateImpl(field, expectedTypes, targetClass, editor, context, createConstantField,
        CreateFromUsageBaseFix.getTargetSubstitutor(context));
  }

  @Nonnull
  public static PsiField insertField(@Nonnull PsiClass targetClass, @Nonnull PsiField field, @Nonnull PsiElement place) {
    CreateFieldFromUsageHelper helper = forLanguage(field.getLanguage());
    if (helper == null) {
      throw new IllegalArgumentException("CreateFieldFromUsageHelper is not found for language: " + field.getLanguage());
    }
    return helper.insertFieldImpl(targetClass, field, place);
  }

  public abstract PsiField insertFieldImpl(@Nonnull PsiClass targetClass, @Nonnull PsiField field, @Nonnull PsiElement place);

  public abstract Template setupTemplateImpl(
      PsiField field,
      Object expectedTypes,
      PsiClass targetClass,
      Editor editor,
      PsiElement context,
      boolean createConstantField,
      PsiSubstitutor substitutor);
}
