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
package com.intellij.refactoring.encapsulateFields;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;

/**
 * @author Max Medvedev
 */
public abstract class EncapsulateFieldHelper {
  private static class Extension extends LanguageExtension<EncapsulateFieldHelper> {

    public Extension() {
      super("consulo.java.encapsulateFields.helper");
    }
  }
  private static final Extension INSTANCE = new Extension();

  @Nonnull
  public abstract PsiField[] getApplicableFields(@Nonnull PsiClass aClass);

  @Nonnull
  public abstract String suggestSetterName(@Nonnull PsiField field);

  @Nonnull
  public abstract String suggestGetterName(@Nonnull PsiField field);

  @Nullable
  public abstract PsiMethod generateMethodPrototype(@Nonnull PsiField field, @Nonnull String methodName, boolean isGetter);

  public abstract boolean processUsage(@Nonnull EncapsulateFieldUsageInfo usage,
                                       @Nonnull EncapsulateFieldsDescriptor descriptor,
                                       PsiMethod setter,
                                       PsiMethod getter);

  @Nullable
  public abstract EncapsulateFieldUsageInfo createUsage(@Nonnull EncapsulateFieldsDescriptor descriptor,
                                                        @Nonnull FieldDescriptor fieldDescriptor,
                                                        @Nonnull PsiReference reference);

  public static EncapsulateFieldHelper getHelper(Language lang) {
    return INSTANCE.forLanguage(lang);
  }
}
