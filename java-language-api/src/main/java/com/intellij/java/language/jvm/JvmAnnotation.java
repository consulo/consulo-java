// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.jvm;

import com.intellij.java.language.jvm.annotation.JvmAnnotationAttribute;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface JvmAnnotation {

  /**
   * Returns the fully qualified name of the annotation class.
   *
   * @return the class name, or null if the annotation is unresolved.
   */
  @Nullable
  String getQualifiedName();

  /**
   * This method is preferable to {@link #findAttribute(String)}
   * because it allows to provide more efficient implementation.
   *
   * @return {@code true} if this annotation has an attribute with the specified name, otherwise {@code false}
   */
  default boolean hasAttribute(String attributeName) {
    return findAttribute(attributeName) != null;
  }

  /**
   * This method is preferable to manual search in results of {@link #getAttributes()}
   * because it allows to provide more efficient implementation.
   *
   * @return attribute if this annotation has an attribute with specified name, otherwise {@code null}
   */
  @Nullable
  default JvmAnnotationAttribute findAttribute(String attributeName) {
    return ContainerUtil.find(getAttributes(), attribute -> attributeName.equals(attribute.getAttributeName()));
  }

  List<JvmAnnotationAttribute> getAttributes();
}
