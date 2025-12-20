/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.typeMigration;

import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.Queue;
import consulo.util.collection.SmartList;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * @author db
 */
public class Util {
  private Util() {
  }

  public static PsiElement getEssentialParent(PsiElement element) {
    PsiElement parent = element.getParent();

    if (parent instanceof PsiParenthesizedExpression) {
      return getEssentialParent(parent);
    }

    return parent;
  }

  @Nullable
  public static PsiElement normalizeElement(PsiElement element) {
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      PsiType initialMethodReturnType = method.getReturnType();
      if (initialMethodReturnType == null) {
        return null;
      }
      List<PsiMethod> normalized = new SmartList<>();
      Queue<PsiMethod> queue = new Queue<>(1);
      queue.addLast(method);
      while (!queue.isEmpty()) {
        PsiMethod currentMethod = queue.pullFirst();
        if (initialMethodReturnType.equals(currentMethod.getReturnType())) {
          for (PsiMethod toConsume : currentMethod.findSuperMethods(false)) {
            queue.addLast(toConsume);
          }
          normalized.add(currentMethod);
        }
      }
      //TODO Dmitry Batkovich multiple result is possible
      return normalized.isEmpty() ? element : normalized.get(normalized.size() - 1);
    } else if (element instanceof PsiParameter && element.getParent() instanceof PsiParameterList) {
      PsiElement declarationScope = ((PsiParameter) element).getDeclarationScope();
      if (declarationScope instanceof PsiLambdaExpression) {
        PsiType interfaceType = ((PsiLambdaExpression) declarationScope).getFunctionalInterfaceType();
        if (interfaceType != null) {
          PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(interfaceType);
          if (interfaceMethod != null) {
            int index = ((PsiParameterList) element.getParent()).getParameterIndex((PsiParameter) element);
            return interfaceMethod.getParameterList().getParameters()[index];
          }
        }
        return null;
      }
      PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

      if (method != null) {
        int index = method.getParameterList().getParameterIndex(((PsiParameter) element));
        PsiMethod superMethod = method.findDeepestSuperMethod();

        if (superMethod != null) {
          return superMethod.getParameterList().getParameters()[index];
        }
      }
    }

    return element;
  }

  public static boolean canBeMigrated(@Nonnull PsiElement[] es) {
    return Arrays.stream(es).allMatch(Util::canBeMigrated);
  }

  private static boolean canBeMigrated(@Nullable PsiElement e) {
    if (e == null) {
      return false;
    }

    PsiElement element = normalizeElement(e);

    if (element == null || !element.isWritable()) {
      return false;
    }

    PsiType type = TypeMigrationLabeler.getElementType(element);

    if (type != null) {
      PsiType elementType = type instanceof PsiArrayType ? type.getDeepComponentType() : type;

      if (elementType instanceof PsiPrimitiveType) {
        return !elementType.equals(PsiType.VOID);
      }

      if (elementType instanceof PsiClassType) {
        PsiClass aClass = ((PsiClassType) elementType).resolve();
        return aClass != null;
      } else if (elementType instanceof PsiDisjunctionType) {
        return true;
      }
    }

    return false;
  }
}
