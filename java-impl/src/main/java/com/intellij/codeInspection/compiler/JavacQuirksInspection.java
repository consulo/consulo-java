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
package com.intellij.codeInspection.compiler;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;

public class JavacQuirksInspection extends BaseLocalInspectionTool {
  @Nls @Nonnull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.COMPILER_ISSUES;
  }

  @Nls @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.compiler.javac.quirks.name");
  }

  @Nonnull
  @Override
  public String getShortName() {
    return "JavacQuirks";
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavacQuirksInspectionVisitor(holder);
  }
}
