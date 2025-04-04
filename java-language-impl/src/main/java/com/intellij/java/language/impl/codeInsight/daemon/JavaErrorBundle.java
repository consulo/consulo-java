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
package com.intellij.java.language.impl.codeInsight.daemon;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.internal.MigratedExtensionsTo;
import consulo.component.util.localize.AbstractBundle;
import consulo.java.language.impl.localize.JavaErrorLocalize;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author max
 */
@Deprecated
@DeprecationInfo("Use JavaErrorLocalize")
@MigratedExtensionsTo(JavaErrorLocalize.class)
public class JavaErrorBundle extends AbstractBundle {
  public static final String BUNDLE = "messages.JavaErrorBundle";
  private static final JavaErrorBundle ourInstance = new JavaErrorBundle();

  public static JavaErrorBundle getInstance() {
    return ourInstance;
  }

  private JavaErrorBundle() {
    super(BUNDLE);
  }

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key) {
    return ourInstance.getMessage(key);
  }

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return ourInstance.getMessage(key, params);
  }
}
