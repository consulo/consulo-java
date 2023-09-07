package com.intellij.java.analysis.impl.codeInspection.nullable;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.ui.Button;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 07/09/2023
 */
@ServiceAPI(ComponentScope.APPLICATION)
public interface NullableNotNullDialogProxy {
  @Nonnull
  Button createConfigureAnnotationsButton();
}
