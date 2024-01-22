// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi.targets;

import consulo.language.impl.psi.DelegatePsiTarget;
import consulo.language.pom.PomRenameableTarget;
import consulo.language.psi.PsiNamedElement;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class AliasingPsiTarget extends DelegatePsiTarget implements PomRenameableTarget<AliasingPsiTarget> {
  public AliasingPsiTarget(@Nonnull PsiNamedElement element) {
    super(element);
  }

  @Override
  public boolean isWritable() {
    return getNavigationElement().isWritable();
  }

  @Override
  public AliasingPsiTarget setName(@Nonnull String newName) {
    return setAliasName(newName);
  }

  @Override
  @Nonnull
  public String getName() {
    return StringUtil.notNullize(getNameAlias(StringUtil.notNullize(((PsiNamedElement) getNavigationElement()).getName())));
  }

  @Nonnull
  public AliasingPsiTarget setAliasName(@Nonnull String newAliasName) {
    return this;
  }

  @Nullable
  public String getNameAlias(@Nonnull String delegatePsiTargetName) {
    return delegatePsiTargetName;
  }

  @Nonnull
  public String getTargetName(@Nonnull String aliasName) {
    return aliasName;
  }
}
