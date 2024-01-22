/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.codeStyle.arrangement;

import consulo.document.util.TextRange;
import consulo.language.codeStyle.arrangement.ArrangementEntry;
import consulo.language.codeStyle.arrangement.match.TextAwareArrangementEntry;
import consulo.language.codeStyle.arrangement.std.ArrangementSettingsToken;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class JavaSectionArrangementEntry extends JavaElementArrangementEntry implements TextAwareArrangementEntry {
  @Nonnull
  private final String myText;

  public JavaSectionArrangementEntry(
      @Nullable ArrangementEntry parent,
      @Nonnull ArrangementSettingsToken type,
      @Nonnull TextRange range,
      @jakarta.annotation.Nonnull String text,
      boolean canBeMatched) {
    super(parent, range.getStartOffset(), range.getEndOffset(), type, "SECTION", canBeMatched);
    myText = text;
  }

  @Nonnull
  @Override
  public String getText() {
    return myText;
  }
}
