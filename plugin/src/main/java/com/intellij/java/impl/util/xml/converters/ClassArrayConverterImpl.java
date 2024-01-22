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
package com.intellij.java.impl.util.xml.converters;

import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.java.impl.util.xml.DomJavaUtil;
import com.intellij.java.impl.util.xml.converters.values.ClassArrayConverter;
import com.intellij.java.impl.util.xml.converters.values.ClassValueConverter;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ServiceImpl;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.module.Module;
import consulo.project.Project;
import consulo.xml.util.xml.ConvertContext;
import consulo.xml.util.xml.GenericDomValue;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Sergey.Vasiliev
 */
@Singleton
@ServiceImpl
public class ClassArrayConverterImpl extends ClassArrayConverter {
  private static final JavaClassReferenceProvider REFERENCE_PROVIDER = new JavaClassReferenceProvider();

  static {
    REFERENCE_PROVIDER.setSoft(true);
    REFERENCE_PROVIDER.setAllowEmpty(true);
  }

  @Nonnull
  public PsiReference[] createReferences(final GenericDomValue genericDomValue, final PsiElement element, final ConvertContext context) {
    final String s = genericDomValue.getStringValue();
    if (s != null) {
      final int offset = ElementManipulators.getOffsetInElement(element);
      final ArrayList<PsiReference> list = new ArrayList<PsiReference>();
      int pos = -1;
      while (true) {
        while (pos + 1 < s.length()) {
          if (!Character.isWhitespace(s.charAt(pos + 1))) {
            break;
          }
          pos++;
        }
        int nextPos = s.indexOf(',', pos + 1);
        if (nextPos == -1) {
          createReference(element, s.substring(pos + 1), pos + 1 + offset, list);
          break;
        }
        else {
          createReference(element, s.substring(pos + 1, nextPos), pos + 1 + offset, list);
          pos = nextPos;
        }
      }
      return list.toArray(new PsiReference[list.size()]);
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private static void createReference(final PsiElement element, final String s, final int offset, List<PsiReference> list) {
    final PsiReference[] references = REFERENCE_PROVIDER.getReferencesByString(s, element, offset);
    //noinspection ManualArrayToCollectionCopy
    for (PsiReference ref : references) {
      list.add(ref);
    }
  }

  public PsiClass fromString(@jakarta.annotation.Nullable @NonNls String s, final ConvertContext context) {
    if (s == null) return null;
    final Module module = context.getModule();
    final PsiFile psiFile = context.getFile();
    final Project project = psiFile.getProject();
    return DomJavaUtil.findClass(s, context.getFile(), context.getModule(), ClassValueConverter.getScope(project, module, psiFile));
  }

  public String toString(@Nullable PsiClass psiClass, final ConvertContext context) {
    return psiClass == null ? null : psiClass.getQualifiedName();
  }
}
