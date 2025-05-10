/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.impl.source.resolve.reference.impl;

import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.patterns.PsiJavaElementPattern;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.pattern.ElementPattern;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceContributor;
import consulo.language.psi.PsiReferenceRegistrar;
import consulo.language.util.ProcessingContext;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static com.intellij.java.analysis.impl.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;
import static com.intellij.java.language.patterns.PsiJavaPatterns.psiLiteral;
import static com.intellij.java.language.patterns.PsiJavaPatterns.psiMethod;
import static consulo.language.pattern.StandardPatterns.or;

/**
 * @author Konstantin Bulenkov
 */
@ExtensionImpl
public class JavaReflectionReferenceContributor extends PsiReferenceContributor {
  public static final PsiJavaElementPattern.Capture<PsiLiteral> PATTERN = psiLiteral().methodCallParameter(psiMethod().withName(GET_FIELD, GET_DECLARED_FIELD, GET_METHOD, GET_DECLARED_METHOD)
      .definedInClass(CommonClassNames.JAVA_LANG_CLASS));

  public static final PsiJavaElementPattern.Capture<PsiLiteral> CLASS_PATTERN = psiLiteral().methodCallParameter(or(psiMethod().withName(FOR_NAME).definedInClass(
      CommonClassNames.JAVA_LANG_CLASS), psiMethod()
      .withName(LOAD_CLASS).definedInClass(JAVA_LANG_CLASS_LOADER)));

  private static final ElementPattern<? extends PsiElement> METHOD_HANDLE_PATTERN = psiLiteral().methodCallParameter(1, psiMethod().withName(HANDLE_FACTORY_METHOD_NAMES).definedInClass
      (JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP));

  @Override
  public void registerReferenceProviders(@Nonnull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PATTERN, new JavaReflectionReferenceProvider() {
      @Nullable
      @Override
      protected PsiReference[] getReferencesByMethod(@Nonnull PsiLiteralExpression literalArgument, @Nonnull PsiReferenceExpression methodReference, @Nonnull ProcessingContext context) {

        final PsiExpression qualifier = methodReference.getQualifierExpression();
        return qualifier != null ? new PsiReference[]{new JavaLangClassMemberReference(literalArgument, qualifier)} : null;
      }
    });

    registrar.registerReferenceProvider(CLASS_PATTERN, new JavaReflectionReferenceProvider() {
      @Nullable
      @Override
      protected PsiReference[] getReferencesByMethod(@Nonnull PsiLiteralExpression literalArgument, @Nonnull PsiReferenceExpression methodReference, @Nonnull ProcessingContext context) {

        final String referenceName = methodReference.getReferenceName();
        if (FOR_NAME.equals(referenceName) || LOAD_CLASS.equals(referenceName)) {
          return new JavaClassReferenceProvider().getReferencesByElement(literalArgument, context);
        }
        return null;
      }
    });

    registrar.registerReferenceProvider(METHOD_HANDLE_PATTERN, new JavaLangInvokeHandleReference.JavaLangInvokeHandleReferenceProvider());
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
