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
package com.intellij.java.language.patterns;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.PatternCondition;
import consulo.language.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;

/**
 * @author peter
 */
public class PsiClassPattern extends PsiMemberPattern<PsiClass, PsiClassPattern>{
  protected PsiClassPattern() {
    super(PsiClass.class);
  }

  public PsiClassPattern inheritorOf(final boolean strict, final PsiClassPattern pattern) {
    return with(new PatternCondition<PsiClass>("inheritorOf") {
      public boolean accepts(@Nonnull PsiClass psiClass, final ProcessingContext context) {
        return isInheritor(psiClass, pattern, context, !strict);
      }
    });
  }

  private static boolean isInheritor(PsiClass psiClass, ElementPattern pattern, final ProcessingContext matchingContext, boolean checkThisClass) {
    if (psiClass == null) return false;
    if (checkThisClass && pattern.getCondition().accepts(psiClass, matchingContext)) return true;
    if (isInheritor(psiClass.getSuperClass(), pattern, matchingContext, true)) return true;
    for (final PsiClass aClass : psiClass.getInterfaces()) {
      if (isInheritor(aClass, pattern, matchingContext, true)) return true;
    }
    return false;
  }

  public PsiClassPattern inheritorOf(final boolean strict, final String className) {
    return with(new PatternCondition<PsiClass>("inheritorOf") {
      public boolean accepts(@Nonnull PsiClass psiClass, final ProcessingContext context) {
        return InheritanceUtil.isInheritor(psiClass, strict, className);
      }
    });
  }

  public PsiClassPattern isInterface() {
    return with(new PatternCondition<PsiClass>("isInterface") {
      public boolean accepts(@Nonnull final PsiClass psiClass, final ProcessingContext context) {
        return psiClass.isInterface();
      }
    });}
  public PsiClassPattern isAnnotationType() {
    return with(new PatternCondition<PsiClass>("isAnnotationType") {
      public boolean accepts(@Nonnull final PsiClass psiClass, final ProcessingContext context) {
        return psiClass.isAnnotationType();
      }
    });}

  public PsiClassPattern withMethod(final boolean checkDeep, final ElementPattern<? extends PsiMethod> memberPattern) {
    return with(new PatternCondition<PsiClass>("withMethod") {
      public boolean accepts(@Nonnull final PsiClass psiClass, final ProcessingContext context) {
        for (PsiMethod method : (checkDeep ? psiClass.getAllMethods() : psiClass.getMethods())) {
          if (memberPattern.accepts(method, context)) {
            return true;
          }
        }
        return false;
      }
    });
  }
  public PsiClassPattern withField(final boolean checkDeep, final ElementPattern<? extends PsiField> memberPattern) {
    return with(new PatternCondition<PsiClass>("withField") {
      public boolean accepts(@Nonnull final PsiClass psiClass, final ProcessingContext context) {
        for (PsiField field : (checkDeep ? psiClass.getAllFields() : psiClass.getFields())) {
          if (memberPattern.accepts(field, context)) {
            return true;
          }
        }
        return false;
      }
    });
  }

   public PsiClassPattern nonAnnotationType() {
    return with(new PatternCondition<PsiClass>("nonAnnotationType") {
      public boolean accepts(@Nonnull final PsiClass psiClass, final ProcessingContext context) {
        return !psiClass.isAnnotationType();
      }
    });
  }

  public PsiClassPattern withQualifiedName(@NonNls @Nonnull final String qname) {
    return with(new PatternCondition<PsiClass>("withQualifiedName") {
      public boolean accepts(@Nonnull final PsiClass psiClass, final ProcessingContext context) {
        return qname.equals(psiClass.getQualifiedName());
      }
    });
  }
  public PsiClassPattern withQualifiedName(@NonNls @Nonnull final ElementPattern<String> qname) {
    return with(new PatternCondition<PsiClass>("withQualifiedName") {
      public boolean accepts(@Nonnull final PsiClass psiClass, final ProcessingContext context) {
        return qname.accepts(psiClass.getQualifiedName(), context);
      }
    });
  }


}
