package com.intellij.java.language.impl.ui;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 30-Jun-24
 */
@ServiceAPI(ComponentScope.PROJECT)
public interface PackageChooserFactory {
  @Nonnull
  PackageChooser create();
}
