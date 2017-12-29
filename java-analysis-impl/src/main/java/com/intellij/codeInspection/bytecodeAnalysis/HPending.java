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

import org.jetbrains.annotations.NotNull;

final class HPending implements HResult {
  @NotNull
  final HComponent[] delta; // sum

  HPending(@NotNull HComponent[] delta) {
    this.delta = delta;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HPending hPending = (HPending)o;
    if (!Arrays.equals(delta, hPending.delta)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(delta);
  }

  @NotNull
  HPending copy() {
    HComponent[] delta1 = new HComponent[delta.length];
    for (int i = 0; i < delta.length; i++) {
      delta1[i] = delta[i].copy();

    }
    return new HPending(delta1);
  }
}
