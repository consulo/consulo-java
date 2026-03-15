// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi.targets;

import consulo.language.impl.psi.DelegatePsiTarget;
import consulo.language.pom.PomRenameableTarget;
import consulo.language.psi.PsiNamedElement;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

public class AliasingPsiTarget extends DelegatePsiTarget implements PomRenameableTarget<AliasingPsiTarget> {
  public AliasingPsiTarget(PsiNamedElement element) {
    super(element);
  }

  @Override
  public boolean isWritable() {
    return getNavigationElement().isWritable();
  }

  @Override
  public AliasingPsiTarget setName(String newName) {
    return setAliasName(newName);
  }

  @Override
  public String getName() {
    return StringUtil.notNullize(getNameAlias(StringUtil.notNullize(((PsiNamedElement) getNavigationElement()).getName())));
  }

  public AliasingPsiTarget setAliasName(String newAliasName) {
    return this;
  }

  @Nullable
  public String getNameAlias(String delegatePsiTargetName) {
    return delegatePsiTargetName;
  }

  public String getTargetName(String aliasName) {
    return aliasName;
  }
}
