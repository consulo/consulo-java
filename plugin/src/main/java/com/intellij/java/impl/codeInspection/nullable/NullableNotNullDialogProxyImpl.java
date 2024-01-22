package com.intellij.java.impl.codeInspection.nullable;

import com.intellij.java.analysis.impl.codeInspection.nullable.NullableNotNullDialogProxy;
import com.intellij.java.impl.codeInsight.NullableNotNullDialog;
import consulo.annotation.component.ServiceImpl;
import consulo.ui.Button;
import jakarta.annotation.Nonnull;
import jakarta.inject.Singleton;

/**
 * @author VISTALL
 * @since 07/09/2023
 */
@Singleton
@ServiceImpl
public class NullableNotNullDialogProxyImpl implements NullableNotNullDialogProxy {
  @Nonnull
  @Override
  public Button createConfigureAnnotationsButton() {
    return NullableNotNullDialog.createConfigureAnnotationsButton();
  }
}
