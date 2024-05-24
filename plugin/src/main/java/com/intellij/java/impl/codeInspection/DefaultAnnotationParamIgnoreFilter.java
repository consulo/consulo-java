package com.intellij.java.impl.codeInspection;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import jakarta.annotation.Nullable;
import jakarta.annotation.Nonnull;

/**
 * Allows skipping DefaultAnnotationParamInspection for specific annotations parameters
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface DefaultAnnotationParamIgnoreFilter {

  /**
   * @param annotationFQN           full qualified name of the annotation
   * @param annotationParameterName name of the annotation param
   * @return true to skip inspection for {@code annotationParameterName} and annotation {@code annotationFQN}
   */
  boolean ignoreAnnotationParam(@Nullable String annotationFQN, @Nonnull String annotationParameterName);
}
