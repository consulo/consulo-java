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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.util.function.Processor;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.PatternCondition;
import consulo.language.pattern.PatternConditionPlus;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.ref.Ref;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import java.util.function.BiPredicate;

/**
 * @author peter
 */
public class PsiMethodPattern extends PsiMemberPattern<PsiMethod,PsiMethodPattern> {
  public PsiMethodPattern() {
    super(PsiMethod.class);
  }

  public PsiMethodPattern withParameterCount(@NonNls final int paramCount) {
    return with(new PatternCondition<PsiMethod>("withParameterCount") {
      @Override
      public boolean accepts(@Nonnull final PsiMethod method, final ProcessingContext context) {
        return method.getParameterList().getParametersCount() == paramCount;
      }
    });
  }

  /**
   * Selects the corrected method by argument types
   * @param inputTypes the array of FQN of the parameter types or wildcards.
   * The special values are:<bl><li>"?" - means any type</li><li>".." - instructs pattern to accept the rest of the arguments</li></bl>
   * @return
   */
  public PsiMethodPattern withParameters(@NonNls final String... inputTypes) {
    final String[] types = inputTypes.length == 0 ? ArrayUtil.EMPTY_STRING_ARRAY : inputTypes;
    return with(new PatternCondition<PsiMethod>("withParameters") {
      @Override
      public boolean accepts(@Nonnull final PsiMethod psiMethod, final ProcessingContext context) {
        final PsiParameterList parameterList = psiMethod.getParameterList();
        int dotsIndex = -1;
        while (++dotsIndex <types.length) {
          if (Comparing.equal("..", types[dotsIndex])) break;
        }

        if (dotsIndex == types.length && parameterList.getParametersCount() != dotsIndex
          || dotsIndex < types.length && parameterList.getParametersCount() < dotsIndex) {
          return false;
        }
        if (dotsIndex > 0) {
          final PsiParameter[] psiParameters = parameterList.getParameters();
          for (int i = 0; i < dotsIndex; i++) {
            if (!Comparing.equal("?", types[i]) && !typeEquivalent(psiParameters[i].getType(), types[i])) {
              return false;
            }
          }
        }
        return true;
      }

      private boolean typeEquivalent(PsiType type, String expectedText) {
        final PsiType erasure = TypeConversionUtil.erasure(type);
        final String text;
        if (erasure instanceof PsiEllipsisType && expectedText.endsWith("[]")) {
          text = ((PsiEllipsisType)erasure).getComponentType().getCanonicalText() + "[]";
        }
        else if (erasure instanceof PsiArrayType && expectedText.endsWith("...")) {
          text = ((PsiArrayType)erasure).getComponentType().getCanonicalText() +"...";
        }
        else {
          text = erasure.getCanonicalText();
        }
        return expectedText.equals(text);
      }
    });
  }

  public PsiMethodPattern definedInClass(@NonNls final String qname) {
    return definedInClass(PsiJavaPatterns.psiClass().withQualifiedName(qname));
  }

  public PsiMethodPattern definedInClass(final ElementPattern<? extends PsiClass> pattern) {
    return with(new PatternConditionPlus<PsiMethod, PsiClass>("definedInClass", pattern) {

      @Override
      public boolean processValues(PsiMethod t, final ProcessingContext context, final BiPredicate<PsiClass, ProcessingContext> processor) {
        if (!processor.test(t.getContainingClass(), context)) return false;
        final Ref<Boolean> result = Ref.create(Boolean.TRUE);
        SuperMethodsSearch.search(t, null, true, false).forEach(new Processor<MethodSignatureBackedByPsiMethod>() {
          @Override
          public boolean process(final MethodSignatureBackedByPsiMethod signature) {
            if (!processor.test(signature.getMethod().getContainingClass(), context)) {
              result.set(Boolean.FALSE);
              return false;
            }
            return true;
          }
        });
        return result.get();
      }
    });
  }

  public PsiMethodPattern constructor(final boolean isConstructor) {
    return with(new PatternCondition<PsiMethod>("constructor") {
      @Override
      public boolean accepts(@Nonnull final PsiMethod method, final ProcessingContext context) {
        return method.isConstructor() == isConstructor;
      }
    });
  }


  public PsiMethodPattern withThrowsList(final ElementPattern<?> pattern) {
    return with(new PatternCondition<PsiMethod>("withThrowsList") {
      @Override
      public boolean accepts(@Nonnull final PsiMethod method, final ProcessingContext context) {
        return pattern.accepts(method.getThrowsList());
      }
    });
  }
}