/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.java.regexp.impl;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.patterns.PsiJavaPatterns;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.impl.intelliLang.util.AnnotationUtilEx;
import consulo.java.impl.intelliLang.util.PsiUtilEx;
import consulo.language.Language;
import consulo.language.inject.advanced.Configuration;
import consulo.language.pattern.PatternCondition;
import consulo.language.psi.*;
import consulo.language.util.ProcessingContext;
import jakarta.annotation.Nonnull;

/**
 * Provides references to RegExp enums for completion.
 */
@ExtensionImpl
public class RegExpLanguageReferenceProvider extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@Nonnull PsiReferenceRegistrar registrar) {
    final Configuration configuration = Configuration.getInstance();
    registrar.registerReferenceProvider(PsiJavaPatterns.literalExpression().with(new PatternCondition<PsiLiteralExpression>("isStringLiteral") {
      @Override
      public boolean accepts(@Nonnull final PsiLiteralExpression expression, final ProcessingContext context) {
        return PsiUtilEx.isStringOrCharacterLiteral(expression);
      }
    }), new PsiReferenceProvider() {
      @Nonnull
      @Override
      public PsiReference[] getReferencesByElement(@Nonnull PsiElement psiElement, @Nonnull ProcessingContext context) {
        final PsiLiteralExpression expression = (PsiLiteralExpression)psiElement;
        final PsiModifierListOwner owner =
          AnnotationUtilEx.getAnnotatedElementFor(expression, AnnotationUtilEx.LookupType.PREFER_DECLARATION);
        if (owner != null && PsiUtilEx.isLanguageAnnotationTarget(owner)) {
          final PsiAnnotation[] annotations =
            AnnotationUtilEx.getAnnotationFrom(owner, configuration.getAdvancedConfiguration().getPatternAnnotationPair(), true);
          if (annotations.length > 0) {
            final String pattern = AnnotationUtilEx.calcAnnotationValue(annotations, "value");
            if (pattern != null) {
              return new PsiReference[]{new RegExpEnumReference(expression, pattern)};
            }
          }
        }
        return PsiReference.EMPTY_ARRAY;
      }
    });
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
