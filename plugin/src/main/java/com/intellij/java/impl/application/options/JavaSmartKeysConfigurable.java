/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.application.options;

import com.intellij.java.language.JavadocBundle;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.BeanConfigurable;
import consulo.configurable.ProjectConfigurable;
import consulo.java.impl.application.options.JavaSmartKeysSettings;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Denis Zhdanov
 * @since 2/2/11 12:32 PM
 */
@ExtensionImpl
public class JavaSmartKeysConfigurable extends BeanConfigurable<JavaSmartKeysSettings> implements ProjectConfigurable {
  public JavaSmartKeysConfigurable() {
    super(JavaSmartKeysSettings.getInstance());
    checkBox("JAVADOC_GENERATE_CLOSING_TAG", JavadocBundle.message("javadoc.generate.closing.tag"));
  }

  @Nonnull
  @Override
  public String getId() {
    return "editor.preferences.smartKeys.java";
  }

  @Nullable
  @Override
  public String getParentId() {
    return "editor.preferences.smartKeys";
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Java";
  }
}
