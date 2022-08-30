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
package com.intellij.spi.psi;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.spi.SPILanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.ClassUtil;
import com.intellij.spi.SPIFileType;
import com.intellij.util.IncorrectOperationException;
import consulo.psi.PsiPackage;

/**
 * User: anna
 */
public class SPIFile extends PsiFileBase {
  public SPIFile(@Nonnull FileViewProvider viewProvider) {
    super(viewProvider, SPILanguage.INSTANCE);
  }

  @Override
  public PsiReference getReference() {
    return new SPIFileName2ClassReference(this, ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      @Override
      public PsiClass compute() {
        return ClassUtil.findPsiClass(getManager(), getName(), null, true, getResolveScope()); 
      }
    }));
  }

  @Nonnull
  @Override
  public PsiReference[] getReferences() {
    final List<PsiReference> refs = new ArrayList<PsiReference>();
    int idx = 0;
    int d;
    final String fileName = getName();
    while ((d = fileName.indexOf(".", idx)) > -1) {
      final PsiJavaPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(fileName.substring(0, d));
      if (aPackage != null) {
        refs.add(new SPIFileName2PackageReference(this, aPackage));
      }
      idx = d + 1;
    }
    final PsiReference reference = getReference();
    PsiElement resolve = reference.resolve();
    while (resolve instanceof PsiClass) {
      resolve = ((PsiClass)resolve).getContainingClass();
      if (resolve != null) {
        final String jvmClassName = ClassUtil.getJVMClassName((PsiClass)resolve);
        if (jvmClassName != null) {
          refs.add(new SPIFileName2PackageReference(this, resolve));
        }
      }
    }
    refs.add(reference);
    return refs.toArray(new PsiReference[refs.size()]);
  }

  @Nonnull
  @Override
  public FileType getFileType() {
    return SPIFileType.INSTANCE;
  }
  
  private static class SPIFileName2ClassReference extends PsiReferenceBase<PsiFile> {
    private final PsiClass myClass;

    public SPIFileName2ClassReference(PsiFile file, PsiClass aClass) {
      super(file, new TextRange(0, 0), false);
      myClass = aClass;
    }

    @javax.annotation.Nullable
    @Override
    public PsiElement resolve() {
      return myClass;
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      if (myClass != null) {
        final String className = ClassUtil.getJVMClassName(myClass);
        if (className != null) {
          final String newFileName = className.substring(0, className.lastIndexOf(myClass.getName())) + newElementName;
          return getElement().setName(newFileName);
        }
      }
      return getElement();
    }

    @Override
    public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
      if (element instanceof PsiClass) {
        final String className = ClassUtil.getJVMClassName((PsiClass)element);
        if (className != null) {
          return getElement().setName(className);
        }
      }
      return getElement();
    }
  }

  private static class SPIFileName2PackageReference extends PsiReferenceBase<PsiFile> {
    private final PsiElement myPackageOrContainingClass;

    public SPIFileName2PackageReference(PsiFile file, @Nonnull PsiElement psiPackage) {
      super(file, new TextRange(0, 0), false);
      myPackageOrContainingClass = psiPackage;
    }

    @Nonnull
    @Override
    public String getCanonicalText() {
      return myPackageOrContainingClass instanceof PsiJavaPackage
             ? ((PsiPackage)myPackageOrContainingClass).getQualifiedName() : ClassUtil.getJVMClassName((PsiClass)myPackageOrContainingClass);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return myPackageOrContainingClass;
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return getElement().setName(newElementName + getElement().getName().substring(getCanonicalText().length()));
    }

    @Override
    public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
      if (element instanceof PsiPackage) {
        return handleElementRename(((PsiPackage)element).getQualifiedName());
      } else if (element instanceof PsiClass) {
        final String className = ClassUtil.getJVMClassName((PsiClass)element);
        if (className != null) {
          return handleElementRename(className);
        }
      }
      return getElement();
    }
  }
}
