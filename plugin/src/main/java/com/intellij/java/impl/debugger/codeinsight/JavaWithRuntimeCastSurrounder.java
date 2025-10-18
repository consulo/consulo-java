/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.impl.debugger.codeinsight;

import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerInvocationUtil;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.codeinsight.RuntimeTypeEvaluator;
import com.intellij.java.debugger.impl.engine.evaluation.DefaultCodeFragmentFactory;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.impl.codeInsight.generation.surroundWith.JavaExpressionSurrounder;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Result;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.Task;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.document.util.TextRange;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiCodeFragment;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * User: lex
 * Date: Jul 17, 2003
 * Time: 7:51:01 PM
 */
@ExtensionImpl
public class JavaWithRuntimeCastSurrounder extends JavaExpressionSurrounder {
    @Override
    public LocalizeValue getTemplateDescription() {
        return CodeInsightLocalize.surroundWithRuntimeTypeTemplate();
    }

    @Override
    public boolean isApplicable(PsiExpression expr) {
        if (!expr.isPhysical()) {
            return false;
        }
        PsiFile file = expr.getContainingFile();
        return file instanceof PsiCodeFragment && DefaultCodeFragmentFactory.isDebuggerFile(file) && RuntimeTypeEvaluator.isSubtypeable(expr);
    }

    @Override
    public TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException {
        DebuggerContextImpl debuggerContext = (DebuggerManagerEx.getInstanceEx(project)).getContext();
        DebuggerSession debuggerSession = debuggerContext.getDebuggerSession();
        if (debuggerSession != null) {
            new Task.Backgroundable(project, JavaDebuggerLocalize.titleEvaluating(), true) {
                private SurroundWithCastWorker myWorker;

                @Override
                public void run(@Nonnull ProgressIndicator indicator) {
                    myWorker = new SurroundWithCastWorker(editor, expr, debuggerContext, indicator);

                    debuggerContext.getDebugProcess().getManagerThread().invokeAndWait(myWorker);
                }

                @RequiredUIAccess
                @Override
                public void onCancel() {
                    if (myWorker != null) {
                        myWorker.release();
                    }
                }
            }.queue();
        }
        return null;
    }

    private static class SurroundWithCastWorker extends RuntimeTypeEvaluator {
        private final Editor myEditor;

        public SurroundWithCastWorker(Editor editor, PsiExpression expression, DebuggerContextImpl context, final ProgressIndicator indicator) {
            super(editor, expression, context, indicator);
            myEditor = editor;
        }

        @Override
        protected void typeCalculationFinished(@Nullable final PsiType type) {
            if (type == null) {
                return;
            }

            hold();
            final Project project = myElement.getProject();
            DebuggerInvocationUtil.invokeLater(
                project,
                () -> new WriteCommandAction(project, CodeInsightLocalize.commandNameSurroundWithRuntimeCast().get()) {
                    @Override
                    protected void run(@Nonnull Result result) throws Throwable {
                        try {
                            PsiElementFactory factory = JavaPsiFacade.getInstance(myElement.getProject()).getElementFactory();
                            PsiParenthesizedExpression parenth =
                                (PsiParenthesizedExpression) factory.createExpressionFromText("((" + type.getCanonicalText() + ")expr)", null);
                            //noinspection ConstantConditions
                            ((PsiTypeCastExpression) parenth.getExpression()).getOperand().replace(myElement);
                            parenth = (PsiParenthesizedExpression) JavaCodeStyleManager.getInstance(project).shortenClassReferences(parenth);
                            PsiExpression expr = (PsiExpression) myElement.replace(parenth);
                            TextRange range = expr.getTextRange();
                            myEditor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
                            myEditor.getCaretModel().moveToOffset(range.getEndOffset());
                            myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
                        }
                        catch (IncorrectOperationException e) {
                            // OK here. Can be caused by invalid type like one for proxy starts with . '.Proxy34'
                        }
                        finally {
                            release();
                        }
                    }
                }.execute(),
                myProgressIndicator.getModalityState()
            );
        }

    }
}
