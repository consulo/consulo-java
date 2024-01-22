package com.intellij.java.impl.codeInsight;

import com.intellij.java.language.codeInsight.MemberImplementorExplorer;
import consulo.annotation.component.ExtensionImpl;
import consulo.component.ComponentManager;
import consulo.component.extension.ExtensionExtender;

import jakarta.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * @author VISTALL
 * @since 16/12/2022
 */
@ExtensionImpl
public class MemberImplementorExplorerExtender implements ExtensionExtender<MemberImplementorExplorer> {
  @Override
  public void extend(@Nonnull ComponentManager componentManager, @Nonnull Consumer<MemberImplementorExplorer> consumer) {
    for (MethodImplementor implementor : componentManager.getExtensionPoint(MethodImplementor.class)) {
      consumer.accept(implementor::getMethodsToImplement);
    }
  }

  @Nonnull
  @Override
  public Class<MemberImplementorExplorer> getExtensionClass() {
    return MemberImplementorExplorer.class;
  }

  @Override
  public boolean hasAnyExtensions(ComponentManager componentManager) {
    return componentManager.getExtensionPoint(MethodImplementor.class).hasAnyExtensions();
  }
}
