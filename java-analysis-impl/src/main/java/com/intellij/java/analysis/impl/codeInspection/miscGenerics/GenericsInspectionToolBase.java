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
package com.intellij.java.analysis.impl.codeInspection.miscGenerics;

import com.intellij.java.analysis.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.java.language.psi.*;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public abstract class GenericsInspectionToolBase<State> extends BaseJavaBatchLocalInspectionTool<State> {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public ProblemDescriptor[] checkClass(@jakarta.annotation.Nonnull PsiClass aClass, @Nonnull InspectionManager manager, boolean isOnTheFly, State state) {
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    if (initializers.length == 0) return null;
    List<ProblemDescriptor> descriptors = new ArrayList<ProblemDescriptor>();
    for (PsiClassInitializer initializer : initializers) {
      final ProblemDescriptor[] localDescriptions = getDescriptions(initializer, manager, isOnTheFly, state);
      if (localDescriptions != null) {
        ContainerUtil.addAll(descriptors, localDescriptions);
      }
    }
    if (descriptors.isEmpty()) return null;
    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  @Override
  public ProblemDescriptor[] checkField(@Nonnull PsiField field, @Nonnull InspectionManager manager, boolean isOnTheFly, State state) {
    final PsiExpression initializer = field.getInitializer();
    if (initializer != null) {
      return getDescriptions(initializer, manager, isOnTheFly, state);
    }
    if (field instanceof PsiEnumConstant) {
      return getDescriptions(field, manager, isOnTheFly, state);
    }
    return null;
  }

  @Override
  public ProblemDescriptor[] checkMethod(@jakarta.annotation.Nonnull PsiMethod psiMethod, @jakarta.annotation.Nonnull InspectionManager manager, boolean isOnTheFly, State state) {
    final PsiCodeBlock body = psiMethod.getBody();
    if (body != null) {
      return getDescriptions(body, manager, isOnTheFly, state);
    }
    return null;
  }

  @Nullable
  public abstract ProblemDescriptor[] getDescriptions(PsiElement place, InspectionManager manager, boolean isOnTheFly, State state);
}
