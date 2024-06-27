/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.compiler;

import com.intellij.java.analysis.impl.codeInspection.ex.BaseLocalInspectionTool;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElementVisitor;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

@ExtensionImpl
public class JavacQuirksInspection extends BaseLocalInspectionTool {
  @Nls @Nonnull
  @Override
  public String getGroupDisplayName() {
    return InspectionLocalize.groupNamesCompilerIssues().get();
  }

  @Nls @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionLocalize.inspectionCompilerJavacQuirksName().get();
  }

  @Nonnull
  @Override
  public String getShortName() {
    return "JavacQuirks";
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitorImpl(
    @Nonnull final ProblemsHolder holder,
    final boolean isOnTheFly,
    LocalInspectionToolSession session,
    Object state
  ) {
    return new JavacQuirksInspectionVisitor(holder);
  }
}
