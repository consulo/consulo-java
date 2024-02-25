// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.psi.impl.cache.TypeInfo;
import consulo.internal.org.objectweb.asm.AnnotationVisitor;
import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.internal.org.objectweb.asm.Type;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.function.Consumer;

final class AnnotationTextCollector extends AnnotationVisitor {
  private final @Nonnull StringBuilder myBuilder = new StringBuilder();
  private final @Nonnull SignatureParsing.TypeInfoProvider myMapping;
  private final Consumer<? super String> myCallback;
  private boolean hasPrefix;
  private boolean hasParams;

  AnnotationTextCollector(@Nullable String desc, @Nonnull SignatureParsing.TypeInfoProvider mapping, Consumer<? super String> callback) {
    super(Opcodes.API_VERSION);
    myMapping = mapping;
    myCallback = callback;

    if (desc != null) {
      hasPrefix = true;
      myBuilder.append('@').append(StubBuildingVisitor.toJavaType(Type.getType(desc), myMapping));
    }
  }

  @Override
  public void visit(String name, Object value) {
    valuePairPrefix(name);
    myBuilder.append(StubBuildingVisitor.constToString(value, TypeInfo.SimpleTypeInfo.NULL, true, myMapping));
  }

  @Override
  public void visitEnum(String name, String desc, String value) {
    valuePairPrefix(name);
    myBuilder.append(StubBuildingVisitor.toJavaType(Type.getType(desc), myMapping)).append('.').append(value);
  }

  private void valuePairPrefix(String name) {
    if (!hasParams) {
      hasParams = true;
      if (hasPrefix) {
        myBuilder.append('(');
      }
    }
    else {
      myBuilder.append(',');
    }

    if (name != null) {
      myBuilder.append(name).append('=');
    }
  }

  @Override
  public AnnotationVisitor visitAnnotation(String name, String desc) {
    valuePairPrefix(name);
    return new AnnotationTextCollector(desc, myMapping, text -> myBuilder.append(text));
  }

  @Override
  public AnnotationVisitor visitArray(String name) {
    valuePairPrefix(name);
    myBuilder.append('{');
    return new AnnotationTextCollector(null, myMapping, text -> myBuilder.append(text).append('}'));
  }

  @Override
  public void visitEnd() {
    if (hasPrefix && hasParams) {
      myBuilder.append(')');
    }
    myCallback.accept(myBuilder.toString());
  }
}