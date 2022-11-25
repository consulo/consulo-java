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
package com.intellij.java.impl.refactoring.safeDelete;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.component.extension.ExtensionPointCacheKey;
import consulo.language.Language;
import consulo.language.extension.ByLanguageValue;
import consulo.language.extension.LanguageExtension;
import consulo.language.extension.LanguageOneToOne;
import consulo.language.psi.PsiReference;
import consulo.usage.UsageInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Max Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JavaSafeDeleteDelegate extends LanguageExtension {
  ExtensionPointCacheKey<JavaSafeDeleteDelegate, ByLanguageValue<JavaSafeDeleteDelegate>> KEY = ExtensionPointCacheKey.create("JavaSafeDeleteDelegate", LanguageOneToOne.build());

  @Nullable
  static JavaSafeDeleteDelegate forLanguage(@Nonnull Language language) {
    return Application.get().getExtensionPoint(JavaSafeDeleteDelegate.class).getOrBuildCache(KEY).get(language);
  }

  void createUsageInfoForParameter(final PsiReference reference,
                                   final List<UsageInfo> usages,
                                   final PsiParameter parameter,
                                   final PsiMethod method);
}
