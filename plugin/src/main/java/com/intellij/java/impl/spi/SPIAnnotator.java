/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.spi;

import com.intellij.java.impl.spi.psi.SPIClassProviderReferenceElement;
import com.intellij.java.language.impl.spi.psi.SPIFile;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.ClassUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;

/**
 * User: anna
 */
public class SPIAnnotator implements Annotator {
  @Override
  @RequiredReadAction
  public void annotate(@jakarta.annotation.Nonnull PsiElement element, @Nonnull AnnotationHolder holder) {
    final VirtualFile file = PsiUtilCore.getVirtualFile(element);
    if (file != null) {
      final String serviceProviderName = file.getName();
      final PsiClass psiClass =
        ClassUtil.findPsiClass(element.getManager(), serviceProviderName, null, true, element.getContainingFile().getResolveScope());
      if (element instanceof SPIFile) {
        if (psiClass == null) {
          holder.newAnnotation(HighlightSeverity.ERROR, "No service provider \"" + serviceProviderName + "\' found")
                .fileLevel()
                .create();
        }
      }
      else if (element instanceof SPIClassProviderReferenceElement) {
        final PsiElement resolve = ((SPIClassProviderReferenceElement)element).resolve();
        if (resolve == null) {
          holder.newAnnotation(HighlightSeverity.ERROR, "Cannot resolve symbol " + element.getText()).range(element).create();
        }
        else if (resolve instanceof PsiClass && psiClass != null) {
          if (!((PsiClass)resolve).isInheritor(psiClass, true)) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Registered extension should implement " + serviceProviderName)
                  .range(element)
                  .create();
          }
        }
      }
    }
  }
}
