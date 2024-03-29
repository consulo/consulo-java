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
package com.intellij.java.debugger.impl.descriptors.data;

import jakarta.annotation.Nonnull;

import com.intellij.java.debugger.impl.ui.impl.watch.ThrownExceptionValueDescriptorImpl;
import consulo.project.Project;
import consulo.internal.com.sun.jdi.ObjectReference;

public final class ThrownExceptionValueData extends DescriptorData<ThrownExceptionValueDescriptorImpl>{
  @Nonnull
  private final ObjectReference myExceptionObj;

  public ThrownExceptionValueData(@Nonnull ObjectReference exceptionObj) {
    myExceptionObj = exceptionObj;
  }

  protected ThrownExceptionValueDescriptorImpl createDescriptorImpl(Project project) {
    return new ThrownExceptionValueDescriptorImpl(project, myExceptionObj);
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ThrownExceptionValueData data = (ThrownExceptionValueData)o;

    if (!myExceptionObj.equals(data.myExceptionObj)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myExceptionObj.hashCode();
  }

  public DisplayKey<ThrownExceptionValueDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<ThrownExceptionValueDescriptorImpl>(myExceptionObj);
  }
}
