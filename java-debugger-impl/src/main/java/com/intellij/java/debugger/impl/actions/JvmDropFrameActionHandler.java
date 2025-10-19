// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.JavaStackFrame;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.execution.debug.breakpoint.XExpression;
import consulo.execution.debug.evaluation.EvaluationMode;
import consulo.execution.debug.evaluation.XDebuggerEvaluator;
import consulo.execution.debug.frame.XDropFrameHandler;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.debug.frame.XValue;
import consulo.execution.debug.localize.XDebuggerLocalize;
import consulo.internal.com.sun.jdi.InvalidStackFrameException;
import consulo.internal.com.sun.jdi.VMDisconnectedException;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.ModalityState;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.MessageDialogBuilder;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JvmDropFrameActionHandler implements XDropFrameHandler {
    private static final Logger LOG = Logger.getInstance(JvmDropFrameActionHandler.class);
    private final @Nonnull DebuggerSession myDebugSession;

    public JvmDropFrameActionHandler(@Nonnull DebuggerSession process) {
        myDebugSession = process;
    }

    @Override
    public boolean canDrop(@Nonnull XStackFrame frame) {
        //noinspection SimplifiableIfStatement
        if (frame instanceof JavaStackFrame javaStackFrame) {
            return javaStackFrame.getStackFrameProxy().getVirtualMachine().canPopFrames() && javaStackFrame.getDescriptor().canDrop();
        }
        return false;
    }

    @Override
    @RequiredUIAccess
    public void drop(@Nonnull XStackFrame frame) {
        if (frame instanceof JavaStackFrame stackFrame) {
            var project = myDebugSession.getProject();
            var debugProcess = myDebugSession.getProcess();
            var debuggerContext = myDebugSession.getContextManager().getContext();
            try {
                myDebugSession.setSteppingThrough(stackFrame.getStackFrameProxy().threadProxy());
                if (evaluateFinallyBlocks(
                    project,
                    XDebuggerLocalize.xdebuggerResetFrameTitle().get(),
                    stackFrame,
                    new XDebuggerEvaluator.XEvaluationCallback() {
                        @Override
                        public void evaluated(@Nonnull XValue result) {
                            popFrame(debugProcess, debuggerContext, stackFrame);
                        }

                        @Override
                        public void errorOccurred(@Nonnull LocalizeValue errorMessage) {
                            showError(
                                project,
                                JavaDebuggerLocalize.errorExecutingFinally(errorMessage),
                                XDebuggerLocalize.xdebuggerResetFrameTitle()
                            );
                        }
                    }
                )) {
                    return;
                }
                popFrame(debugProcess, debuggerContext, stackFrame);
            }
            catch (InvalidStackFrameException | VMDisconnectedException ignored) {
            }
        }
    }

    @RequiredUIAccess
    public static boolean evaluateFinallyBlocks(
        Project project,
        String title,
        JavaStackFrame stackFrame,
        XDebuggerEvaluator.XEvaluationCallback callback
    ) {
        if (!DebuggerSettings.EVALUATE_FINALLY_NEVER.equals(DebuggerSettings.getInstance().EVALUATE_FINALLY_ON_POP_FRAME)) {
            List<PsiStatement> statements = getFinallyStatements(project, stackFrame.getDescriptor().getSourcePosition());
            if (!statements.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (PsiStatement statement : statements) {
                    sb.append("\n").append(statement.getText());
                }
                if (DebuggerSettings.EVALUATE_FINALLY_ALWAYS.equals(DebuggerSettings.getInstance().EVALUATE_FINALLY_ON_POP_FRAME)) {
                    evaluateAndAct(project, stackFrame, sb, callback);
                    return true;
                }
                else {
                    int res = MessageDialogBuilder
                        .yesNoCancel(
                            title,
                            JavaDebuggerLocalize.warningFinallyBlockDetected().get() + sb
                        )
                        .icon(UIUtil.getWarningIcon())
                        .yesText(JavaDebuggerLocalize.buttonExecuteFinally().get())
                        .noText(JavaDebuggerLocalize.buttonDropAnyway().get())
                        .project(project)
                        .cancelText(CommonLocalize.buttonCancel().get())
                        .doNotAsk(new DialogWrapper.DoNotAskOption() {
                            @Override
                            public boolean isToBeShown() {
                                return !DebuggerSettings.EVALUATE_FINALLY_ALWAYS
                                    .equals(DebuggerSettings.getInstance().EVALUATE_FINALLY_ON_POP_FRAME)
                                    && !DebuggerSettings.EVALUATE_FINALLY_NEVER
                                    .equals(DebuggerSettings.getInstance().EVALUATE_FINALLY_ON_POP_FRAME);
                            }

                            @Override
                            public void setToBeShown(boolean value, int exitCode) {
                                if (!value) {
                                    DebuggerSettings.getInstance().EVALUATE_FINALLY_ON_POP_FRAME = exitCode == Messages.YES
                                        ? DebuggerSettings.EVALUATE_FINALLY_ALWAYS
                                        : DebuggerSettings.EVALUATE_FINALLY_NEVER;
                                }
                                else {
                                    DebuggerSettings.getInstance().EVALUATE_FINALLY_ON_POP_FRAME = DebuggerSettings.EVALUATE_FINALLY_ASK;
                                }
                            }

                            @Override
                            public boolean canBeHidden() {
                                return true;
                            }

                            @Override
                            public boolean shouldSaveOptionsOnCancel() {
                                return false;
                            }

                            @Nonnull
                            @Override
                            public LocalizeValue getDoNotShowMessage() {
                                return CommonLocalize.dialogOptionsDoNotShow();
                            }
                        })
                        .show();

                    switch (res) {
                        case Messages.CANCEL -> {
                            return true;
                        }
                        case Messages.NO -> {
                        }
                        case Messages.YES -> { // evaluate finally
                            evaluateAndAct(project, stackFrame, sb, callback);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void popFrame(DebugProcessImpl debugProcess, DebuggerContextImpl debuggerContext, JavaStackFrame stackFrame) {
        debugProcess.getManagerThread()
            .schedule(debugProcess.createPopFrameCommand(debuggerContext, stackFrame.getStackFrameProxy()));
    }

    @RequiredUIAccess
    private static void evaluateAndAct(
        Project project,
        JavaStackFrame stackFrame,
        StringBuilder sb,
        XDebuggerEvaluator.XEvaluationCallback callback
    ) {
        XDebuggerEvaluator evaluator = stackFrame.getEvaluator();
        if (evaluator != null) {
            evaluator.evaluate(
                XExpression.fromText(sb.toString(), EvaluationMode.CODE_FRAGMENT),
                callback,
                stackFrame.getSourcePosition()
            );
        }
        else {
            Messages.showMessageDialog(
                project,
                XDebuggerLocalize.xdebuggerEvaluateStackFrameHasNotEvaluator().get(),
                XDebuggerLocalize.xdebuggerResetFrameTitle().get(),
                UIUtil.getErrorIcon()
            );
        }
    }

    public static void showError(Project project, LocalizeValue message, LocalizeValue title) {
        project.getApplication().invokeLater(
            () -> Messages.showMessageDialog(project, message.get(), title.get(), UIUtil.getErrorIcon()),
            ModalityState.any()
        );
    }

    @RequiredReadAction
    private static List<PsiStatement> getFinallyStatements(Project project, @Nullable SourcePosition position) {
        if (position == null) {
            return Collections.emptyList();
        }
        List<PsiStatement> res = new ArrayList<>();
        PsiElement element = position.getFile().findElementAt(position.getOffset());
        PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
        while (tryStatement != null) {
            PsiResourceList resourceList = tryStatement.getResourceList();
            if (resourceList != null) {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                for (PsiResourceListElement listElement : resourceList) {
                    String varName = getResourceName(listElement);
                    if (varName != null) {
                        res.add(factory.createStatementFromText("if (" + varName + " != null) " + varName + ".close();", tryStatement));
                    }
                }
            }
            PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if (finallyBlock != null) {
                ContainerUtil.addAll(res, finallyBlock.getStatements());
            }
            tryStatement = PsiTreeUtil.getParentOfType(tryStatement, PsiTryStatement.class);
        }
        return res;
    }

    @RequiredReadAction
    private static String getResourceName(PsiResourceListElement resource) {
        if (resource instanceof PsiResourceVariable resourceVar) {
            return resourceVar.getName();
        }
        else if (resource instanceof PsiResourceExpression resourceExpr) {
            return resourceExpr.getExpression().getText();
        }
        LOG.error("Unknown PsiResourceListElement type: " + resource.getClass());
        return null;
    }
}
