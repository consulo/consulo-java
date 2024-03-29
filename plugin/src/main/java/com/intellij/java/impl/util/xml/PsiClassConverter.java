/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.java.impl.util.xml;

import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.ClassKind;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.xml.util.xml.*;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class PsiClassConverter extends Converter<PsiClass> implements CustomReferenceConverter<PsiClass> {

  public PsiClass fromString(final String s, final ConvertContext context) {
    if (StringUtil.isEmptyOrSpaces(s)) return null;

    final DomElement element = context.getInvocationElement();
    final GlobalSearchScope scope = element instanceof GenericDomValue ? getScope(context) : null;
    return DomJavaUtil.findClass(s.trim(), context.getFile(), context.getModule(), scope);
  }

  @Nullable
  public String getErrorMessage(@Nullable final String s, final ConvertContext context) {
    return null;
  }

  public String toString(final PsiClass t, final ConvertContext context) {
    return t == null ? null : t.getQualifiedName();
  }

  @Nonnull
  public PsiReference[] createReferences(GenericDomValue<PsiClass> genericDomValue, PsiElement element, ConvertContext context) {

    ExtendClass extendClass = genericDomValue.getAnnotation(ExtendClass.class);
    final JavaClassReferenceProvider provider = createClassReferenceProvider(genericDomValue, context, extendClass);
    return provider.getReferencesByElement(element);
  }

  protected JavaClassReferenceProvider createClassReferenceProvider(final GenericDomValue<PsiClass> genericDomValue,
                                                                    final ConvertContext context,
                                                                    ExtendClass extendClass) {
    return createJavaClassReferenceProvider(genericDomValue, extendClass, new JavaClassReferenceProvider() {

      @Override
      public GlobalSearchScope getScope(Project project) {
        return PsiClassConverter.this.getScope(context);
      }
    });
  }

  public static JavaClassReferenceProvider createJavaClassReferenceProvider(final GenericDomValue genericDomValue,
                                                                            ExtendClass extendClass,
                                                                            final JavaClassReferenceProvider provider) {

    if (extendClass != null) {
      if (StringUtil.isNotEmpty(extendClass.value())) {
        provider.setOption(JavaClassReferenceProvider.EXTEND_CLASS_NAMES, new String[]{extendClass.value()});
      }
      if (extendClass.instantiatable()) {
        provider.setOption(JavaClassReferenceProvider.INSTANTIATABLE, Boolean.TRUE);
      }
      if (!extendClass.allowAbstract()) {
        provider.setOption(JavaClassReferenceProvider.CONCRETE, Boolean.TRUE);
      }
      if (!extendClass.allowInterface()) {
        provider.setOption(JavaClassReferenceProvider.NOT_INTERFACE, Boolean.TRUE);
      }
      if (!extendClass.allowEnum()) {
        provider.setOption(JavaClassReferenceProvider.NOT_ENUM, Boolean.TRUE);
      }
      if (extendClass.jvmFormat()) {
        provider.setOption(JavaClassReferenceProvider.JVM_FORMAT, Boolean.TRUE);
      }
      provider.setAllowEmpty(extendClass.allowEmpty());
    }

    ClassTemplate template = genericDomValue.getAnnotation(ClassTemplate.class);
    if (template != null) {
      if (StringUtil.isNotEmpty(template.value())) {
        provider.setOption(JavaClassReferenceProvider.CLASS_TEMPLATE, template.value());
      }
      provider.setOption(JavaClassReferenceProvider.CLASS_KIND, template.kind());
    }

    provider.setSoft(true);
    return provider;
  }

  @Nullable
  protected GlobalSearchScope getScope(@Nonnull ConvertContext context) {
    return context.getSearchScope();
  }

  public static class AnnotationType extends PsiClassConverter {

    @Override
    protected JavaClassReferenceProvider createClassReferenceProvider(GenericDomValue<PsiClass> genericDomValue,
                                                                      ConvertContext context,
                                                                      ExtendClass extendClass) {
      final JavaClassReferenceProvider provider = super.createClassReferenceProvider(genericDomValue, context,
          extendClass);

      provider.setOption(JavaClassReferenceProvider.CLASS_KIND, ClassKind.ANNOTATION);
      //provider.setOption(JavaClassReferenceProvider.EXTEND_CLASS_NAMES, new String[] {"org.springframework.samples.petclinic.jsr330.Foo"});
      //
      return provider;
    }
  }

  public static class EnumType extends PsiClassConverter {

    @Override
    protected JavaClassReferenceProvider createClassReferenceProvider(GenericDomValue<PsiClass> genericDomValue,
                                                                      ConvertContext context,
                                                                      ExtendClass extendClass) {
      final JavaClassReferenceProvider provider = super.createClassReferenceProvider(genericDomValue, context,
          extendClass);
      provider.setOption(JavaClassReferenceProvider.CLASS_KIND, ClassKind.ENUM);

      return provider;
    }
  }
}
