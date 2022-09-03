/*
 * Copyright 2007 Bas Leijdekkers
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
package com.intellij.java.impl.ig;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.ig.InspectionGadgetsFix;

import javax.annotation.Nonnull;

public class DelegatingFix extends InspectionGadgetsFix {

  private final LocalQuickFix delegate;

  public DelegatingFix(LocalQuickFix delegate) {
    this.delegate = delegate;
  }

  @Nonnull
  public String getName() {
    return delegate.getName();
  }

  @Nonnull
  public String getFamilyName() {
    return delegate.getName();
  }

  protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
    delegate.applyFix(project, descriptor);
  }

  /**
   * Delegating fix should check for read-only status separately
   */
  @Override
  protected boolean isQuickFixOnReadOnlyFile(PsiElement problemElement) {
    return false;
  }
}