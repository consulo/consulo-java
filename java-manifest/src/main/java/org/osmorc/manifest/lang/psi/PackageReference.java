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
package org.osmorc.manifest.lang.psi;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReferenceBase;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class PackageReference extends PsiReferenceBase<PsiElement> {
  private final PackageReferenceSet myReferenceSet;
  private final int myIndex;
  private final GlobalSearchScope _moduleWithLibrariesScope;

  public PackageReference(final PackageReferenceSet set, final TextRange range, final int index) {
    super(set.getElement(), range);
    myReferenceSet = set;
    myIndex = index;
    Module module = ModuleUtilCore.findModuleForPsiElement(set.getElement());
    if (module != null) {
      _moduleWithLibrariesScope = GlobalSearchScope.moduleWithLibrariesScope(module);
    } else {
      _moduleWithLibrariesScope = GlobalSearchScope.allScope(set.getElement().getProject());
    }
  }

  @Nullable
  private PsiJavaPackage getPsiPackage() {
    return myIndex == 0
        ? JavaPsiFacade.getInstance(getElement().getProject()).findPackage("")
        : myReferenceSet.getReference(myIndex - 1).resolve();
  }

  public boolean isSoft() {
    return true;
  }

  @Nullable
  public PsiJavaPackage resolve() {
    final PsiJavaPackage parentPackage = getPsiPackage();
    if (parentPackage != null) {
      for (PsiJavaPackage aPackage : parentPackage.getSubPackages(_moduleWithLibrariesScope)) {
        if (Comparing.equal(aPackage.getName(), getCanonicalText())) {
          return aPackage;
        }
      }
    }
    return null;
  }

  @Nonnull
  public Object[] getVariants() {
    final PsiJavaPackage psiPackage = getPsiPackage();
    if (psiPackage == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    final PsiJavaPackage[] psiPackages = psiPackage.getSubPackages(_moduleWithLibrariesScope);
    final Object[] variants = new Object[psiPackages.length];
    System.arraycopy(psiPackages, 0, variants, 0, variants.length);
    return variants;
  }
}
