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

import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;

interface NullableMethodAnalysisData {
  Type NullType = Type.getObjectType("null");
  Type ThisType = Type.getObjectType("this");
  Type CallType = Type.getObjectType("/Call");

  final class LabeledNull extends BasicValue
  {
    final int origins;

    public LabeledNull(int origins) {
      super(NullType);
      this.origins = origins;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      LabeledNull that = (LabeledNull)o;
      return origins == that.origins;
    }

    @Override
    public int hashCode() {
      return origins;
    }
  }

  final class Calls extends BasicValue {
    final int mergedLabels;

    public Calls(int mergedLabels) {
      super(CallType);
      this.mergedLabels = mergedLabels;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      Calls calls = (Calls)o;
      return mergedLabels == calls.mergedLabels;
    }

    @Override
    public int hashCode() {
      return mergedLabels;
    }
  }

  final class Constraint {
    final static Constraint EMPTY = new Constraint(0, 0);

    final int calls;
    final int nulls;

    public Constraint(int calls, int nulls) {
      this.calls = calls;
      this.nulls = nulls;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Constraint that = (Constraint)o;

      if (calls != that.calls) return false;
      if (nulls != that.nulls) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = calls;
      result = 31 * result + nulls;
      return result;
    }
  }

  BasicValue ThisValue = new BasicValue(ThisType);
}
