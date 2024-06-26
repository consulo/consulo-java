/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.ui.impl.watch;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.engine.ContextUtil;
import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.engine.JavaValueModifier;
import com.intellij.java.debugger.engine.StackFrameContext;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.engine.evaluation.TextWithImports;
import com.intellij.java.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.java.debugger.engine.evaluation.expression.Modifier;
import com.intellij.java.debugger.impl.engine.evaluation.expression.UnsupportedExpressionException;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import consulo.application.ReadAction;
import consulo.application.dumb.IndexNotReadyException;
import consulo.project.Project;
import consulo.language.psi.PsiCodeFragment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionCodeFragment;
import consulo.execution.debug.frame.XValueModifier;
import consulo.internal.com.sun.jdi.ClassNotLoadedException;
import consulo.internal.com.sun.jdi.IncompatibleThreadStateException;
import consulo.internal.com.sun.jdi.InvalidTypeException;
import consulo.internal.com.sun.jdi.InvocationException;
import consulo.internal.com.sun.jdi.ObjectCollectedException;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.Value;

/**
 * @author lex
 */
public abstract class EvaluationDescriptor extends ValueDescriptorImpl
{
	private Modifier myModifier;
	protected TextWithImports myText;

	protected EvaluationDescriptor(TextWithImports text, Project project, Value value)
	{
		super(project, value);
		myText = text;
	}

	protected EvaluationDescriptor(TextWithImports text, Project project)
	{
		super(project);
		setLvalue(false);
		myText = text;
	}

	protected abstract EvaluationContextImpl getEvaluationContext(EvaluationContextImpl evaluationContext);

	protected abstract PsiCodeFragment getEvaluationCode(StackFrameContext context) throws EvaluateException;

	public PsiCodeFragment createCodeFragment(PsiElement context)
	{
		TextWithImports text = getEvaluationText();
		return DebuggerUtilsEx.findAppropriateCodeFragmentFactory(text, context).createCodeFragment(text, context, myProject);
	}

	public final Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException
	{
		try
		{
			PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(() ->
			{
			});

			EvaluationContextImpl thisEvaluationContext = getEvaluationContext(evaluationContext);
			SourcePosition position = ContextUtil.getSourcePosition(evaluationContext);
			PsiElement psiContext = ContextUtil.getContextElement(evaluationContext, position);

			ExpressionEvaluator evaluator = ReadAction.compute(() ->
			{
				PsiCodeFragment code = getEvaluationCode(thisEvaluationContext);
				try
				{
					return DebuggerUtilsEx.findAppropriateCodeFragmentFactory(getEvaluationText(), psiContext).getEvaluatorBuilder().build(code, position);
				}
				catch(UnsupportedExpressionException ex)
				{
					ExpressionEvaluator eval = CompilingEvaluatorImpl.create(myProject, code.getContext(), element -> code);
					if(eval != null)
					{
						return eval;
					}
					throw ex;
				}
			});

			if(!thisEvaluationContext.getDebugProcess().isAttached())
			{
				throw EvaluateExceptionUtil.PROCESS_EXITED;
			}
			StackFrameProxyImpl frameProxy = thisEvaluationContext.getFrameProxy();
			if(frameProxy == null)
			{
				throw EvaluateExceptionUtil.NULL_STACK_FRAME;
			}

			Value value = evaluator.evaluate(thisEvaluationContext);
			DebuggerUtilsEx.keep(value, thisEvaluationContext);

			myModifier = evaluator.getModifier();
			setLvalue(myModifier != null);

			return value;
		}
		catch(IndexNotReadyException ex)
		{
			throw new EvaluateException("Evaluation is not possible during indexing", ex);
		}
		catch(final EvaluateException ex)
		{
			throw new EvaluateException(ex.getLocalizedMessage(), ex);
		}
		catch(ObjectCollectedException ex)
		{
			throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
		}
	}

	public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException
	{
		PsiElement evaluationCode = getEvaluationCode(context);
		if(evaluationCode instanceof PsiExpressionCodeFragment)
		{
			return ((PsiExpressionCodeFragment) evaluationCode).getExpression();
		}
		else
		{
			throw new EvaluateException(DebuggerBundle.message("error.cannot.create.expression.from.code.fragment"), null);
		}
	}

	@Nullable
	public Modifier getModifier()
	{
		return myModifier;
	}

	public boolean canSetValue()
	{
		return super.canSetValue() && myModifier != null && myModifier.canSetValue();
	}

	public TextWithImports getEvaluationText()
	{
		return myText;
	}

	@Override
	public XValueModifier getModifier(JavaValue value)
	{
		return new JavaValueModifier(value)
		{
			@Override
			protected void setValueImpl(@Nonnull String expression, @Nonnull XModificationCallback callback)
			{
				final EvaluationDescriptor evaluationDescriptor = EvaluationDescriptor.this;
				if(evaluationDescriptor.canSetValue())
				{
					final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(getProject()).getContext();
					set(expression, callback, debuggerContext, new SetValueRunnable()
					{
						public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException, InvalidTypeException, EvaluateException
						{
							final Modifier modifier = evaluationDescriptor.getModifier();
							modifier.setValue(preprocessValue(evaluationContext, newValue, modifier.getExpectedType()));
							update(debuggerContext);
						}

						public ReferenceType loadClass(EvaluationContextImpl evaluationContext,
								String className) throws InvocationException, ClassNotLoadedException, IncompatibleThreadStateException, InvalidTypeException, EvaluateException
						{
							return evaluationContext.getDebugProcess().loadClass(evaluationContext, className, evaluationContext.getClassLoader());
						}
					});
				}
			}
		};
	}
}
