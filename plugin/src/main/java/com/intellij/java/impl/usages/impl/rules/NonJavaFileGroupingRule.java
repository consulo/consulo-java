/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.usages.impl.rules;

import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.ServerPageFile;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.usage.Usage;
import consulo.usage.UsageGroup;
import consulo.usage.UsageTarget;
import consulo.usage.rule.FileGroupingRule;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class NonJavaFileGroupingRule extends FileGroupingRule {
  public NonJavaFileGroupingRule(Project project) {
    super(project);
  }

  @Nullable
  @Override
  public UsageGroup getParentGroupFor(@Nonnull Usage usage, @Nonnull UsageTarget[] targets) {
    final FileUsageGroup usageGroup = (FileUsageGroup) super.getParentGroupFor(usage, targets);
    if (usageGroup != null) {
      final PsiFile psiFile = usageGroup.getPsiFile();
      if (psiFile instanceof PsiJavaFile && !(psiFile instanceof ServerPageFile)) {
        return null;
      }
    }
    return usageGroup;
  }
}
