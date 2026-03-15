package com.intellij.java.language.impl.ui;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;

/**
 * @author VISTALL
 * @since 30-Jun-24
 */
@ServiceAPI(ComponentScope.PROJECT)
public interface PackageChooserFactory {
  PackageChooser create();
}
