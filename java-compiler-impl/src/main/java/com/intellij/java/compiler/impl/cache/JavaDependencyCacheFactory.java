package com.intellij.java.compiler.impl.cache;

import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.DependencyCache;
import consulo.compiler.DependencyCacheFactory;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 09/12/2022
 */
@ExtensionImpl
public class JavaDependencyCacheFactory implements DependencyCacheFactory {
  @Nonnull
  @Override
  public DependencyCache create(@jakarta.annotation.Nonnull String cacheDir) {
    return new JavaDependencyCache(cacheDir);
  }
}
