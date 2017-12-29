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

package com.intellij.codeInspection.bytecodeAnalysis;

import java.util.List;

import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;

// additional data structures for combined analysis
interface CombinedData {

  final class ParamKey {
    final Method method;
    final int i;
    final boolean stable;

    ParamKey(Method method, int i, boolean stable) {
      this.method = method;
      this.i = i;
      this.stable = stable;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ParamKey paramKey = (ParamKey)o;

      if (i != paramKey.i) return false;
      if (stable != paramKey.stable) return false;
      if (!method.equals(paramKey.method)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = method.hashCode();
      result = 31 * result + i;
      result = 31 * result + (stable ? 1 : 0);
      return result;
    }
  }

  // value knowing at which instruction it was created
  interface Trackable {
    int getOriginInsnIndex();
  }

  final class TrackableCallValue extends BasicValue implements Trackable {
    private final int originInsnIndex;
    final Method method;
    final List<? extends BasicValue> args;
    final boolean stableCall;
    final boolean thisCall;

    TrackableCallValue(int originInsnIndex, Type tp, Method method, List<? extends BasicValue> args, boolean stableCall, boolean thisCall) {
      super(tp);
      this.originInsnIndex = originInsnIndex;
      this.method = method;
      this.args = args;
      this.stableCall = stableCall;
      this.thisCall = thisCall;
    }

    @Override
    public int getOriginInsnIndex() {
      return originInsnIndex;
    }
  }

  final class NthParamValue extends BasicValue {
    final int n;

    public NthParamValue(Type type, int n) {
      super(type);
      this.n = n;
    }
  }

  final class TrackableNullValue extends BasicValue implements Trackable {
    static final Type NullType = Type.getObjectType("null");
    private final int originInsnIndex;
    public TrackableNullValue(int originInsnIndex) {
      super(NullType);
      this.originInsnIndex = originInsnIndex;
    }

    @Override
    public int getOriginInsnIndex() {
      return originInsnIndex;
    }
  }

  final class TrackableValue extends BasicValue implements Trackable {
    private final int originInsnIndex;

    public TrackableValue(int originInsnIndex, Type type) {
      super(type);
      this.originInsnIndex = originInsnIndex;
    }

    @Override
    public int getOriginInsnIndex() {
      return originInsnIndex;
    }
  }

  BasicValue ThisValue = new BasicValue(Type.getObjectType("java/lang/Object"));
}
