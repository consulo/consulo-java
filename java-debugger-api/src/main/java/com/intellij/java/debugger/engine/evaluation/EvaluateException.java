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

import consulo.internal.com.sun.jdi.InvocationException;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.logging.Logger;
import jakarta.annotation.Nullable;

public class EvaluateException extends Exception {
    private static final Logger LOG = Logger.getInstance(EvaluateException.class);
    private ObjectReference myTargetException;

    public EvaluateException(String message) {
        super(message);
        if (LOG.isDebugEnabled()) {
            LOG.debug(message);
        }
    }

    public EvaluateException(String msg, Throwable th) {
        super(msg, th);
        if (th instanceof EvaluateException evaluateException) {
            myTargetException = evaluateException.getExceptionFromTargetVM();
        }
        else if (th instanceof InvocationException invocationException) {
            myTargetException = invocationException.exception();
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug(msg);
        }
    }

    @Nullable
    public ObjectReference getExceptionFromTargetVM() {
        return myTargetException;
    }

    public void setTargetException(ObjectReference targetException) {
        myTargetException = targetException;
    }

    @Override
    public String getMessage() {
        String errorMessage = super.getMessage();
        if (errorMessage != null) {
            return errorMessage;
        }
        Throwable cause = getCause();
        String causeMessage = cause != null ? cause.getMessage() : null;
        if (causeMessage != null) {
            return causeMessage;
        }
        return "unknown error";
    }
}