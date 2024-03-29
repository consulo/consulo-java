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
package com.intellij.java.impl.refactoring.memberPullUp;

import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import jakarta.annotation.Nonnull;

/**
 * Created by Max Medvedev on 10/4/13
 */
@ExtensionImpl
public class JavaPullUpHelperFactory implements PullUpHelperFactory {
  @Override
  public PullUpHelper createPullUpHelper(PullUpData data) {
    return new JavaPullUpHelper(data);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
