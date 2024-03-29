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
import com.intellij.java.language.psi.PsiMember;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.InitialPatternCondition;
import consulo.language.pattern.PatternConditionPlus;
import consulo.language.util.ProcessingContext;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

import java.util.function.BiPredicate;

/**
 * @author peter
 */
public class PsiMemberPattern<T extends PsiMember, Self extends PsiMemberPattern<T,Self>> extends PsiModifierListOwnerPattern<T,Self> {
  public PsiMemberPattern(@Nonnull final InitialPatternCondition<T> condition) {
    super(condition);
  }

  protected PsiMemberPattern(final Class<T> aClass) {
    super(aClass);
  }

  public Self inClass(final @NonNls String qname) {
    return inClass(PsiJavaPatterns.psiClass().withQualifiedName(qname));
  }

  public Self inClass(final ElementPattern pattern) {
    return with(new PatternConditionPlus<T, PsiClass>("inClass", pattern) {
      @Override
      public boolean processValues(T t, ProcessingContext context, BiPredicate<PsiClass, ProcessingContext> processor) {
        return processor.test(t.getContainingClass(), context);
      }
    });
  }

  public static class Capture extends PsiMemberPattern<PsiMember, Capture> {

    protected Capture() {
      super(new InitialPatternCondition<PsiMember>(PsiMember.class) {
        public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
          return o instanceof PsiMember;
        }
      });
    }
  }
}
