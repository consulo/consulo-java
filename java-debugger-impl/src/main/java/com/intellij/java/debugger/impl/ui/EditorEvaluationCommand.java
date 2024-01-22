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
package com.intellij.java.debugger.impl.ui;

import consulo.language.editor.hint.HintManager;
import consulo.ide.impl.idea.codeInsight.hint.HintManagerImpl;
import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.DebuggerInvocationUtil;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.events.DebuggerContextCommandImpl;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.component.ProcessCanceledException;
import consulo.application.progress.ProgressIndicator;
import consulo.project.Project;
import consulo.application.util.function.Computable;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nullable;

/**
 * @author lex
 */
public abstract class EditorEvaluationCommand<T> extends DebuggerContextCommandImpl {
  protected final PsiElement myElement;
  @Nullable private final Editor myEditor;
  protected final ProgressIndicator myProgressIndicator;
  private final DebuggerContextImpl myDebuggerContext;

  public EditorEvaluationCommand(@Nullable Editor editor, PsiElement expression, DebuggerContextImpl context,
                                 final ProgressIndicator indicator) {
    super(context);
    Project project = expression.getProject();
    myProgressIndicator = indicator;
    myEditor = editor;
    myElement = expression;
    myDebuggerContext = (DebuggerManagerEx.getInstanceEx(project)).getContext();
  }

  public Priority getPriority() {
    return Priority.HIGH;
  }

  protected abstract T evaluate(EvaluationContextImpl evaluationContext) throws EvaluateException;

  public T evaluate() throws EvaluateException {
    myProgressIndicator.setText(DebuggerBundle.message("progress.evaluating", ApplicationManager.getApplication().runReadAction(new Computable<String>() {
            public String compute() {
              return myElement.getText();
            }
          })));

    try {
      T result = evaluate(myDebuggerContext.createEvaluationContext());

      if (myProgressIndicator.isCanceled()) throw new ProcessCanceledException();

      return result;
    } catch (final EvaluateException e) {
      if (myEditor != null) {
        DebuggerInvocationUtil.invokeLater(myDebuggerContext.getProject(), new Runnable() {
          public void run() {
            showEvaluationHint(myEditor, myElement, e);
          }
        }, myProgressIndicator.getModalityState());
      }
      throw e;
    }
  }

  public static void showEvaluationHint(final Editor myEditor, final PsiElement myElement, final EvaluateException e) {
    if (myEditor.isDisposed() || !myEditor.getComponent().isVisible()) return;

    HintManager.getInstance().showErrorHint(myEditor, e.getMessage(), myElement.getTextRange().getStartOffset(),
                                            myElement.getTextRange().getEndOffset(), HintManagerImpl.UNDER,
                                            HintManagerImpl.HIDE_BY_ESCAPE | HintManagerImpl.HIDE_BY_TEXT_CHANGE,
                                            1500);
  }

}
