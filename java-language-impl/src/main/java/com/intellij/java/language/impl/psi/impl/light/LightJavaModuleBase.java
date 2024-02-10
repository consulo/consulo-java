// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import consulo.util.lang.lazy.LazyValue;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public abstract class LightJavaModuleBase extends LightElement implements PsiJavaModule {
  private final LightJavaModuleReferenceElement myRefElement;
  private final Supplier<List<PsiPackageAccessibilityStatement>> myExports = LazyValue.atomicNotNull(this::findExports);

  public LightJavaModuleBase(@Nonnull PsiManager manager, @Nonnull String name) {
    super(manager, JavaLanguage.INSTANCE);
    myRefElement = new LightJavaModuleReferenceElement(manager, name);
  }

  @Nonnull
  public abstract VirtualFile getRootVirtualFile();

  @Override
  @Nullable
  public final PsiDocComment getDocComment() {
    return null;
  }

  @Override
  @Nonnull
  public final Iterable<PsiRequiresStatement> getRequires() {
    return Collections.emptyList();
  }

  @Override
  @Nonnull
  public final Iterable<PsiPackageAccessibilityStatement> getExports() {
    return myExports.get();
  }

  protected List<PsiPackageAccessibilityStatement> findExports() {
    return List.of();
  }

  @Override
  @Nonnull
  public final Iterable<PsiPackageAccessibilityStatement> getOpens() {
    return Collections.emptyList();
  }

  @Override
  @Nonnull
  public final Iterable<PsiUsesStatement> getUses() {
    return Collections.emptyList();
  }

  @Override
  @Nonnull
  public final Iterable<PsiProvidesStatement> getProvides() {
    return Collections.emptyList();
  }

  @Override
  @Nonnull
  public final PsiJavaModuleReferenceElement getNameIdentifier() {
    return myRefElement;
  }

  @Override
  @Nonnull
  public final String getName() {
    return myRefElement.getReferenceText();
  }

  @Override
  public final PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot modify an automatic module '" + getName() + "'");
  }

  @Override
  public final PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public final boolean hasModifierProperty(@Nonnull String name) {
    return false;
  }

  @Override
  public final ItemPresentation getPresentation() {
    return ItemPresentationProvider.getItemPresentation(this);
  }

  @Override
  public final int hashCode() {
    return getName().hashCode() * 31 + getManager().hashCode();
  }

  @Override
  public final String toString() {
    return "PsiJavaModule:" + getName();
  }

  public static final class LightJavaModuleReferenceElement extends LightElement implements PsiJavaModuleReferenceElement {
    private final String myText;

    public LightJavaModuleReferenceElement(@Nonnull PsiManager manager, @Nonnull String text) {
      super(manager, JavaLanguage.INSTANCE);
      myText = text;
    }

    @Override
    @Nonnull
    public String getReferenceText() {
      return myText;
    }

    @Override
    public PsiJavaModuleReference getReference() {
      return null;
    }

    @Override
    public String toString() {
      return "PsiJavaModuleReference";
    }
  }

  public static class LightPackageAccessibilityStatement extends LightElement implements PsiPackageAccessibilityStatement {
    private final String myPackageName;

    public LightPackageAccessibilityStatement(@Nonnull PsiManager manager, @Nonnull String packageName) {
      super(manager, JavaLanguage.INSTANCE);
      myPackageName = packageName;
    }

    @Override
    public
    @Nonnull
    Role getRole() {
      return Role.EXPORTS;
    }

    @Override
    public
    @Nullable
    PsiJavaCodeReferenceElement getPackageReference() {
      return null;
    }

    @Override
    public
    @Nullable
    String getPackageName() {
      return myPackageName;
    }

    @Override
    public
    @Nonnull
    Iterable<PsiJavaModuleReferenceElement> getModuleReferences() {
      return Collections.emptyList();
    }

    @Override
    public
    @Nonnull
    List<String> getModuleNames() {
      return Collections.emptyList();
    }

    @Override
    public String toString() {
      return "PsiPackageAccessibilityStatement";
    }
  }
}
