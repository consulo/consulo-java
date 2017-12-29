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
package com.intellij.debugger;

import org.jetbrains.annotations.PropertyKey;
import com.intellij.AbstractBundle;
import com.intellij.execution.configurations.RemoteConnection;

public class DebuggerBundle extends AbstractBundle
{
	public static String getAddressDisplayName(final RemoteConnection connection)
	{
		return connection.isUseSockets() ? connection.getHostName() + ":" + connection.getAddress() : connection.getAddress();
	}

	public static String getTransportName(final RemoteConnection connection)
	{
		return connection.isUseSockets() ? message("transport.name.socket") : message("transport.name.shared.memory");
	}

	private static final DebuggerBundle ourInstance = new DebuggerBundle();

	private DebuggerBundle()
	{
		super("messages.DebuggerBundle");
	}

	public static String message(@PropertyKey(resourceBundle = "messages.DebuggerBundle") String key)
	{
		return ourInstance.getMessage(key);
	}

	public static String message(@PropertyKey(resourceBundle = "messages.DebuggerBundle") String key, Object... params)
	{
		return ourInstance.getMessage(key, params);
	}
}
