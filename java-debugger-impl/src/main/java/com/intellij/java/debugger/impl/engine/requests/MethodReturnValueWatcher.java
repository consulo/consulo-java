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
package com.intellij.java.debugger.impl.engine.requests;

import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import consulo.internal.com.sun.jdi.*;
import consulo.internal.com.sun.jdi.event.Event;
import consulo.internal.com.sun.jdi.event.MethodEntryEvent;
import consulo.internal.com.sun.jdi.event.MethodExitEvent;
import consulo.internal.com.sun.jdi.request.EventRequest;
import consulo.internal.com.sun.jdi.request.EventRequestManager;
import consulo.internal.com.sun.jdi.request.MethodEntryRequest;
import consulo.internal.com.sun.jdi.request.MethodExitRequest;
import consulo.java.debugger.impl.JavaRegistry;
import consulo.logging.Logger;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 23, 2006
 */
public class MethodReturnValueWatcher
{
	private static final Logger LOG = Logger.getInstance(MethodReturnValueWatcher.class);
	private
	@Nullable
	Method myLastExecutedMethod;
	private
	@Nullable
	Value myLastMethodReturnValue;

	private ThreadReference myThread;
	private
	@Nullable
	MethodEntryRequest myEntryRequest;
	private
	@Nullable
	Method myEntryMethod;
	private
	@Nullable
	MethodExitRequest myExitRequest;

	private volatile boolean myEnabled;
	private boolean myFeatureEnabled;
	private final EventRequestManager myRequestManager;

	public MethodReturnValueWatcher(EventRequestManager requestManager)
	{
		myRequestManager = requestManager;
		myFeatureEnabled = DebuggerSettings.getInstance().WATCH_RETURN_VALUES;
	}

	private void processMethodExitEvent(MethodExitEvent event)
	{
		if(LOG.isDebugEnabled())
		{
			LOG.debug("<- " + event.method());
		}
		try
		{
			if(JavaRegistry.DEBUGGER_WATCH_RETURN_SPEEDUP && Comparing.equal(myEntryMethod, event.method()))
			{
				LOG.debug("Now watching all");
				enableEntryWatching(true);
				createExitRequest().enable();
			}
			final Method method = event.method();
			//myLastMethodReturnValue = event.returnValue();

			final Value retVal = (Value) event.returnValue();

			if(method == null || !"void".equals(method.returnTypeName()))
			{
				// remember methods with non-void return types only
				myLastExecutedMethod = method;
				myLastMethodReturnValue = retVal;
			}
		}
		catch(UnsupportedOperationException ex)
		{
			LOG.error(ex);
		}
	}

	private void processMethodEntryEvent(MethodEntryEvent event)
	{
		if(LOG.isDebugEnabled())
		{
			LOG.debug("-> " + event.method());
		}
		try
		{
			if(myEntryRequest != null && myEntryRequest.isEnabled())
			{
				myExitRequest = createExitRequest();
				myExitRequest.addClassFilter(event.method().declaringType());
				myEntryMethod = event.method();
				myExitRequest.enable();

				if(LOG.isDebugEnabled())
				{
					LOG.debug("Now watching only " + event.method());
				}

				enableEntryWatching(false);
			}
		}
		catch(VMDisconnectedException e)
		{
			throw e;
		}
		catch(Exception e)
		{
			LOG.error(e);
		}
	}

	private void enableEntryWatching(boolean enable)
	{
		if(myEntryRequest != null)
		{
			myEntryRequest.setEnabled(enable);
		}
	}

	@Nullable
	public Method getLastExecutedMethod()
	{
		return myLastExecutedMethod;
	}

	@Nullable
	public Value getLastMethodReturnValue()
	{
		return myLastMethodReturnValue;
	}

	public boolean isFeatureEnabled()
	{
		return myFeatureEnabled;
	}

	public boolean isEnabled()
	{
		return myEnabled;
	}

	public void setFeatureEnabled(final boolean featureEnabled)
	{
		myFeatureEnabled = featureEnabled;
		clear();
	}

	public void enable(ThreadReference thread)
	{
		setTrackingEnabled(true, thread);
	}

	public void disable()
	{
		setTrackingEnabled(false, null);
	}

	private void setTrackingEnabled(boolean trackingEnabled, final ThreadReference thread)
	{
		myEnabled = trackingEnabled;
		updateRequestState(trackingEnabled && myFeatureEnabled, thread);
	}

	public void clear()
	{
		myLastExecutedMethod = null;
		myLastMethodReturnValue = null;
		myThread = null;
	}

	private void updateRequestState(final boolean enabled, @Nullable final ThreadReference thread)
	{
		DebuggerManagerThreadImpl.assertIsManagerThread();
		try
		{
			if(myEntryRequest != null)
			{
				myRequestManager.deleteEventRequest(myEntryRequest);
				myEntryRequest = null;
			}
			if(myExitRequest != null)
			{
				myRequestManager.deleteEventRequest(myExitRequest);
				myExitRequest = null;
			}
			if(enabled)
			{
				clear();
				myThread = thread;

				if(JavaRegistry.DEBUGGER_WATCH_RETURN_SPEEDUP)
				{
					createEntryRequest().enable();
				}
				createExitRequest().enable();
			}
		}
		catch(ObjectCollectedException ignored)
		{
		}
	}

	private static final String WATCHER_REQUEST_KEY = "WATCHER_REQUEST_KEY";

	private MethodEntryRequest createEntryRequest()
	{
		DebuggerManagerThreadImpl.assertIsManagerThread(); // to ensure EventRequestManager synchronization
		myEntryRequest = prepareRequest(myRequestManager.createMethodEntryRequest());
		return myEntryRequest;
	}

	@Nonnull
	private MethodExitRequest createExitRequest()
	{
		DebuggerManagerThreadImpl.assertIsManagerThread(); // to ensure EventRequestManager synchronization
		if(myExitRequest != null)
		{
			myRequestManager.deleteEventRequest(myExitRequest);
		}
		myExitRequest = prepareRequest(myRequestManager.createMethodExitRequest());
		return myExitRequest;
	}

	@Nonnull
	private <T extends EventRequest> T prepareRequest(T request)
	{
		request.setSuspendPolicy(JavaRegistry.DEBUGGER_WATCH_RETURN_SPEEDUP ? EventRequest.SUSPEND_EVENT_THREAD : EventRequest.SUSPEND_NONE);
		if(myThread != null)
		{
			if(request instanceof MethodEntryRequest)
			{
				((MethodEntryRequest) request).addThreadFilter(myThread);
			}
			else if(request instanceof MethodExitRequest)
			{
				((MethodExitRequest) request).addThreadFilter(myThread);
			}
		}
		request.putProperty(WATCHER_REQUEST_KEY, true);
		return request;
	}

	public boolean processEvent(Event event)
	{
		EventRequest request = event.request();
		if(request == null || request.getProperty(WATCHER_REQUEST_KEY) == null)
		{
			return false;
		}

		if(event instanceof MethodEntryEvent)
		{
			processMethodEntryEvent(((MethodEntryEvent) event));
		}
		else if(event instanceof MethodExitEvent)
		{
			processMethodExitEvent(((MethodExitEvent) event));
		}
		return true;
	}
}
