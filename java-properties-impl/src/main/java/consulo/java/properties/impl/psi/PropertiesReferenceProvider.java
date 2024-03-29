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
package consulo.java.properties.impl.psi;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.impl.psi.CommonReferenceProviderTypes;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.xml.util.XmlUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.analysis.impl.util.JavaI18nUtil;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.xml.psi.xml.XmlAttribute;
import consulo.xml.psi.xml.XmlAttributeValue;
import consulo.xml.psi.xml.XmlTag;
import jakarta.inject.Inject;

import jakarta.annotation.Nonnull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author cdr
 */
@ExtensionImpl
public class PropertiesReferenceProvider extends PsiReferenceProviderByType {
  private final boolean myDefaultSoft;

  @Inject
  public PropertiesReferenceProvider() {
    this(false);
  }

  public PropertiesReferenceProvider(final boolean defaultSoft) {
    myDefaultSoft = defaultSoft;
  }

  @Nonnull
  @Override
  public ReferenceProviderType getReferenceProviderType() {
    return CommonReferenceProviderTypes.PROPERTIES_FILE_KEY_PROVIDER;
  }

  @Override
  public boolean acceptsTarget(@Nonnull PsiElement target) {
    return target instanceof IProperty;
  }

  @Nonnull
  public PsiReference[] getReferencesByElement(@Nonnull PsiElement element, @Nonnull final ProcessingContext context) {
    Object value = null;
    String bundleName = null;
    boolean propertyRefWithPrefix = false;
    boolean soft = myDefaultSoft;

    if (element instanceof PsiLiteralExpression) {
      PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
      value = literalExpression.getValue();

      final Map<String, Object> annotationParams = new HashMap<String, Object>();
      annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
      if (JavaI18nUtil.mustBePropertyKey(element.getProject(), literalExpression, annotationParams)) {
        soft = false;
        final Object resourceBundleName = annotationParams.get(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER);
        if (resourceBundleName instanceof PsiExpression) {
          PsiExpression expr = (PsiExpression)resourceBundleName;
          final Object bundleValue =
            JavaPsiFacade.getInstance(expr.getProject()).getConstantEvaluationHelper().computeConstantExpression(expr);
          bundleName = bundleValue == null ? null : bundleValue.toString();
        }
      }
    }
    else if (element instanceof XmlAttributeValue && isNonDynamicAttribute(element)) {
      if (element.getTextLength() < 2) {
        return PsiReference.EMPTY_ARRAY;
      }
      value = ((XmlAttributeValue)element).getValue();
      final XmlAttribute attribute = (XmlAttribute)element.getParent();
      if ("key".equals(attribute.getName())) {
        final XmlTag parent = attribute.getParent();
        if ("message".equals(parent.getLocalName()) && Arrays.binarySearch(XmlUtil.JSTL_FORMAT_URIS, parent.getNamespace()) >= 0) {
          propertyRefWithPrefix = true;
        }
      }
    }

    if (value instanceof String) {
      String text = (String)value;
      PsiReference reference = propertyRefWithPrefix ?
        new PrefixBasedPropertyReference(text, element, bundleName, soft) :
        new PropertyReference(text, element, bundleName, soft);
      return new PsiReference[]{reference};
    }
    return PsiReference.EMPTY_ARRAY;
  }

  static boolean isNonDynamicAttribute(final PsiElement element) {
    return PsiTreeUtil.getChildOfAnyType(element, OuterLanguageElement.class) == null;
  }

}
