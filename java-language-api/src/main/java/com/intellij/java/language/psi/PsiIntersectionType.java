/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.language.psi;

import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import java.util.*;

/**
 * Intersection types arise in a process of computing least upper bound.
 *
 * @author ven
 */
public class PsiIntersectionType extends PsiType.Stub {
  private final PsiType[] myConjuncts;

  private PsiIntersectionType(@Nonnull PsiType[] conjuncts) {
    super(TypeAnnotationProvider.EMPTY);
    myConjuncts = conjuncts;
  }

  @Nonnull
  public static PsiType createIntersection(@Nonnull List<PsiType> conjuncts) {
    return createIntersection(conjuncts.toArray(createArray(conjuncts.size())));
  }

  @Nonnull
  public static PsiType createIntersection(PsiType... conjuncts) {
    return createIntersection(true, conjuncts);
  }

  @Nonnull
  public static PsiType createIntersection(boolean flatten, PsiType... conjuncts) {
    assert conjuncts.length > 0;
    if (flatten) {
      conjuncts = flattenAndRemoveDuplicates(conjuncts);
    }
    if (conjuncts.length == 1) {
      return conjuncts[0];
    }
    return new PsiIntersectionType(conjuncts);
  }

  private static PsiType[] flattenAndRemoveDuplicates(final PsiType[] conjuncts) {
    try {
      final Set<PsiType> flattenConjuncts = PsiCapturedWildcardType.guard.doPreventingRecursion(conjuncts, true, () -> flatten(conjuncts, new LinkedHashSet<>()));
      if (flattenConjuncts == null) {
        return conjuncts;
      }
      return flattenConjuncts.toArray(createArray(flattenConjuncts.size()));
    } catch (NoSuchElementException e) {
      throw new RuntimeException(Arrays.toString(conjuncts), e);
    }
  }

  public static Set<PsiType> flatten(PsiType[] conjuncts, Set<PsiType> types) {
    for (PsiType conjunct : conjuncts) {
      if (conjunct instanceof PsiIntersectionType) {
        PsiIntersectionType type = (PsiIntersectionType) conjunct;
        flatten(type.getConjuncts(), types);
      } else {
        types.add(conjunct);
      }
    }
    if (types.size() > 1) {
      PsiType[] array = types.toArray(createArray(types.size()));
      for (Iterator<PsiType> iterator = types.iterator(); iterator.hasNext(); ) {
        PsiType type = iterator.next();

        for (PsiType existing : array) {
          if (type != existing) {
            final boolean allowUncheckedConversion = type instanceof PsiClassType && ((PsiClassType) type).isRaw();
            if (TypeConversionUtil.isAssignable(type, existing, allowUncheckedConversion)) {
              iterator.remove();
              break;
            }
          }
        }
      }
      if (types.isEmpty()) {
        types.add(array[0]);
      }
    }
    return types;
  }

  @Nonnull
  public PsiType[] getConjuncts() {
    return myConjuncts;
  }

  @Nonnull
  @Override
  public String getPresentableText(final boolean annotated) {
    return StringUtil.join(myConjuncts, psiType -> psiType.getPresentableText(annotated), " & ");
  }

  @Nonnull
  @Override
  public String getCanonicalText(boolean annotated) {
    return myConjuncts[0].getCanonicalText(annotated);
  }

  @Nonnull
  @Override
  public String getInternalCanonicalText() {
    return StringUtil.join(myConjuncts, PsiType::getInternalCanonicalText, " & ");
  }

  @Override
  public boolean isValid() {
    for (PsiType conjunct : myConjuncts) {
      if (!conjunct.isValid()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean equalsToText(@Nonnull String text) {
    return false;
  }

  @Override
  public <A> A accept(@Nonnull PsiTypeVisitor<A> visitor) {
    return visitor.visitIntersectionType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return myConjuncts[0].getResolveScope();
  }

  @Override
  @Nonnull
  public PsiType[] getSuperTypes() {
    return myConjuncts;
  }

  @Nonnull
  public PsiType getRepresentative() {
    return myConjuncts[0];
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof PsiIntersectionType)) {
      return false;
    }
    final PsiType[] first = getConjuncts();
    final PsiType[] second = ((PsiIntersectionType) obj).getConjuncts();
    if (first.length != second.length) {
      return false;
    }
    //positional equality
    for (int i = 0; i < first.length; i++) {
      if (!first[i].equals(second[i])) {
        return false;
      }
    }

    return true;
  }

  @Override
  public int hashCode() {
    return myConjuncts[0].hashCode();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("PsiIntersectionType: ");
    for (int i = 0; i < myConjuncts.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(myConjuncts[i].getPresentableText());
    }
    return sb.toString();
  }

  public String getConflictingConjunctsMessage() {
    final PsiType[] conjuncts = getConjuncts();
    for (int i = 0; i < conjuncts.length; i++) {
      PsiClass conjunct = PsiUtil.resolveClassInClassTypeOnly(conjuncts[i]);
      if (conjunct != null && !conjunct.isInterface()) {
        for (int i1 = i + 1; i1 < conjuncts.length; i1++) {
          PsiClass oppositeConjunct = PsiUtil.resolveClassInClassTypeOnly(conjuncts[i1]);
          if (oppositeConjunct != null && !oppositeConjunct.isInterface()) {
            if (!conjunct.isInheritor(oppositeConjunct, true) && !oppositeConjunct.isInheritor(conjunct, true)) {
              return conjuncts[i].getPresentableText() + " and " + conjuncts[i1].getPresentableText();
            }
          }
        }
      }
    }
    return null;
  }
}