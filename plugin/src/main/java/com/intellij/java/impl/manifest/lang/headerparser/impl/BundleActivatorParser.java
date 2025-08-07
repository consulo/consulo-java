/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.intellij.java.impl.manifest.lang.headerparser.impl;

import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.ClassKind;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.osmorc.manifest.lang.headerparser.impl.AbstractHeaderParserImpl;
import org.osmorc.manifest.lang.psi.Clause;
import org.osmorc.manifest.lang.psi.HeaderValuePart;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class BundleActivatorParser extends AbstractHeaderParserImpl{

  @Override
  public PsiReference[] getReferences(@Nonnull HeaderValuePart headerValuePart) {
    if (headerValuePart.getParent() instanceof Clause) {
      final Module module = ModuleUtilCore.findModuleForPsiElement(headerValuePart);
      JavaClassReferenceProvider provider;
      if (module != null) {
        provider = new JavaClassReferenceProvider() {
          @Override
          public GlobalSearchScope getScope(Project project) {
            return GlobalSearchScope.moduleScope(module);
          }
        };
      }
      else {
        provider = new JavaClassReferenceProvider();
      }

      provider.setOption(JavaClassReferenceProvider.EXTEND_CLASS_NAMES, new String[]{"org.osgi.framework.BundleActivator"});
      provider.setOption(JavaClassReferenceProvider.CONCRETE, true);
      provider.setOption(JavaClassReferenceProvider.CLASS_KIND, ClassKind.CLASS);
      return provider.getReferencesByElement(headerValuePart);
    }
    return PsiReference.EMPTY_ARRAY;
  }

  @Override
  public boolean isAcceptable(@Nonnull Object o) {
    return o instanceof PsiClass;
  }
}