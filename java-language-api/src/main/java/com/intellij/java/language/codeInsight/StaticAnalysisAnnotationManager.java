// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.codeInsight;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import jakarta.inject.Singleton;

/**
 * A service that provides recognized annotations for static analysis.
 * Methods return lists of fully-qualified annotation names from various static analysis packages with known meaning.
 */
@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public final class StaticAnalysisAnnotationManager {
  private static final String[] KNOWN_UNSTABLE_API_ANNOTATIONS = {
    "org.jetbrains.annotations.ApiStatus.ScheduledForRemoval",
    "org.jetbrains.annotations.ApiStatus.Experimental",
    "org.jetbrains.annotations.ApiStatus.Internal",
    "com.google.common.annotations.Beta",
    "io.reactivex.annotations.Beta",
    "io.reactivex.annotations.Experimental",
    "rx.annotations.Experimental",
    "rx.annotations.Beta",
    "org.apache.http.annotation.Beta",
    "org.gradle.api.Incubating"
  };

  private static final String[] KNOWN_INTERNAL_API_ANNOTATIONS = {
    "org.jetbrains.annotations.ApiStatus.Internal",
    "com.intellij.openapi.util.IntellijInternalApi"
  };

  private static final String[] KNOWN_CONTRACT_ANNOTATIONS = {
    "org.jetbrains.annotations.Contract",
    "org.springframework.lang.Contract"
  };

  public static StaticAnalysisAnnotationManager getInstance() {
    return Application.get().getInstance(StaticAnalysisAnnotationManager.class);
  }

  /**
   * @return array of annotations applicable to JVM methods, fields and classes that mark element as unstable, experimental,
   * or not intended for external use.
   */
  public String[] getKnownUnstableApiAnnotations() {
    return KNOWN_UNSTABLE_API_ANNOTATIONS;
  }

  /**
   * @return array of annotations applicable to JVM methods, fields and classes that mark element as not intended for external use.
   */
  public String[] getKnownInternalApiAnnotations() {
    return KNOWN_INTERNAL_API_ANNOTATIONS;
  }

  /**
   * @return array of contract annotations that uses the contract syntax specified by {@code org.jetbrains.annotations.Contract}
   */
  public String[] getKnownContractAnnotations() {
    return KNOWN_CONTRACT_ANNOTATIONS;
  }
}
