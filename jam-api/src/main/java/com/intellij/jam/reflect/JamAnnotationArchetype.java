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
package com.intellij.jam.reflect;

import consulo.util.collection.SmartList;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nullable;

import java.util.List;

/**
 * @author peter
 */
public class JamAnnotationArchetype {
  @Nullable
  private final JamAnnotationArchetype myArchetype;
  private final List<JamAttributeMeta<?>> myAttributes = new SmartList<JamAttributeMeta<?>>();

  public JamAnnotationArchetype() {
    this(null);
  }

  public JamAnnotationArchetype(@Nullable JamAnnotationArchetype archetype) {
    myArchetype = archetype;
  }

  public JamAnnotationArchetype addAttribute(JamAttributeMeta<?> attributeMeta) {
    myAttributes.add(attributeMeta);
    return this;
  }

  public List<JamAttributeMeta<?>> getAttributes() {
    return myAttributes;
  }

  @Nullable
  public JamAnnotationArchetype getArchetype() {
    return myArchetype;
  }

  @Nullable
  public JamAttributeMeta<?> findAttribute(@Nullable @NonNls String name) {
    if (name == null) name = "value";
    for (final JamAttributeMeta<?> attribute : myAttributes) {
      if (attribute.getAttributeLink().getAttributeName().equals(name)) {
        return attribute;
      }
    }
    if (myArchetype != null) {
      return myArchetype.findAttribute(name);
    }
    return null;
  }

}
