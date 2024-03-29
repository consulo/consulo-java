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
package com.intellij.java.debugger.impl.descriptors.data;

import jakarta.annotation.Nonnull;

import com.intellij.java.debugger.impl.ui.impl.watch.StaticDescriptorImpl;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.internal.com.sun.jdi.ReferenceType;

public final class StaticData extends DescriptorData<StaticDescriptorImpl>{
  private static final Key STATIC = new Key("STATIC");

  private final ReferenceType myRefType;

  public StaticData(@Nonnull ReferenceType refType) {
    myRefType = refType;
  }

  public ReferenceType getRefType() {
    return myRefType;
  }

  protected StaticDescriptorImpl createDescriptorImpl(Project project) {
    return new StaticDescriptorImpl(myRefType);
  }

  public boolean equals(Object object) {
    return object instanceof StaticData;

  }

  public int hashCode() {
    return STATIC.hashCode();
  }

  public DisplayKey<StaticDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<StaticDescriptorImpl>(STATIC);
  }
}
