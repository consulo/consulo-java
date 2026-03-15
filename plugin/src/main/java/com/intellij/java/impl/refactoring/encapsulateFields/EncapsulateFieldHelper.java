/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.encapsulateFields;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.component.extension.ExtensionPointCacheKey;
import consulo.language.Language;
import consulo.language.extension.ByLanguageValue;
import consulo.language.extension.LanguageExtension;
import consulo.language.extension.LanguageOneToOne;
import consulo.language.psi.PsiReference;

import org.jspecify.annotations.Nullable;

/**
 * @author Max Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class EncapsulateFieldHelper implements LanguageExtension {
 private static final ExtensionPointCacheKey<EncapsulateFieldHelper, ByLanguageValue<EncapsulateFieldHelper>> KEY = ExtensionPointCacheKey.create("EncapsulateFieldHelper", LanguageOneToOne.build());

  @Nullable
  public static EncapsulateFieldHelper forLanguage(Language language) {
    return Application.get().getExtensionPoint(EncapsulateFieldHelper.class).getOrBuildCache(KEY).get(language);
  }

  public abstract PsiField[] getApplicableFields(PsiClass aClass);

  public abstract String suggestSetterName(PsiField field);

  public abstract String suggestGetterName(PsiField field);

  @Nullable
  public abstract PsiMethod generateMethodPrototype(PsiField field, String methodName, boolean isGetter);

  public abstract boolean processUsage(EncapsulateFieldUsageInfo usage,
                                       EncapsulateFieldsDescriptor descriptor,
                                       PsiMethod setter,
                                       PsiMethod getter);

  @Nullable
  public abstract EncapsulateFieldUsageInfo createUsage(EncapsulateFieldsDescriptor descriptor,
                                                        FieldDescriptor fieldDescriptor,
                                                        PsiReference reference);

  @Deprecated
  public static EncapsulateFieldHelper getHelper(Language lang) {
    return forLanguage(lang);
  }
}
