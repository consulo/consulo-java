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
package com.intellij.java.impl.psi.codeStyle.arrangement;

import consulo.document.util.TextRange;
import consulo.ide.impl.idea.util.containers.ContainerUtilRt;
import consulo.language.codeStyle.arrangement.*;
import consulo.language.codeStyle.arrangement.std.ArrangementSettingsToken;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Set;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/20/12 4:50 PM
 */
public class JavaElementArrangementEntry extends DefaultArrangementEntry
  implements TypeAwareArrangementEntry, NameAwareArrangementEntry,ModifierAwareArrangementEntry
{

  @Nonnull
  private final Set<ArrangementSettingsToken> myModifiers = ContainerUtilRt.newHashSet();
  @Nonnull
  private final Set<ArrangementSettingsToken> myTypes     = ContainerUtilRt.newHashSet();

  @Nonnull
  private final  ArrangementSettingsToken myType;
  @Nullable private final String                   myName;

  public JavaElementArrangementEntry(@Nullable ArrangementEntry parent,
                                     @Nonnull TextRange range,
                                     @Nonnull ArrangementSettingsToken type,
                                     @Nullable String name,
                                     boolean canBeMatched)
  {
    this(parent, range.getStartOffset(), range.getEndOffset(), type, name, canBeMatched);
  }

  public JavaElementArrangementEntry(@Nullable ArrangementEntry parent,
                                     int startOffset,
                                     int endOffset,
                                     @Nonnull ArrangementSettingsToken type,
                                     @Nullable String name,
                                     boolean canBeArranged)
  {
    super(parent, startOffset, endOffset, canBeArranged);
    myType = type;
    myTypes.add(type);
    myName = name;
  }

  @Nonnull
  @Override
  public Set<ArrangementSettingsToken> getModifiers() {
    return myModifiers;
  }

  public void addModifier(@Nonnull ArrangementSettingsToken modifier) {
    myModifiers.add(modifier);
  }

  @Nullable
  @Override
  public String getName() {
    return myName;
  }

  @Nonnull
  @Override
  public Set<ArrangementSettingsToken> getTypes() {
    return myTypes;
  }

  @Nonnull
  public ArrangementSettingsToken getType() {
    return myType;
  }

  @Override
  public String toString() {
    return String.format(
      "[%d; %d): %s %s %s",
      getStartOffset(), getEndOffset(), StringUtil.join(myModifiers, " ").toLowerCase(),
      myTypes.iterator().next().toString().toLowerCase(), myName == null ? "<no name>" : myName
    );
  }
}
