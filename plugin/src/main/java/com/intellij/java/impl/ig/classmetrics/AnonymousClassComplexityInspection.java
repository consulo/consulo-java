/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.classmetrics;

import com.intellij.java.impl.ig.fixes.MoveAnonymousToInnerClassFix;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class AnonymousClassComplexityInspection
  extends ClassMetricInspection {

  private static final int DEFAULT_COMPLEXITY_LIMIT = 3;

  @Override
  @Nonnull
  public String getID() {
    return "OverlyComplexAnonymousInnerClass";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.overlyComplexAnonymousInnerClassDisplayName().get();
  }

  @Override
  protected int getDefaultLimit() {
    return DEFAULT_COMPLEXITY_LIMIT;
  }

  @Override
  protected String getConfigurationLabel() {
    return InspectionGadgetsLocalize.cyclomaticComplexityLimitOption().get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new MoveAnonymousToInnerClassFix();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final Integer totalComplexity = (Integer)infos[0];
    return InspectionGadgetsLocalize.overlyComplexAnonymousInnerClassProblemDescriptor(totalComplexity).get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassComplexityVisitor();
  }

  private class ClassComplexityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@Nonnull PsiClass psiClass) {
      // no call to super, to prevent double counting
    }

    @Override
    public void visitAnonymousClass(
      @Nonnull PsiAnonymousClass aClass) {
      if (aClass instanceof PsiEnumConstantInitializer) {
        return;
      }
      final int totalComplexity = calculateTotalComplexity(aClass);
      if (totalComplexity <= getLimit()) {
        return;
      }
      registerClassError(aClass, Integer.valueOf(totalComplexity));
    }

    private int calculateTotalComplexity(PsiClass aClass) {
      if (aClass == null) {
        return 0;
      }
      final PsiMethod[] methods = aClass.getMethods();
      int totalComplexity = calculateComplexityForMethods(methods);
      totalComplexity += calculateInitializerComplexity(aClass);
      return totalComplexity;
    }

    private int calculateInitializerComplexity(PsiClass aClass) {
      final ComplexityVisitor visitor = new ComplexityVisitor();
      int complexity = 0;
      final PsiClassInitializer[] initializers = aClass.getInitializers();
      for (final PsiClassInitializer initializer : initializers) {
        visitor.reset();
        initializer.accept(visitor);
        complexity += visitor.getComplexity();
      }
      return complexity;
    }

    private int calculateComplexityForMethods(PsiMethod[] methods) {
      final ComplexityVisitor visitor = new ComplexityVisitor();
      int complexity = 0;
      for (final PsiMethod method : methods) {
        visitor.reset();
        method.accept(visitor);
        complexity += visitor.getComplexity();
      }
      return complexity;
    }
  }
}