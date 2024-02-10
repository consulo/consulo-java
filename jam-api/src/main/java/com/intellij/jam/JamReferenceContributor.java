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
package com.intellij.jam;

import com.intellij.jam.reflect.JamAnnotationMeta;
import com.intellij.jam.reflect.JamAttributeMeta;
import com.intellij.jam.reflect.JamStringAttributeMeta;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.patterns.PsiJavaElementPattern;
import com.intellij.java.language.patterns.PsiJavaPatterns;
import com.intellij.java.language.patterns.PsiNameValuePairPattern;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import java.util.List;

import static com.intellij.java.language.patterns.PsiJavaPatterns.psiLiteral;
import static com.intellij.java.language.patterns.PsiJavaPatterns.psiNameValuePair;

/**
 * @author peter
 */
@ExtensionImpl
public class JamReferenceContributor extends PsiReferenceContributor {
  private static final PsiNameValuePairPattern NAME_VALUE_PAIR = psiNameValuePair().withParent(PsiAnnotationParameterList.class);
  public static final PsiJavaElementPattern.Capture<PsiLiteral> STRING_IN_ANNO = psiLiteral().withParent(
    PsiJavaPatterns.or(NAME_VALUE_PAIR,
      PsiJavaPatterns.psiElement(PsiArrayInitializerMemberValue.class).withParent(NAME_VALUE_PAIR)
    ));

  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(STRING_IN_ANNO, new PsiReferenceProvider() {
      @Nonnull
      @Override
      public PsiReference[] getReferencesByElement(@Nonnull PsiElement element, @Nonnull ProcessingContext context) {
        final PsiNameValuePair pair = PsiTreeUtil.getParentOfType(element, PsiNameValuePair.class);
        final PsiAnnotation anno = (PsiAnnotation)pair.getParent().getParent();
        final PsiAnnotation originalAnno = anno == null ? null :  CompletionUtilCore.getOriginalElement(anno);
        final JamService service = JamService.getJamService(pair.getProject());
        final JamAnnotationMeta annotationMeta = service.getMeta(originalAnno);
        if (annotationMeta != null) {
          final JamAttributeMeta<?> attribute = annotationMeta.findAttribute(pair.getName());
          if (attribute instanceof JamStringAttributeMeta) {
            final JamStringAttributeMeta<?,?> meta = (JamStringAttributeMeta)attribute;
            final Object jam = attribute.getJam(PsiElementRef.real(anno));
            final JamConverter<?> converter = meta.getConverter();
            if (jam instanceof List) {
              List<PsiReference> refs = ContainerUtil.newArrayList();
              final List<JamStringAttributeElement> list = (List<JamStringAttributeElement>)jam;
              for (final JamStringAttributeElement attributeElement : list) {
                if (element.equals(attributeElement.getPsiElement())) {
                  ContainerUtil.addAll(refs, converter.createReferences(attributeElement));
                }
              }
              return refs.toArray(new PsiReference[refs.size()]);
            }
            return converter.createReferences((JamStringAttributeElement)jam);

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
