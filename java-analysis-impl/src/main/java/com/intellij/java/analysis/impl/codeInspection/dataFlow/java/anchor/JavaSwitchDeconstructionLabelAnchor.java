// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor;

import com.intellij.java.language.psi.PsiPattern;

/**
 * An anchor for a deconstruction pattern in a switch label. Represents the value of the record component corresponding to this pattern.
 */
public class JavaSwitchDeconstructionLabelAnchor extends JavaSwitchLabelTakenAnchor {
  public JavaSwitchDeconstructionLabelAnchor(PsiPattern labelElement) {
    super(labelElement);
  }
}
