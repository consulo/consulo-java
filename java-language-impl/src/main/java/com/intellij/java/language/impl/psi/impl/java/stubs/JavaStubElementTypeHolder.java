package com.intellij.java.language.impl.psi.impl.java.stubs;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.stub.ObjectStubSerializerProvider;
import consulo.language.psi.stub.StubElementTypeHolder;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

/**
 * @author VISTALL
 * @since 09/12/2022
 */
@ExtensionImpl
public class JavaStubElementTypeHolder extends StubElementTypeHolder<JavaStubElementTypes> {
  @Nullable
  @Override
  public String getExternalIdPrefix() {
    return null;
  }

  @Override
  public List<ObjectStubSerializerProvider> loadSerializers() {
    return allFromStaticFields(JavaStubElementTypes.class, Field::get);
  }
}
