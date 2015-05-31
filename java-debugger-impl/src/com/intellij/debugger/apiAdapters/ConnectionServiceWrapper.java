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
package com.intellij.debugger.apiAdapters;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.consulo.lombok.annotations.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ArrayUtil;
import consulo.internal.com.sun.jdi.Bootstrap;
import consulo.internal.com.sun.jdi.VMDisconnectedException;
import consulo.internal.com.sun.jdi.VirtualMachine;
import consulo.internal.com.sun.jdi.VirtualMachineManager;

/**
 * @author max
 */
@Logger
public class ConnectionServiceWrapper
{
	private static Class<?> ourDelegateClass;

	static
	{
		try
		{
			//noinspection HardCodedStringLiteral
			ourDelegateClass = SystemInfo.JAVA_VERSION.startsWith("1.4") ? Class.forName("consulo.internal.com.sun.tools.jdi.ConnectionService") :
					Class.forName("consulo.internal.com.sun.jdi.connect.spi.Connection");
		}
		catch(ClassNotFoundException e)
		{
			LOGGER.error(e);
		}
	}

	private final Object myConnection;

	public ConnectionServiceWrapper(final Object connection)
	{
		myConnection = connection;
	}

	public void close() throws IOException
	{
		try
		{
			//noinspection HardCodedStringLiteral
			final Method method = ourDelegateClass.getMethod("close", ArrayUtil.EMPTY_CLASS_ARRAY);
			method.invoke(myConnection, ArrayUtil.EMPTY_OBJECT_ARRAY);
		}
		catch(NoSuchMethodException e)
		{
			LOGGER.error(e);
		}
		catch(IllegalAccessException e)
		{
			LOGGER.error(e);
		}
		catch(InvocationTargetException e)
		{
			final Throwable cause = e.getCause();
			if(cause instanceof IOException)
			{
				throw (IOException) cause;
			}
			LOGGER.error(e);
		}
	}

	public VirtualMachine createVirtualMachine() throws IOException
	{
		try
		{
			final VirtualMachineManager virtualMachineManager = Bootstrap.virtualMachineManager();
			//noinspection HardCodedStringLiteral
			final Method method = virtualMachineManager.getClass().getMethod("createVirtualMachine", new Class[]{ourDelegateClass});
			return (VirtualMachine) method.invoke(virtualMachineManager, new Object[]{myConnection});
		}
		catch(NoSuchMethodException e)
		{
			LOGGER.error(e);
		}
		catch(IllegalAccessException e)
		{
			LOGGER.error(e);
		}
		catch(InvocationTargetException e)
		{
			final Throwable cause = e.getCause();
			if(cause instanceof IOException)
			{
				throw (IOException) cause;
			}
			if(cause instanceof VMDisconnectedException)
			{
				return null; // ignore this one
			}
			LOGGER.error(e);
		}
		return null;
	}
}
