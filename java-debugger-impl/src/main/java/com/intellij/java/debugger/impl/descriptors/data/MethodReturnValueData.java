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

import com.intellij.java.debugger.impl.ui.impl.watch.MethodReturnValueDescriptorImpl;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.internal.com.sun.jdi.Method;
import consulo.internal.com.sun.jdi.Value;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public final class MethodReturnValueData extends DescriptorData<MethodReturnValueDescriptorImpl>{
  private final @jakarta.annotation.Nullable
  Value myReturnValue;
  private final @jakarta.annotation.Nonnull
  Method myMethod;

  public MethodReturnValueData(@jakarta.annotation.Nonnull Method method, @jakarta.annotation.Nullable Value returnValue) {
    super();
    myMethod = method;
    myReturnValue = returnValue;
  }

  public @Nullable
  Value getReturnValue() {
    return myReturnValue;
  }

  public @jakarta.annotation.Nonnull
  Method getMethod() {
    return myMethod;
  }

  protected MethodReturnValueDescriptorImpl createDescriptorImpl(Project project) {
    return new MethodReturnValueDescriptorImpl(project, myMethod, myReturnValue);
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MethodReturnValueData that = (MethodReturnValueData)o;

    if (!myMethod.equals(that.myMethod)) return false;
    if (myReturnValue != null ? !myReturnValue.equals(that.myReturnValue) : that.myReturnValue != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myReturnValue != null ? myReturnValue.hashCode() : 0);
    result = 31 * result + myMethod.hashCode();
    return result;
  }

  public DisplayKey<MethodReturnValueDescriptorImpl> getDisplayKey() {
    return new MethodReturnValueDisplayKey(myMethod, myReturnValue);
  }

  private static final class MethodReturnValueDisplayKey extends Pair<Method, Value> implements DisplayKey<MethodReturnValueDescriptorImpl> {
    public MethodReturnValueDisplayKey(@Nonnull Method method, @Nullable Value value) {
      super(method, value);
    }
  }
}
