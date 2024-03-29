package com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.PsiLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.analysis.impl.util.JavaI18nUtil;
import consulo.language.Language;
import consulo.language.pattern.FilterPattern;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReferenceContributor;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.psi.PsiReferenceRegistrar;
import consulo.language.psi.filter.ElementFilter;

import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
@ExtensionImpl
public class JavaReferenceContributor extends PsiReferenceContributor {
  public void registerReferenceProviders(final PsiReferenceRegistrar registrar) {
    final PsiReferenceProvider filePathReferenceProvider = new FilePathReferenceProvider();
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class).and(new FilterPattern(new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        PsiLiteralExpression literalExpression = (PsiLiteralExpression) context;
        final Map<String, Object> annotationParams = new HashMap<String, Object>();
        annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
        return !JavaI18nUtil.mustBePropertyKey(context.getProject(), literalExpression, annotationParams);
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    })), filePathReferenceProvider);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
