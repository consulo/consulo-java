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
package com.intellij.java.debugger.impl;

import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import consulo.application.Application;
import consulo.language.psi.PsiDocumentManager;
import consulo.project.Project;
import consulo.ui.ModalityState;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;

import javax.swing.*;

public class DebuggerInvocationUtil {
    public static void swingInvokeLater(final Project project, @Nonnull @RequiredUIAccess Runnable runnable) {
        SwingUtilities.invokeLater(() -> {
            if (project != null && !project.isDisposed()) {
                runnable.run();
            }
        });
    }

    public static void invokeLater(final Project project, @Nonnull final Runnable runnable) {
        Application.get().invokeLater(() -> {
            if (project != null && !project.isDisposed()) {
                runnable.run();
            }
        });
    }

    public static void invokeLater(final Project project, @Nonnull final Runnable runnable, ModalityState state) {
        Application.get().invokeLater(() -> {
            if (project == null || project.isDisposed()) {
                return;
            }

            runnable.run();
        }, state);
    }

    public static void invokeAndWait(final Project project, @Nonnull final Runnable runnable, ModalityState state) {
        Application.get().invokeAndWait(() -> {
            if (project == null || project.isDisposed()) {
                return;
            }

            runnable.run();
        }, state);
    }

    public static <T> T commitAndRunReadAction(Project project, final EvaluatingComputable<T> computable) throws EvaluateException {
        final Throwable[] ex = new Throwable[]{null};
        T result = PsiDocumentManager.getInstance(project).commitAndRunReadAction(() -> {
            try {
                return computable.compute();
            }
            catch (Exception th) {
                ex[0] = th;
            }

            return null;
        });

        if (ex[0] != null) {
            if (ex[0] instanceof RuntimeException) {
                throw (RuntimeException) ex[0];
            }
            else {
                throw (EvaluateException) ex[0];
            }
        }

        return result;
    }
}
