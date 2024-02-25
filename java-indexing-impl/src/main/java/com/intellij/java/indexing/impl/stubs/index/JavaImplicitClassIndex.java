// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.indexing.impl.stubs.index;

import com.intellij.java.language.impl.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.java.language.psi.PsiImplicitClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.StringStubIndexExtension;
import consulo.language.psi.stub.StubIndex;
import consulo.language.psi.stub.StubIndexKey;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

@ExtensionImpl
public class JavaImplicitClassIndex extends StringStubIndexExtension<PsiImplicitClass> {
  private static final JavaImplicitClassIndex ourInstance = new JavaImplicitClassIndex();

  public static JavaImplicitClassIndex getInstance() {
    return ourInstance;
  }

  @Override
  public @Nonnull StubIndexKey<String, PsiImplicitClass> getKey() {
    return JavaStubIndexKeys.IMPLICIT_CLASSES;
  }

  public Collection<String> getAllClasses(@Nonnull Project project) {
    return StubIndex.getInstance().getAllKeys(getKey(), project);
  }

  public @Nonnull Collection<PsiImplicitClass> getElements(@Nonnull String key,
                                                           @Nonnull Project project,
                                                           @Nullable GlobalSearchScope scope) {
    return StubIndex.getElements(JavaStubIndexKeys.IMPLICIT_CLASSES, key, project, scope, PsiImplicitClass.class);
  }
}