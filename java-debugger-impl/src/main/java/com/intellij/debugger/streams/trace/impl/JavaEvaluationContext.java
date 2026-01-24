/*
 * Copyright 2013-2026 consulo.io
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

package com.intellij.debugger.streams.trace.impl;

import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import consulo.execution.debug.stream.trace.GenericEvaluationContext;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public class JavaEvaluationContext implements GenericEvaluationContext {
    private final EvaluationContextImpl context;

    JavaEvaluationContext(@Nonnull EvaluationContextImpl context) {
        this.context = context;
    }

    @Nonnull
    public EvaluationContextImpl getContext() {
        return context;
    }

    @Nonnull
    @Override
    public Project getProject() {
        return context.getProject();
    }
}
