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

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 9/26/12 5:47 PM
 */
public class JavaArrangementOverriddenMethodsInfo {

  @Nonnull
  private final List<JavaElementArrangementEntry> myMethodEntries = new ArrayList<JavaElementArrangementEntry>();
  @Nonnull
  private final String myName;

  public JavaArrangementOverriddenMethodsInfo(@Nonnull String name) {
    myName = name;
  }

  public void addMethodEntry(@Nonnull JavaElementArrangementEntry entry) {
    myMethodEntries.add(entry);
  }

  @Nonnull
  public String getName() {
    return myName;
  }

  @Nonnull
  public List<JavaElementArrangementEntry> getMethodEntries() {
    return myMethodEntries;
  }

  @Override
  public String toString() {
    return "methods from " + myName;
  }
}
