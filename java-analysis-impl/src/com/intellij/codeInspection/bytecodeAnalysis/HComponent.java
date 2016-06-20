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

/**
 * Represents a lattice product of a constant {@link #value} and all {@link #ids}.
 */
final class HComponent {
  @NotNull
  Value value;
  @NotNull final HKey[] ids;

  HComponent(@NotNull Value value, @NotNull HKey[] ids) {
    this.value = value;
    this.ids = ids;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HComponent that = (HComponent)o;

    if (!Arrays.equals(ids, that.ids)) return false;
    if (value != that.value) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = value.hashCode();
    result = 31 * result + Arrays.hashCode(ids);
    return result;
  }

  public boolean remove(@NotNull HKey id) {
    return HUtils.remove(ids, id);
  }

  public boolean isEmpty() {
    return HUtils.isEmpty(ids);
  }

  @NotNull
  public HComponent copy() {
    return new HComponent(value, ids.clone());
  }
}
