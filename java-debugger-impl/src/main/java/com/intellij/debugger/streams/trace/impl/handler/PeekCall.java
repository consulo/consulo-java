// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl.handler;

import com.intellij.java.language.psi.CommonClassNames;
import consulo.document.util.TextRange;
import consulo.execution.debug.stream.trace.impl.handler.type.GenericType;
import consulo.execution.debug.stream.wrapper.CallArgument;
import consulo.execution.debug.stream.wrapper.IntermediateStreamCall;
import consulo.execution.debug.stream.wrapper.StreamCallType;
import consulo.execution.debug.stream.wrapper.impl.CallArgumentImpl;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class PeekCall implements IntermediateStreamCall {
  private final List<CallArgument> myLambda;
  private final GenericType myElementType;

  public PeekCall(@Nonnull String lambda, @Nonnull GenericType elementType) {
    myLambda = Collections.singletonList(new CallArgumentImpl(CommonClassNames.JAVA_LANG_OBJECT, lambda));
    myElementType = elementType;
  }

  @Override
  public @Nonnull String getName() {
    return "peek";
  }

  @Override
  public @Nonnull String getGenericArguments() {
    return "";
  }

  @Override
  public @Nonnull List<CallArgument> getArguments() {
    return myLambda;
  }

  @Override
  public @Nonnull TextRange getTextRange() {
    return TextRange.EMPTY_RANGE;
  }

  @Override
  public @Nonnull StreamCallType getType() {
    return StreamCallType.INTERMEDIATE;
  }

  @Override
  public @Nonnull GenericType getTypeAfter() {
    return myElementType;
  }

  @Override
  public @Nonnull GenericType getTypeBefore() {
    return myElementType;
  }
}
