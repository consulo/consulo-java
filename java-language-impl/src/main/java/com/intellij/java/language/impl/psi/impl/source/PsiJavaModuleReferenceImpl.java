// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.JavaCoreBundle;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import consulo.document.util.TextRange;
import consulo.language.psi.*;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import javax.annotation.Nonnull;
import java.util.Collection;

public class PsiJavaModuleReferenceImpl extends PsiReferenceBase.Poly<PsiJavaModuleReferenceElement> implements PsiJavaModuleReference {
  public PsiJavaModuleReferenceImpl(@Nonnull PsiJavaModuleReferenceElement element) {
    super(element, new TextRange(0, element.getTextLength()), false);
  }

  @Nonnull
  @Override
  public String getCanonicalText() {
    return getElement().getReferenceText();
  }

  @Override
  public PsiJavaModule resolve() {
    return (PsiJavaModule) super.resolve();
  }

  @Override
  @Nonnull
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    return ResolveCache.getInstance(getProject()).resolveWithCaching(this, Resolver.INSTANCE, false, incompleteCode);
  }

  @Override
  public PsiElement handleElementRename(@Nonnull String newName) throws IncorrectOperationException {
    PsiJavaModuleReferenceElement element = getElement();
    if (element instanceof PsiCompiledElement) {
      throw new IncorrectOperationException(JavaCoreBundle.message("psi.error.attempt.to.edit.class.file", element.getContainingFile()));
    }
    PsiElement newElement = PsiElementFactory.getInstance(element.getProject()).createModuleReferenceFromText(newName, null);
    return element.replace(newElement);
  }

  private Project getProject() {
    return getElement().getProject();
  }

  private static class Resolver implements ResolveCache.PolyVariantResolver<PsiJavaModuleReferenceImpl> {
    private static final ResolveCache.PolyVariantResolver<PsiJavaModuleReferenceImpl> INSTANCE = new Resolver();

    @Override
    @Nonnull
    public ResolveResult[] resolve(@Nonnull PsiJavaModuleReferenceImpl reference, boolean incompleteCode) {
      PsiJavaModuleReferenceElement refElement = reference.getElement();
      PsiFile file = refElement.getContainingFile();
      String moduleName = reference.getCanonicalText();

      if (file instanceof PsiJavaFile) {
        PsiJavaModule module = ((PsiJavaFile) file).getModuleDeclaration();
        if (module != null && module.getName().equals(moduleName)) {
          return new ResolveResult[]{new PsiElementResolveResult(module)};
        }
      }

      boolean global = incompleteCode || refElement.getParent() instanceof PsiPackageAccessibilityStatement;
      Project project = file.getProject();
      GlobalSearchScope scope = global ? GlobalSearchScope.allScope(project) : file.getResolveScope();
      Collection<PsiJavaModule> modules = JavaPsiFacade.getInstance(project).findModules(moduleName, scope);
      if (!modules.isEmpty()) {
        ResolveResult[] result = new ResolveResult[modules.size()];
        int i = 0;
        for (PsiJavaModule module : modules)
          result[i++] = new PsiElementResolveResult(module);
        return result;
      } else {
        return ResolveResult.EMPTY_ARRAY;
      }
    }
  }
}