/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.completion;

import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementWeigher;
import com.intellij.java.analysis.impl.codeInsight.JavaPsiEquivalenceUtil;
import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.java.impl.psi.filters.element.ExcludeSillyAssignment;
import com.intellij.java.indexing.impl.search.MethodDeepestSuperSearcher;
import com.intellij.java.language.impl.psi.scope.ElementClassFilter;
import com.intellij.java.language.patterns.PsiJavaPatterns;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.util.lang.Comparing;
import consulo.language.pattern.StandardPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.filter.AndFilter;
import consulo.language.psi.filter.ClassFilter;
import consulo.language.psi.filter.ElementFilter;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.application.util.function.CommonProcessors;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

/**
 * @author peter
 */
class RecursionWeigher extends LookupElementWeigher {
  private final ElementFilter myFilter;
  private final PsiElement myPosition;
  private final PsiReferenceExpression myReference;
  @Nullable
  private final PsiMethodCallExpression myExpression;
  private final PsiMethod myPositionMethod;
  private final ExpectedTypeInfo[] myExpectedInfos;
  private final PsiExpression myCallQualifier;
  private final PsiExpression myPositionQualifier;
  private final boolean myDelegate;
  private final CompletionType myCompletionType;

  public RecursionWeigher(PsiElement position,
                          CompletionType completionType,
                          @jakarta.annotation.Nonnull PsiReferenceExpression reference,
                          @jakarta.annotation.Nullable PsiMethodCallExpression expression,
                          ExpectedTypeInfo[] expectedInfos) {
    super("recursion");
    myCompletionType = completionType;
    myFilter = recursionFilter(position);
    myPosition = position;
    myReference = reference;
    myExpression = expression;
    myPositionMethod = PsiTreeUtil.getParentOfType(position, PsiMethod.class, false);
    myExpectedInfos = expectedInfos;
    myCallQualifier = normalizeQualifier(myReference.getQualifierExpression());
    myPositionQualifier = normalizeQualifier(position.getParent() instanceof PsiJavaCodeReferenceElement ? ((PsiJavaCodeReferenceElement) position.getParent()).getQualifier() : null);
    myDelegate = isDelegatingCall();
  }

  @Nullable
  private static PsiExpression normalizeQualifier(@Nullable PsiElement qualifier) {
    return qualifier instanceof PsiThisExpression || !(qualifier instanceof PsiExpression) ? null : (PsiExpression) qualifier;
  }

  private boolean isDelegatingCall() {
    if (myCallQualifier != null && myPositionQualifier != null && myCallQualifier != myPositionQualifier && JavaPsiEquivalenceUtil.areExpressionsEquivalent(myCallQualifier, myPositionQualifier)) {
      return false;
    }

    if (myCallQualifier == null && myPositionQualifier == null) {
      return false;
    }

    return true;
  }

  @Nullable
  static ElementFilter recursionFilter(PsiElement element) {
    if (PsiJavaPatterns.psiElement().afterLeaf(PsiKeyword.RETURN).inside(PsiReturnStatement.class).accepts(element)) {
      return new ExcludeDeclaredFilter(ElementClassFilter.METHOD);
    }

    if (PsiJavaPatterns.psiElement().inside(StandardPatterns.or(PsiJavaPatterns.psiElement(PsiAssignmentExpression.class), PsiJavaPatterns.psiElement(PsiVariable.class))).
        andNot(PsiJavaPatterns.psiElement().afterLeaf(".")).accepts(element)) {
      return new AndFilter(new ExcludeSillyAssignment(), new ExcludeDeclaredFilter(new ClassFilter(PsiVariable.class)));
    }
    return null;
  }

  private enum Result {
    delegation,
    normal,
    passingObjectToItself,
    recursive,
  }

  @Nonnull
  @Override
  public Result weigh(@jakarta.annotation.Nonnull LookupElement element) {
    final Object object = element.getObject();
    if (!(object instanceof PsiMethod || object instanceof PsiVariable || object instanceof PsiExpression)) {
      return Result.normal;
    }

    if (myFilter != null && !myFilter.isAcceptable(object, myPosition)) {
      return Result.recursive;
    }

    if (isPassingObjectToItself(object) && myCompletionType == CompletionType.SMART) {
      return Result.passingObjectToItself;
    }

    if (myExpectedInfos != null) {
      final PsiType itemType = JavaCompletionUtil.getLookupElementType(element);
      if (itemType != null) {
        boolean hasRecursiveInvocations = false;
        boolean hasOtherInvocations = false;

        for (final ExpectedTypeInfo expectedInfo : myExpectedInfos) {
          PsiMethod calledMethod = expectedInfo.getCalledMethod();
          if (!expectedInfo.getType().isAssignableFrom(itemType)) {
            continue;
          }

          if (calledMethod != null && calledMethod.equals(myPositionMethod) || isGetterSetterAssignment(object, calledMethod)) {
            hasRecursiveInvocations = true;
          } else if (calledMethod != null) {
            hasOtherInvocations = true;
          }
        }
        if (hasRecursiveInvocations && !hasOtherInvocations) {
          return myDelegate ? Result.delegation : Result.recursive;
        }
      }
    }
    if (myExpression != null) {
      return Result.normal;
    }

    if (object instanceof PsiMethod && myPositionMethod != null) {
      final PsiMethod method = (PsiMethod) object;
      if (PsiTreeUtil.isAncestor(myReference, myPosition, false) && Comparing.equal(method.getName(), myPositionMethod.getName())) {
        if (!myDelegate && findDeepestSuper(method).equals(findDeepestSuper(myPositionMethod))) {
          return Result.recursive;
        }
        return Result.delegation;
      }
    }

    return Result.normal;
  }

  @jakarta.annotation.Nullable
  private String getSetterPropertyName(@jakarta.annotation.Nullable PsiMethod calledMethod) {
    if (PropertyUtil.isSimplePropertySetter(calledMethod)) {
      assert calledMethod != null;
      return PropertyUtil.getPropertyName(calledMethod);
    }
    PsiReferenceExpression reference = ExcludeSillyAssignment.getAssignedReference(myPosition);
    if (reference != null) {
      PsiElement target = reference.resolve();
      if (target instanceof PsiField) {
        return PropertyUtil.suggestPropertyName((PsiField) target);
      }
    }
    return null;
  }

  private boolean isGetterSetterAssignment(Object lookupObject, @Nullable PsiMethod calledMethod) {
    String prop = getSetterPropertyName(calledMethod);
    if (prop == null) {
      return false;
    }

    if (lookupObject instanceof PsiField && prop.equals(PropertyUtil.suggestPropertyName((PsiField) lookupObject))) {
      return true;
    }
    if (lookupObject instanceof PsiMethod && PropertyUtil.isSimplePropertyGetter((PsiMethod) lookupObject) && prop.equals(PropertyUtil.getPropertyName((PsiMethod) lookupObject))) {
      return true;
    }
    return false;
  }

  private boolean isPassingObjectToItself(Object object) {
    if (object instanceof PsiThisExpression) {
      return myCallQualifier != null && !myDelegate || myCallQualifier instanceof PsiSuperExpression;
    }
    return myCallQualifier instanceof PsiReferenceExpression && object.equals(((PsiReferenceExpression) myCallQualifier).advancedResolve(true).getElement());
  }

  @jakarta.annotation.Nonnull
  public static PsiMethod findDeepestSuper(@jakarta.annotation.Nonnull final PsiMethod method) {
    CommonProcessors.FindFirstProcessor<PsiMethod> processor = new CommonProcessors.FindFirstProcessor<>();
    MethodDeepestSuperSearcher.processDeepestSuperMethods(method, processor);
    final PsiMethod first = processor.getFoundValue();
    return first == null ? method : first;
  }
}
