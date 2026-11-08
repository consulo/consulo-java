/*
 * Copyright 2013-2026 consulo.io
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
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.language.codeInsight.Nullability;
import consulo.language.dataFlow.types.DfType;
import consulo.language.dataFlow.types.DfaNullability;
import org.jspecify.annotations.Nullable;

/**
 * The Java part of JetBrains' {@code DfaNullability}: the conversions between the language independent
 * {@link DfaNullability} of the platform and the Java nullability annotations ({@link Nullability}) and reference types ({@link DfTypes}).
 * In JetBrains these are the methods {@code toNullability}, {@code fromNullability} and {@code asDfType} of {@code DfaNullability}.
 */
public final class JavaDfaNullability {
  private JavaDfaNullability() {
  }

  public static Nullability toNullability(@Nullable DfaNullability dfaNullability) {
    if (dfaNullability == null) return Nullability.UNKNOWN;
    return switch (dfaNullability) {
      case NULL, NULLABLE -> Nullability.NULLABLE;
      case NOT_NULL -> Nullability.NOT_NULL;
      case UNKNOWN, FLUSHED -> Nullability.UNKNOWN;
    };
  }

  public static DfaNullability fromNullability(Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> DfaNullability.NOT_NULL;
      case NULLABLE -> DfaNullability.NULLABLE;
      case UNKNOWN -> DfaNullability.UNKNOWN;
    };
  }

  public static DfReferenceType asDfType(DfaNullability nullability) {
    return switch (nullability) {
      case NULL -> DfTypes.NULL;
      case NOT_NULL -> DfTypes.NOT_NULL_OBJECT;
      case UNKNOWN -> DfTypes.OBJECT_OR_NULL;
      default -> DfTypes.customObject(TypeConstraints.TOP, nullability, Mutability.UNKNOWN, null, DfType.BOTTOM);
    };
  }

  public static DfaNullability fromDfType(DfType type) {
    return DfaNullability.fromDfType(type);
  }
}
