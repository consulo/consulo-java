/*
 * Copyright 2000-2009 JetBrains s.r.o.
 * Copyright 2013-2015 must-be.org
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
package com.intellij.java.debugger.impl.apiAdapters;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import jakarta.annotation.Nonnull;

import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import consulo.process.ExecutionException;
import consulo.logging.Logger;
import consulo.util.collection.ArrayUtil;
import consulo.internal.com.sun.jdi.connect.spi.TransportService;
import consulo.java.debugger.impl.apiAdapters.TransportClassDelegates;

public class TransportServiceWrapper
{
	private static final Logger LOGGER = Logger.getInstance(TransportServiceWrapper.class);

	@Nonnull
	public static TransportServiceWrapper createTransportService(int type) throws ExecutionException
	{
		Class<?> transportClass = null;
		switch(type)
		{
			case DebuggerSettings.SOCKET_TRANSPORT:
				transportClass = TransportClassDelegates.getSocketTransportServiceClass();
				break;
			case DebuggerSettings.SHMEM_TRANSPORT:
				transportClass = TransportClassDelegates.getSharedMemoryTransportServiceClass();
				if(transportClass == null)
				{
					transportClass = TransportClassDelegates.getSocketTransportServiceClass();
				}
				break;
		}

		try
		{
			return new TransportServiceWrapper(transportClass);
		}
		catch(Exception e)
		{
			throw new ExecutionException(e.getClass().getName() + " : " + e.getMessage());
		}
	}

	private final TransportService myDelegateObject;
	private final Class<?> myDelegateClass;

	private TransportServiceWrapper(Class<?> delegateClass) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
			InstantiationException
	{
		myDelegateClass = delegateClass;
		final Constructor constructor = delegateClass.getDeclaredConstructor(ArrayUtil.EMPTY_CLASS_ARRAY);
		constructor.setAccessible(true);
		myDelegateObject = (TransportService) constructor.newInstance(ArrayUtil.EMPTY_OBJECT_ARRAY);
	}

	@Nonnull
	public TransportService.ListenKey startListening() throws IOException
	{
		return myDelegateObject.startListening();
	}

	public void stopListening(final TransportService.ListenKey listenKey) throws IOException
	{
		myDelegateObject.stopListening(listenKey);
	}

	@Nonnull
	public String transportId()
	{
		if(myDelegateClass == TransportClassDelegates.getSharedMemoryTransportServiceClass())
		{
			return "dt_shmem";
		}
		else if(myDelegateClass == TransportClassDelegates.getSocketTransportServiceClass())
		{
			return "dt_socket";
		}

		LOGGER.error("Unknown service: " + myDelegateClass.getName());
		return "<unknown>";
	}
}
