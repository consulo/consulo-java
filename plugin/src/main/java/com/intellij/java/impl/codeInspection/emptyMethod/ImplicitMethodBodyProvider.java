package com.intellij.java.impl.codeInspection.emptyMethod;

import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 16/12/2022
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface ImplicitMethodBodyProvider {
  @RequiredReadAction
  boolean hasImplicitMethodBody(@Nonnull RefMethod method);
}
