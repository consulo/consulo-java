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
package com.intellij.java.impl.codeInsight.generation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.intellij.java.language.impl.codeInsight.generation.EncapsulatableClassMember;
import consulo.component.extension.ExtensionPointName;
import consulo.component.extension.Extensions;
import com.intellij.java.language.psi.PsiClass;
import consulo.ide.impl.idea.util.Function;
import consulo.ide.impl.idea.util.NotNullFunction;
import consulo.util.collection.ContainerUtil;

/**
 * @author peter
 */
public class GenerateAccessorProviderRegistrar {

  public final static ExtensionPointName<NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>>> EP_NAME = ExtensionPointName.create("consulo.java.generateAccessorProvider");

  private static final List<NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>>> ourProviders = new ArrayList<NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>>>();

  static {
    ourProviders.addAll(Arrays.asList(Extensions.getExtensions(EP_NAME)));
  }

  /** @see #EP_NAME */
  @Deprecated
  public static void registerProvider(NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>> function) {
    ourProviders.add(function);
  }

  protected static List<EncapsulatableClassMember> getEncapsulatableClassMembers(final PsiClass psiClass) {
    return ContainerUtil.concat(ourProviders, new Function<NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>>, Collection<? extends EncapsulatableClassMember>>() {
      @Override
      public Collection<? extends EncapsulatableClassMember> fun(NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>> s) {
        return s.fun(psiClass);
      }
    });
  }
}
