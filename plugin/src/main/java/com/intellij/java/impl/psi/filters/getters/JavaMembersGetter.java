/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.filters.getters;

import com.intellij.java.impl.codeInsight.completion.*;
import com.intellij.java.impl.codeInsight.lookup.VariableLookupItem;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.ast.IElementType;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.TailType;
import consulo.language.editor.completion.lookup.TailTypeDecorator;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author peter
 */
public class JavaMembersGetter extends MembersGetter {
  private final PsiType myExpectedType;

  public JavaMembersGetter(@Nonnull PsiType expectedType, CompletionParameters parameters) {
    super(new JavaStaticMemberProcessor(parameters), parameters.getPosition());
    myExpectedType = JavaCompletionUtil.originalize(expectedType);
  }

  public void addMembers(boolean searchInheritors, final Consumer<LookupElement> results) {
    if (myExpectedType instanceof PsiPrimitiveType && PsiType.DOUBLE.isAssignableFrom(myExpectedType)) {
      addConstantsFromTargetClass(results, searchInheritors);
      addConstantsFromReferencedClassesInSwitch(results);
    }

    if (myPlace.getParent().getParent() instanceof PsiSwitchLabelStatement) {
      return; //non-enum values are processed above, enum values will be suggested by reference completion
    }

    final PsiClass psiClass = PsiUtil.resolveClassInType(myExpectedType);
    processMembers(results, psiClass, PsiTreeUtil.getParentOfType(myPlace, PsiAnnotation.class) == null, searchInheritors);
  }

  private void addConstantsFromReferencedClassesInSwitch(final Consumer<LookupElement> results) {
    final Set<PsiField> fields = ReferenceExpressionCompletionContributor.findConstantsUsedInSwitch(myPlace);
    final Set<PsiClass> classes = new HashSet<PsiClass>();
    for (PsiField field : fields) {
      ContainerUtil.addIfNotNull(classes, field.getContainingClass());
    }
    for (PsiClass aClass : classes) {
      processMembers(new Consumer<LookupElement>() {
        @Override
        public void accept(LookupElement element) {
          //noinspection SuspiciousMethodCalls
          if (!fields.contains(element.getObject())) {
            results.accept(TailTypeDecorator.withTail(element, TailType.CASE_COLON));
          }
        }
      }, aClass, true, false);
    }
  }

  private void addConstantsFromTargetClass(Consumer<LookupElement> results, boolean searchInheritors) {
    PsiElement parent = myPlace.getParent();
    if (!(parent instanceof PsiReferenceExpression)) {
      return;
    }

    PsiElement prev = parent;
    parent = parent.getParent();
    while (parent instanceof PsiBinaryExpression) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)parent;
      final IElementType op = binaryExpression.getOperationTokenType();
      if (JavaTokenType.EQEQ == op || JavaTokenType.NE == op) {
        if (prev == binaryExpression.getROperand()) {
          processMembers(results, getCalledClass(binaryExpression.getLOperand()), true, searchInheritors
          );
        }
        return;
      }
      prev = parent;
      parent = parent.getParent();
    }
    if (parent instanceof PsiExpressionList) {
      processMembers(results, getCalledClass(parent.getParent()), true, searchInheritors);
    }
  }

  @Nullable
  private static PsiClass getCalledClass(@jakarta.annotation.Nullable PsiElement call) {
    if (call instanceof PsiMethodCallExpression) {
      for (final JavaResolveResult result : ((PsiMethodCallExpression)call).getMethodExpression().multiResolve(true)) {
        final PsiElement element = result.getElement();
        if (element instanceof PsiMethod) {
          final PsiClass aClass = ((PsiMethod)element).getContainingClass();
          if (aClass != null) {
            return aClass;
          }
        }
      }
    }
    if (call instanceof PsiNewExpression) {
      final PsiJavaCodeReferenceElement reference = ((PsiNewExpression)call).getClassReference();
      if (reference != null) {
        for (final JavaResolveResult result : reference.multiResolve(true)) {
          final PsiElement element = result.getElement();
          if (element instanceof PsiClass) {
            return (PsiClass)element;
          }
        }
      }
    }
    return null;
  }

  @Override
  @jakarta.annotation.Nullable
  protected LookupElement createFieldElement(PsiField field) {
    if (!myExpectedType.isAssignableFrom(field.getType())) {
      return null;
    }

    return new VariableLookupItem(field, false);
  }

  @Override
  @jakarta.annotation.Nullable
  protected LookupElement createMethodElement(PsiMethod method) {
    PsiSubstitutor substitutor = SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(method, myExpectedType);
    PsiType type = substitutor.substitute(method.getReturnType());
    if (type == null || !myExpectedType.isAssignableFrom(type)) {
      return null;
    }


    JavaMethodCallElement item = new JavaMethodCallElement(method, false, false);
    item.setInferenceSubstitutor(substitutor, myPlace);
    return item;
  }
}
