/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.debugger.engine.evaluation;

import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.annotation.DeprecationInfo;
import consulo.internal.com.sun.jdi.*;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

/**
 * @author lex
 */
public class EvaluateExceptionUtil {
    public static final EvaluateException INCONSISTEND_DEBUG_INFO =
        createEvaluateException(JavaDebuggerLocalize.evaluationErrorInconsistentDebugInfo());
    public static final EvaluateException BOOLEAN_EXPECTED =
        createEvaluateException(JavaDebuggerLocalize.evaluationErrorBooleanValueExpectedInCondition());
    public static final EvaluateException PROCESS_EXITED =
        createEvaluateException(JavaDebuggerLocalize.evaluationErrorProcessExited());
    public static final EvaluateException NULL_STACK_FRAME =
        createEvaluateException(JavaDebuggerLocalize.evaluationErrorStackFrameUnavailable());
    public static final EvaluateException NESTED_EVALUATION_ERROR =
        createEvaluateException(JavaDebuggerLocalize.evaluationErrorNestedEvaluation());
    public static final EvaluateException INVALID_DEBUG_INFO =
        createEvaluateException(JavaDebuggerLocalize.evaluationErrorSourcesOutOfSync());
    public static final EvaluateException CANNOT_FIND_SOURCE_CLASS =
        createEvaluateException(JavaDebuggerLocalize.evaluationErrorCannotFindStackframeSource());
    public static final EvaluateException OBJECT_WAS_COLLECTED =
        createEvaluateException(JavaDebuggerLocalize.evaluationErrorObjectCollected());
    public static final EvaluateException ARRAY_WAS_COLLECTED =
        createEvaluateException(JavaDebuggerLocalize.evaluationErrorArrayCollected());
    public static final EvaluateException THREAD_WAS_RESUMED =
        createEvaluateException(JavaDebuggerLocalize.evaluationErrorThreadResumed());
    public static final EvaluateException DEBUG_INFO_UNAVAILABLE =
        createEvaluateException(JavaDebuggerLocalize.evaluationErrorDebugInfoUnavailable());

    private EvaluateExceptionUtil() {
    }

    public static EvaluateException createEvaluateException(Throwable th) {
        return createEvaluateException(null, th);
    }

    public static EvaluateException createEvaluateException(String msg, Throwable th) {
        String message = msg != null ? msg + ": " + reason(th) : reason(th).get();
        return new EvaluateException(message, th instanceof EvaluateException ? th.getCause() : th);
    }

    public static EvaluateException createEvaluateException(@Nonnull LocalizeValue reason) {
        return new EvaluateException(reason.get(), null);
    }

    @Deprecated
    @DeprecationInfo("Use variant with LocalizeValue")
    public static EvaluateException createEvaluateException(String reason) {
        return new EvaluateException(reason, null);
    }

    @Nonnull
    private static LocalizeValue reason(Throwable th) {
        if (th instanceof InvalidTypeException) {
            String originalReason = th.getMessage();
            return LocalizeValue.join(JavaDebuggerLocalize.evaluationErrorTypeMismatch(), LocalizeValue.space(), LocalizeValue.ofNullable(originalReason));
        }
        else if (th instanceof AbsentInformationException) {
            return JavaDebuggerLocalize.evaluationErrorDebugInfoUnavailable();
        }
        else if (th instanceof ClassNotLoadedException cnle) {
            return JavaDebuggerLocalize.evaluationErrorClassNotLoaded(cnle.className());
        }
        else if (th instanceof ClassNotPreparedException) {
            return LocalizeValue.ofNullable(th.getLocalizedMessage());
        }
        else if (th instanceof IncompatibleThreadStateException) {
            return JavaDebuggerLocalize.evaluationErrorThreadNotAtBreakpoint();
        }
        else if (th instanceof InconsistentDebugInfoException) {
            return JavaDebuggerLocalize.evaluationErrorInconsistentDebugInfo();
        }
        else if (th instanceof ObjectCollectedException) {
            return JavaDebuggerLocalize.evaluationErrorObjectCollected();
        }
        else if (th instanceof InvocationException ie) {
            return JavaDebuggerLocalize.evaluationErrorMethodException(ie.exception().referenceType().name());
        }
        else if (th instanceof EvaluateException) {
            return LocalizeValue.of(th.getMessage());
        }
        else {
            String message = th.getLocalizedMessage();
            return LocalizeValue.of(th.getClass().getName() + " : " + (message != null ? message : ""));
        }
    }
}
