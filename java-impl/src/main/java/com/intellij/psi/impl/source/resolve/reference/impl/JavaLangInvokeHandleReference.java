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
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static com.intellij.java.analysis.impl.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Pavel.Dolgov
 */
public class JavaLangInvokeHandleReference extends PsiReferenceBase<PsiLiteralExpression> implements InsertHandler<LookupElement> {

  private final PsiExpression myContext;

  public JavaLangInvokeHandleReference(@Nonnull PsiLiteralExpression literal, @Nonnull PsiExpression context) {
    super(literal);
    myContext = context;
  }

  @Override
  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    return element;
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    final Object value = myElement.getValue();
    if (value instanceof String) {
      final String name = (String) value;
      final String type = getMemberType(myElement);

      if (type != null) {
        final ReflectiveClass reflectiveClass = getReflectiveClass(myContext);
        if (reflectiveClass != null) {
          switch (type) {
            case FIND_GETTER:
            case FIND_SETTER:
              return resolveField(name, reflectiveClass.getPsiClass(), JavaLangInvokeHandleReference::isNonStaticField);

            case FIND_STATIC_GETTER:
            case FIND_STATIC_SETTER:
              return resolveField(name, reflectiveClass.getPsiClass(), JavaLangInvokeHandleReference::isStaticField);

            case FIND_VIRTUAL:
              return resolveMethod(name, reflectiveClass.getPsiClass(), JavaLangInvokeHandleReference::isNonStaticMethod);
            case FIND_STATIC:
              return resolveMethod(name, reflectiveClass.getPsiClass(), JavaLangInvokeHandleReference::isStaticMethod);

            case FIND_VAR_HANDLE:
              return resolveField(name, reflectiveClass.getPsiClass(), JavaLangInvokeHandleReference::isNonStaticField);

            case FIND_STATIC_VAR_HANDLE:
              return resolveField(name, reflectiveClass.getPsiClass(), JavaLangInvokeHandleReference::isStaticField);
          }
        }
      }
    }
    return null;
  }

  private static PsiElement resolveField(@Nonnull String name, @Nonnull PsiClass psiClass, Condition<? super PsiField> filter) {
    final PsiField field = psiClass.findFieldByName(name, true);
    return field != null && filter.value(field) ? field : null;
  }

  private static PsiElement resolveMethod(@Nonnull String name, @Nonnull PsiClass psiClass, Condition<? super PsiMethod> filter) {
    final PsiMethod[] methods = psiClass.findMethodsByName(name, true);
    return ContainerUtil.find(methods, filter);
  }

  @Nonnull
  @Override
  public Object[] getVariants() {
    final Object value = myElement.getValue();
    if (value instanceof String) {
      final String type = getMemberType(myElement);

      if (type != null) {
        final ReflectiveClass reflectiveClass = getReflectiveClass(myContext);
        if (reflectiveClass != null) {
          switch (type) {
            case FIND_GETTER:
            case FIND_SETTER:
              return lookupFields(reflectiveClass.getPsiClass(), JavaLangInvokeHandleReference::isNonStaticField);
            case FIND_STATIC_GETTER:
            case FIND_STATIC_SETTER:
              return lookupFields(reflectiveClass.getPsiClass(), JavaLangInvokeHandleReference::isStaticField);

            case FIND_VIRTUAL:
              return lookupMethods(reflectiveClass.getPsiClass(), JavaLangInvokeHandleReference::isNonStaticMethod);
            case FIND_STATIC:
              return lookupMethods(reflectiveClass.getPsiClass(), JavaLangInvokeHandleReference::isStaticMethod);

            case FIND_VAR_HANDLE:
              return lookupFields(reflectiveClass.getPsiClass(), JavaLangInvokeHandleReference::isNonStaticField);
            case FIND_STATIC_VAR_HANDLE:
              return lookupFields(reflectiveClass.getPsiClass(), JavaLangInvokeHandleReference::isStaticField);
          }
        }
      }
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private Object[] lookupMethods(@Nonnull PsiClass psiClass, Predicate<? super PsiMethod> filter) {
    return psiClass.getVisibleSignatures().stream().map(MethodSignatureBackedByPsiMethod::getMethod).filter(filter).sorted(Comparator.comparingInt((PsiMethod method) -> getMethodSortOrder
        (method)).thenComparing(PsiMethod::getName)).map(method -> withPriority(JavaLookupElementBuilder.forMethod(method, PsiSubstitutor.EMPTY).withInsertHandler(this), -getMethodSortOrder
        (method))).toArray();
  }

  private Object[] lookupFields(@Nonnull PsiClass psiClass, Predicate<? super PsiField> filter) {
    final Set<String> uniqueNames = new HashSet<>();
    return Arrays.stream(psiClass.getAllFields()).filter(field -> field != null && (field.getContainingClass() == psiClass || !field.hasModifierProperty(PsiModifier.PRIVATE)) && field.getName()
        != null && uniqueNames.add(field.getName())).filter(filter).sorted(Comparator.comparing((PsiField field) -> isPublic(field) ? 0 : 1).thenComparing(PsiField::getName)).map(field ->
        withPriority(JavaLookupElementBuilder.forField(field).withInsertHandler(this), isPublic(field))).toArray();
  }

  private static boolean isNonStaticField(PsiField field) {
    return field != null && !field.hasModifierProperty(PsiModifier.STATIC);
  }

  private static boolean isStaticField(PsiField field) {
    return field != null && field.hasModifierProperty(PsiModifier.STATIC);
  }

  private static boolean isNonStaticMethod(@Nullable PsiMethod method) {
    return isRegularMethod(method) && !method.hasModifierProperty(PsiModifier.STATIC);
  }

  private static boolean isStaticMethod(@Nullable PsiMethod method) {
    return isRegularMethod(method) && method.hasModifierProperty(PsiModifier.STATIC);
  }

  @Override
  public void handleInsert(@Nonnull InsertionContext context, @Nonnull LookupElement item) {
    final Object object = item.getObject();

    if (object instanceof PsiMethod) {
      final ReflectiveSignature signature = getMethodSignature((PsiMethod) object);
      if (signature != null) {
        final String text = ", " + getMethodTypeExpressionText(signature);
        replaceText(context, text);
      }
    } else if (object instanceof PsiField) {
      final PsiField field = (PsiField) object;
      final String typeText = getTypeText(field.getType());
      final String text = ", " + typeText + ".class";
      replaceText(context, text);
    }
  }

  static class JavaLangInvokeHandleReferenceProvider extends PsiReferenceProvider {
    @Nonnull
    @Override
    public PsiReference[] getReferencesByElement(@Nonnull PsiElement element, @Nonnull ProcessingContext context) {
      if (element instanceof PsiLiteralExpression) {
        final PsiLiteralExpression literal = (PsiLiteralExpression) element;
        if (literal.getValue() instanceof String) {
          final PsiElement parent = element.getParent();
          if (parent instanceof PsiExpressionList) {
            final PsiExpression[] expressions = ((PsiExpressionList) parent).getExpressions();
            final PsiExpression qualifier = expressions.length != 0 ? expressions[0] : null;
            if (qualifier != null) {
              return new PsiReference[]{new JavaLangInvokeHandleReference(literal, qualifier)};
            }
          }
        }
      }
      return PsiReference.EMPTY_ARRAY;
    }
  }
}
