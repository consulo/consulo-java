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
package com.intellij.java.debugger.impl;

import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.StackFrameContext;
import com.intellij.java.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.TextWithImports;
import com.intellij.java.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.java.debugger.impl.actions.DebuggerAction;
import com.intellij.java.debugger.impl.apiAdapters.TransportServiceWrapper;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.evaluation.TextWithImportsImpl;
import com.intellij.java.debugger.impl.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeExpression;
import com.intellij.java.debugger.impl.ui.tree.DebuggerTreeNode;
import com.intellij.java.debugger.impl.ui.tree.render.BatchEvaluator;
import com.intellij.java.language.impl.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.application.util.function.ThrowableComputable;
import consulo.component.ProcessCanceledException;
import consulo.dataContext.DataContext;
import consulo.execution.debug.breakpoint.XExpression;
import consulo.ide.impl.idea.xdebugger.impl.breakpoints.XExpressionState;
import consulo.internal.com.sun.jdi.InternalException;
import consulo.internal.com.sun.jdi.ObjectCollectedException;
import consulo.internal.com.sun.jdi.VMDisconnectedException;
import consulo.internal.com.sun.jdi.Value;
import consulo.internal.com.sun.jdi.connect.spi.TransportService;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.io.NetUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.JDOMExternalizerUtil;
import consulo.util.xml.serializer.SkipDefaultValuesSerializationFilters;
import consulo.util.xml.serializer.XmlSerializer;
import jakarta.inject.Singleton;
import org.jdom.Element;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;

@Singleton
public class DebuggerUtilsImpl extends DebuggerUtilsEx
{
	public static final Key<PsiType> PSI_TYPE_KEY = Key.create("PSI_TYPE_KEY");
	private static final Logger LOG = Logger.getInstance(DebuggerUtilsImpl.class);

	@Override
	public PsiExpression substituteThis(PsiExpression expressionWithThis, PsiExpression howToEvaluateThis, Value howToEvaluateThisValue, StackFrameContext context) throws EvaluateException
	{
		return DebuggerTreeNodeExpression.substituteThis(expressionWithThis, howToEvaluateThis, howToEvaluateThisValue);
	}

	@Override
	public EvaluatorBuilder getEvaluatorBuilder()
	{
		return EvaluatorBuilderImpl.getInstance();
	}

	@Override
	public DebuggerTreeNode getSelectedNode(DataContext context)
	{
		return DebuggerAction.getSelectedNode(context);
	}

	@Override
	public DebuggerContextImpl getDebuggerContext(DataContext context)
	{
		return DebuggerAction.getDebuggerContext(context);
	}

	@Override
	@SuppressWarnings({"HardCodedStringLiteral"})
	public Element writeTextWithImports(TextWithImports text)
	{
		Element element = new Element("TextWithImports");

		element.setAttribute("text", text.toExternalForm());
		element.setAttribute("type", text.getKind() == CodeFragmentKind.EXPRESSION ? "expression" : "code fragment");
		return element;
	}

	@Override
	@SuppressWarnings({"HardCodedStringLiteral"})
	public TextWithImports readTextWithImports(Element element)
	{
		LOG.assertTrue("TextWithImports".equals(element.getName()));

		String text = element.getAttributeValue("text");
		if("expression".equals(element.getAttributeValue("type")))
		{
			return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, text);
		}
		else
		{
			return new TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, text);
		}
	}

	@Override
	public void writeTextWithImports(Element root, String name, TextWithImports value)
	{
		if(value.getKind() == CodeFragmentKind.EXPRESSION)
		{
			JDOMExternalizerUtil.writeField(root, name, value.toExternalForm());
		}
		else
		{
			Element element = JDOMExternalizerUtil.writeOption(root, name);
			XExpression expression = TextWithImportsImpl.toXExpression(value);
			if(expression != null)
			{
				XmlSerializer.serializeInto(new XExpressionState(expression), element, new SkipDefaultValuesSerializationFilters());
			}
		}
	}

	@Override
	public TextWithImports readTextWithImports(Element root, String name)
	{
		String s = JDOMExternalizerUtil.readField(root, name);
		if(s != null)
		{
			return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, s);
		}
		else
		{
			Element option = JDOMExternalizerUtil.getOption(root, name);
			if(option != null)
			{
				XExpressionState state = new XExpressionState();
				XmlSerializer.deserializeInto(state, option);
				return TextWithImportsImpl.fromXExpression(state.toXExpression());
			}
		}
		return null;
	}

	@Override
	public TextWithImports createExpressionWithImports(String expression)
	{
		return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expression);
	}

	@Override
	public PsiElement getContextElement(StackFrameContext context)
	{
		return PositionUtil.getContextElement(context);
	}

	@Nonnull
	public static Pair<PsiElement, PsiType> getPsiClassAndType(@Nullable String className, Project project)
	{
		PsiElement contextClass = null;
		PsiType contextType = null;
		if(!StringUtil.isEmpty(className))
		{
			PsiPrimitiveType primitiveType = PsiJavaParserFacadeImpl.getPrimitiveType(className);
			if(primitiveType != null)
			{
				contextClass = JavaPsiFacade.getInstance(project).findClass(primitiveType.getBoxedTypeName(), GlobalSearchScope.allScope(project));
				contextType = primitiveType;
			}
			else
			{
				contextClass = findClass(className, project, GlobalSearchScope.allScope(project));
				if(contextClass != null)
				{
					contextClass = contextClass.getNavigationElement();
				}
				if(contextClass instanceof PsiCompiledElement)
				{
					contextClass = ((PsiCompiledElement) contextClass).getMirror();
				}
				contextType = getType(className, project);
			}
			if(contextClass != null)
			{
				contextClass.putUserData(PSI_TYPE_KEY, contextType);
			}
		}
		return Pair.create(contextClass, contextType);
	}

	@Override
	public PsiClass chooseClassDialog(String title, Project project)
	{
		TreeClassChooser dialog = TreeClassChooserFactory.getInstance(project).createAllProjectScopeChooser(title);
		dialog.showDialog();
		return dialog.getSelected();
	}

	@Override
	@Nonnull
	public TransportService.ListenKey findAvailableDebugAddress(final int type) throws ExecutionException
	{
		final TransportServiceWrapper transportService = TransportServiceWrapper.createTransportService(type);

		if(type == DebuggerSettings.SOCKET_TRANSPORT)
		{
			final int freePort;
			try
			{
				freePort = NetUtil.findAvailableSocketPort();
			}
			catch(IOException e)
			{
				throw new ExecutionException(DebugProcessImpl.processError(e));
			}
			return new TransportService.ListenKey()
			{
				@Override
				public String address()
				{
					return Integer.toString(freePort);
				}
			};
		}

		try
		{
			TransportService.ListenKey address = transportService.startListening();
			transportService.stopListening(address);
			return address;
		}
		catch(IOException e)
		{
			throw new ExecutionException(DebugProcessImpl.processError(e));
		}
	}

	public static boolean isRemote(DebugProcess debugProcess)
	{
		return Boolean.TRUE.equals(debugProcess.getUserData(BatchEvaluator.REMOTE_SESSION_KEY));
	}

	public static <T, E extends Exception> T suppressExceptions(ThrowableComputable<T, E> supplier, T defaultValue) throws E
	{
		return suppressExceptions(supplier, defaultValue, true, null);
	}

	public static <T, E extends Exception> T suppressExceptions(ThrowableComputable<T, E> supplier, T defaultValue, boolean ignorePCE, Class<E> rethrow) throws E
	{
		try
		{
			return supplier.compute();
		}
		catch(ProcessCanceledException e)
		{
			if(!ignorePCE)
			{
				throw e;
			}
		}
		catch(VMDisconnectedException | ObjectCollectedException e)
		{
			throw e;
		}
		catch(InternalException e)
		{
			LOG.info(e);
		}
		catch(Exception | AssertionError e)
		{
			if(rethrow != null && rethrow.isInstance(e))
			{
				throw e;
			}
			else
			{
				LOG.error(e);
			}
		}
		return defaultValue;
	}
}