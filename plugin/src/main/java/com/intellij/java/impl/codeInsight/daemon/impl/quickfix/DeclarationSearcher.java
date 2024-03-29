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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

public class DeclarationSearcher {
  private final PsiMethod myMethod;
  private final PsiType myTargetType;

  private final Map<PsiElement, PsiVariable> cache;

  public DeclarationSearcher(final PsiMethod method, final PsiType targetType) {
    myMethod = method;
    myTargetType = targetType;

    cache = new HashMap<PsiElement, PsiVariable>();
  }

  @Nullable
  public PsiVariable getDeclaration(@Nonnull PsiElement endPositionElement) {
    final PsiVariable localVariable = getLocalDeclaration(endPositionElement);
    if (localVariable != null) {
      return localVariable;
    }
    return getParameterDeclaration();
  }

  @Nullable
  private PsiVariable getParameterDeclaration() {
    for (PsiParameter parameter : myMethod.getParameterList().getParameters()) {
      if (myTargetType.equals(parameter.getType())) {
        return goThroughCache(myMethod, parameter);
      }
    }
    return null;
  }

  @Nullable
  private PsiVariable getLocalDeclaration(@Nonnull PsiElement endPositionElement) {
    final PsiElement parent = endPositionElement.getParent();

    // reuse of cache is possible IF requests are done up-to-down. otherwise - not first declaration can be returned
    final PsiVariable cachedCandidate = cache.get(parent);
    if (cachedCandidate != null) {
      return cachedCandidate;
    }

    if (parent != myMethod) {
      // go up
      final PsiVariable parentResult = getLocalDeclaration(parent);
      if (parentResult != null) {
        return parentResult;
      }
    }

    // look self
    for (PsiElement element : parent.getChildren()) {
      if (element == endPositionElement) {
        break;
      }
      if (element instanceof PsiDeclarationStatement) {
        final PsiElement[] declared = ((PsiDeclarationStatement) element).getDeclaredElements();
        for (final PsiElement declaredElement : declared) {
          if (declaredElement instanceof PsiLocalVariable && myTargetType.equals(((PsiLocalVariable)declaredElement).getType())) {
            return goThroughCache(parent, (PsiVariable) declaredElement);
          }
        }
      } else if (element instanceof PsiParameter) {
        // foreach
        if (myTargetType.equals(((PsiParameter) element).getType())) {
          return goThroughCache(parent, (PsiVariable) element);
        }
      }
    }

    return null;
  }

  private PsiVariable goThroughCache(final PsiElement parent, final PsiVariable variable) {
    cache.put(parent, variable);
    return variable;
  }
}
