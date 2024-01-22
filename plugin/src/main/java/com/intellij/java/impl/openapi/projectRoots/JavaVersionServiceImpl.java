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
package com.intellij.java.impl.openapi.projectRoots;

import com.intellij.java.language.impl.projectRoots.JavaSdkVersionUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.projectRoots.JavaVersionService;
import consulo.annotation.component.ServiceImpl;
import consulo.language.psi.PsiElement;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;

/**
 * @author anna
 * @since 3/28/12
 */
@Singleton
@ServiceImpl
public class JavaVersionServiceImpl extends JavaVersionService {
  @Override
  public boolean isAtLeast(@jakarta.annotation.Nonnull PsiElement element, @Nonnull JavaSdkVersion version) {
    return JavaSdkVersionUtil.isAtLeast(element, version);
  }

  @Override
  public JavaSdkVersion getJavaSdkVersion(@jakarta.annotation.Nonnull PsiElement element) {
    return JavaSdkVersionUtil.getJavaSdkVersion(element);
  }
}
