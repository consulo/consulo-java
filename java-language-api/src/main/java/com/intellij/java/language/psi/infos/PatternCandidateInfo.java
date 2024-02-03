// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi.infos;

import com.intellij.java.language.psi.PsiSubstitutor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * A resolve result that describes deconstruction pattern inference
 */
public class PatternCandidateInfo extends CandidateInfo {
  private final String myInferenceError;

  public PatternCandidateInfo(@Nonnull CandidateInfo candidate,
                              @Nonnull PsiSubstitutor substitutor,
                              @Nullable String inferenceError) {
    super(candidate, substitutor);
    myInferenceError = inferenceError;
  }

  public String getInferenceError() {
    return myInferenceError;
  }
}
