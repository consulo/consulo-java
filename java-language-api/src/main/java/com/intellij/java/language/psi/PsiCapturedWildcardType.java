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
import consulo.application.util.RecursionManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.util.lang.Comparing;
import consulo.application.util.RecursionGuard;

import org.jspecify.annotations.Nullable;

/**
 * @author ven
 */
public class PsiCapturedWildcardType extends PsiType.Stub {
  private final PsiWildcardType myExistential;
  private final PsiElement myContext;
  @Nullable
  private final PsiTypeParameter myParameter;

  private PsiType myUpperBound;

  public static PsiCapturedWildcardType create(PsiWildcardType existential, PsiElement context) {
    return create(existential, context, null);
  }

  public static PsiCapturedWildcardType create(PsiWildcardType existential, PsiElement context, @Nullable PsiTypeParameter parameter) {
    return new PsiCapturedWildcardType(existential, context, parameter);
  }

  private PsiCapturedWildcardType(PsiWildcardType existential, PsiElement context, @Nullable PsiTypeParameter parameter) {
    super(TypeAnnotationProvider.EMPTY);
    myExistential = existential;
    myContext = context;
    myParameter = parameter;
    myUpperBound = PsiType.getJavaLangObject(myContext.getManager(), getResolveScope());
  }

  public static RecursionGuard<Object> guard = RecursionManager.createGuard("captureGuard");

  public static boolean isCapture() {
    return guard.currentStack().isEmpty();
  }

  @Nullable
  public static PsiType captureUpperBound(PsiTypeParameter typeParameter, PsiWildcardType wildcardType, PsiSubstitutor captureSubstitutor) {
    final PsiType[] boundTypes = typeParameter.getExtendsListTypes();
    PsiType originalBound = !wildcardType.isSuper() ? wildcardType.getBound() : null;
    PsiType glb = originalBound;
    for (PsiType boundType : boundTypes) {
      final PsiType substitutedBoundType = captureSubstitutor.substitute(boundType);
      //glb for array types is not specified yet
      if (originalBound instanceof PsiArrayType &&
          substitutedBoundType instanceof PsiArrayType &&
          !originalBound.isAssignableFrom(substitutedBoundType) &&
          !substitutedBoundType.isAssignableFrom(originalBound)) {
        continue;
      }

      if (glb == null) {
        glb = substitutedBoundType;
      } else {
        glb = GenericsUtil.getGreatestLowerBound(glb, substitutedBoundType);
      }
    }

    return glb;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof PsiCapturedWildcardType)) {
      return false;
    }

    final PsiCapturedWildcardType captured = (PsiCapturedWildcardType) o;
    final PsiManager manager = myContext.getManager();
    if (!manager.areElementsEquivalent(myContext, captured.myContext)) {
      return false;
    }

    if ((myExistential.isSuper() || captured.myExistential.isSuper()) && !myExistential.equals(captured.myExistential)) {
      return false;
    }

    if ((myContext instanceof PsiReferenceExpression || myContext instanceof PsiMethodCallExpression) && !manager.areElementsEquivalent(myParameter, captured.myParameter)) {
      return false;
    }

    if (myParameter != null) {
      final Boolean sameUpperBounds = guard.doPreventingRecursion(myContext, true, () -> Comparing.equal(myUpperBound, captured.myUpperBound));

      if (sameUpperBounds == null || sameUpperBounds) {
        return true;
      }
    }
    return myExistential.equals(captured.myExistential);
  }

  @Override
  public int hashCode() {
    return myUpperBound.hashCode() + 31 * myContext.hashCode();
  }

  @Override
  public String getPresentableText(boolean annotated) {
    return "capture of " + myExistential.getPresentableText(annotated);
  }

  @Override
  public String getCanonicalText(boolean annotated) {
    return myExistential.getCanonicalText(annotated);
  }

  @Override
  public String getInternalCanonicalText() {
    return "capture<" + myExistential.getInternalCanonicalText() + '>';
  }

  @Override
  public boolean isValid() {
    return myExistential.isValid() && myContext.isValid();
  }

  @Override
  public boolean equalsToText(String text) {
    return false;
  }

  @Override
  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitCapturedWildcardType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return myExistential.getResolveScope();
  }

  @Override
  public PsiType[] getSuperTypes() {
    return myExistential.getSuperTypes();
  }

  public PsiType getLowerBound() {
    return myExistential.isSuper() ? myExistential.getBound() : NULL;
  }

  public PsiType getUpperBound() {
    return getUpperBound(true);
  }

  public PsiType getUpperBound(boolean capture) {
    final PsiType bound = myExistential.getBound();
    if (myExistential.isExtends() && myParameter == null) {
      assert bound != null : myExistential.getCanonicalText();
      return bound;
    } else {
      return isCapture() && capture ? PsiUtil.captureToplevelWildcards(myUpperBound, myContext) : myUpperBound;
    }
  }

  public void setUpperBound(PsiType upperBound) {
    myUpperBound = upperBound;
  }

  public PsiWildcardType getWildcard() {
    return myExistential;
  }

  public PsiElement getContext() {
    return myContext;
  }

  public PsiTypeParameter getTypeParameter() {
    return myParameter;
  }
}