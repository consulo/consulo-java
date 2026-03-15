package com.intellij.java.compiler.impl.cache;

import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.DependencyCache;
import consulo.compiler.DependencyCacheFactory;

/**
 * @author VISTALL
 * @since 09/12/2022
 */
@ExtensionImpl
public class JavaDependencyCacheFactory implements DependencyCacheFactory {
  @Override
  public DependencyCache create(String cacheDir) {
    return new JavaDependencyCache(cacheDir);
  }
}
