// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl;

import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.language.psi.CommonClassNames;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.frame.XValue;
import consulo.execution.debug.localize.XDebuggerLocalize;
import consulo.execution.debug.stream.trace.XValueInterpreter;
import consulo.internal.com.sun.jdi.*;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import java.util.concurrent.CompletableFuture;

public class JavaValueInterpreter implements XValueInterpreter {
    @Override
    public CompletableFuture<Result> extract(@Nonnull XDebugSession session, @Nonnull XValue result) {
        return CompletableFuture.supplyAsync(() -> {
            if (result instanceof JavaValue javaValue) {
                Value reference = javaValue.getDescriptor().getValue();
                if (reference instanceof ArrayReference arrayRef) {
                    return new Result.Array(
                        new JvmArrayReference(arrayRef),
                        hasInnerExceptions(arrayRef),
                        new JavaEvaluationContext(javaValue.getEvaluationContext())
                    );
                }
                else if (reference instanceof ObjectReference objRef) {
                    ReferenceType type = objRef.referenceType();
                    ClassType classType = type instanceof ClassType ? (ClassType) type : null;
                    if (classType != null) {
                        while (classType != null && !CommonClassNames.JAVA_LANG_THROWABLE.equals(classType.name())) {
                            classType = classType.superclass();
                        }
                        if (classType != null) {
                            String exceptionMessage = DebuggerUtils.tryExtractExceptionMessage(objRef);
                            LocalizeValue descriptionWithReason;
                            if (exceptionMessage == null) {
                                descriptionWithReason = XDebuggerLocalize.streamDebuggerEvaluationFailedWithException(type.name());
                            }
                            else {
                                descriptionWithReason = XDebuggerLocalize.streamDebuggerEvaluationFailedWithExceptionAndMessage(type.name(), exceptionMessage);
                            }
                            return new Result.Error(descriptionWithReason.get());
                        }
                    }
                }
            }
            return Result.Unknown.INSTANCE;
        });
    }

    private boolean hasInnerExceptions(@Nonnull ArrayReference resultArray) {
        ArrayReference result = (ArrayReference) resultArray.getValue(1);
        ReferenceType type = result.referenceType();
        if (type instanceof ArrayType arrayType) {
            if (arrayType.componentTypeName().contains("Throwable")) {
                return true;
            }
        }
        return false;
    }
}

