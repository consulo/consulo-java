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

import java.util.Arrays;

abstract class HEffectQuantum {
  private final int myHash;

  HEffectQuantum(int hash) {
    myHash = hash;
  }

  @Override
  public final int hashCode() {
    return myHash;
  }

  static final HEffectQuantum TopEffectQuantum = new HEffectQuantum(-1) {};
  static final HEffectQuantum ThisChangeQuantum = new HEffectQuantum(-2) {};
  static class ParamChangeQuantum extends HEffectQuantum {
    final int n;
    public ParamChangeQuantum(int n) {
      super(n);
      this.n = n;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ParamChangeQuantum that = (ParamChangeQuantum)o;

      if (n != that.n) return false;

      return true;
    }
  }
  static class CallQuantum extends HEffectQuantum {
    final HKey key;
    final DataValue[] data;
    final boolean isStatic;
    public CallQuantum(HKey key, DataValue[] data, boolean isStatic) {
      super((key.hashCode() * 31 + Arrays.hashCode(data)) * 31 + (isStatic ? 1 : 0));
      this.key = key;
      this.data = data;
      this.isStatic = isStatic;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      CallQuantum that = (CallQuantum)o;

      if (isStatic != that.isStatic) return false;
      if (!key.equals(that.key)) return false;
      // Probably incorrect - comparing Object[] arrays with Arrays.equals
      if (!Arrays.equals(data, that.data)) return false;

      return true;
    }
  }
}
