// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.jvm.annotation;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents a single element from <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.16">element_value_pairs</a>
 * in an annotation.
 */
public interface JvmAnnotationAttribute {

  @Nonnull
  String getAttributeName();

  @Nullable
  JvmAnnotationAttributeValue getAttributeValue();
}
