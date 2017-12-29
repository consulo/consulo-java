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

interface EffectQuantum {
  EffectQuantum TopEffectQuantum = new EffectQuantum() {};
  EffectQuantum ThisChangeQuantum = new EffectQuantum() {};
  class ParamChangeQuantum implements EffectQuantum {
    final int n;
    public ParamChangeQuantum(int n) {
      this.n = n;
    }
  }
  class CallQuantum implements EffectQuantum {
    final Key key;
    final DataValue[] data;
    final boolean isStatic;
    public CallQuantum(Key key, DataValue[] data, boolean isStatic) {
      this.key = key;
      this.data = data;
      this.isStatic = isStatic;
    }
  }
}
