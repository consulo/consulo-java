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
package consulo.java.impl.intelliLang;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.intelliLang.Configuration;
import consulo.language.Language;
import consulo.language.pattern.PatternCondition;
import consulo.language.pattern.StandardPatterns;
import consulo.language.psi.*;
import consulo.language.util.ProcessingContext;
import consulo.util.lang.Comparing;
import consulo.java.impl.intelliLang.util.AnnotationUtilEx;
import consulo.java.impl.intelliLang.util.PsiUtilEx;

import javax.annotation.Nonnull;

import static com.intellij.java.language.patterns.PsiJavaPatterns.literalExpression;

/**
 * Provides references to Language-IDs and RegExp enums for completion.
 */
@ExtensionImpl
public class LanguageReferenceProvider extends PsiReferenceContributor {

  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    final Configuration configuration = Configuration.getInstance();
    registrar.registerReferenceProvider(
      literalExpression().annotationParam(StandardPatterns.string().with(new PatternCondition<String>("isLanguageAnnotation") {
        @Override
        public boolean accepts(@Nonnull final String s, final ProcessingContext context) {
          return Comparing.equal(configuration.getAdvancedConfiguration().getLanguageAnnotationClass(), s);
        }
      }), "value").and(literalExpression().with(new PatternCondition<PsiLiteralExpression>("isStringLiteral") {
        @Override
        public boolean accepts(@Nonnull final PsiLiteralExpression expression, final ProcessingContext context) {
          return PsiUtilEx.isStringOrCharacterLiteral(expression);
        }
      })), new PsiReferenceProvider() {
        @Nonnull
        @Override
        public PsiReference[] getReferencesByElement(@Nonnull final PsiElement element, @Nonnull final ProcessingContext context) {
          return new PsiReference[]{new LanguageReference((PsiLiteralExpression)element)};
        }
      });
    registrar.registerReferenceProvider(literalExpression().with(new PatternCondition<PsiLiteralExpression>("isStringLiteral") {
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
          final PsiAnnotation[] annotations = AnnotationUtilEx.getAnnotationFrom(owner, configuration.getAdvancedConfiguration().getPatternAnnotationPair(), true);
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
