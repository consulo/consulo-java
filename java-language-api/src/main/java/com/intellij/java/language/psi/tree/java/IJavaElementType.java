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
package com.intellij.java.language.psi.tree.java;

import com.intellij.java.language.JavaLanguage;
import consulo.language.ast.IElementType;
import org.jetbrains.annotations.NonNls;

public class IJavaElementType extends IElementType {
  private final boolean myLeftBound;

  public IJavaElementType(@NonNls final String debugName) {
    this(debugName, false);
  }

  public IJavaElementType(@NonNls final String debugName, final boolean leftBound) {
    super(debugName, JavaLanguage.INSTANCE);
    myLeftBound = leftBound;
  }

  @Override
  public boolean isLeftBound() {
    return myLeftBound;
  }
}