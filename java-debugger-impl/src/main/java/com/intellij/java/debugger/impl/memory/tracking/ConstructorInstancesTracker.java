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
package com.intellij.java.debugger.impl.memory.tracking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.java.debugger.impl.breakpoints.properties.JavaLineBreakpointProperties;
import com.intellij.java.debugger.DebuggerManager;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.memory.component.InstancesTracker;
import com.intellij.java.debugger.impl.memory.component.MemoryViewDebugProcessData;
import com.intellij.java.debugger.impl.memory.event.InstancesTrackerListener;
import com.intellij.java.debugger.impl.memory.utils.StackFrameItem;
import com.intellij.java.debugger.impl.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.java.debugger.impl.ui.breakpoints.LineBreakpoint;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.project.Project;
import consulo.execution.debug.XDebuggerManager;
import consulo.ide.impl.idea.xdebugger.impl.XDebuggerManagerImpl;
import consulo.ide.impl.idea.xdebugger.impl.breakpoints.LineBreakpointState;
import consulo.ide.impl.idea.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import consulo.disposer.Disposable;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.Method;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.event.LocatableEvent;
import consulo.internal.com.sun.jdi.request.BreakpointRequest;
import consulo.internal.com.sun.jdi.request.EventRequest;

public class ConstructorInstancesTracker implements TrackerForNewInstances, Disposable, BackgroundTracker
{
	private static final int TRACKED_INSTANCES_LIMIT = 2000;
	private final String myClassName;
	private final Project myProject;
	private final MyConstructorBreakpoints myBreakpoint;

	@Nullable
	private HashSet<ObjectReference> myNewObjects = null;

	@Nonnull
	private HashSet<ObjectReference> myTrackedObjects = new HashSet<>();

	private volatile boolean myIsBackgroundMode;
	private volatile boolean myIsBackgroundTrackingEnabled;

	public ConstructorInstancesTracker(@Nonnull ReferenceType ref, @Nonnull XDebugSession debugSession, @Nonnull InstancesTracker instancesTracker)
	{
		myProject = debugSession.getProject();
		myIsBackgroundTrackingEnabled = instancesTracker.isBackgroundTrackingEnabled();

		myClassName = ref.name();
		final DebugProcessImpl debugProcess = (DebugProcessImpl) DebuggerManager.getInstance(myProject).getDebugProcess(debugSession.getDebugProcess().getProcessHandler());

		instancesTracker.addTrackerListener(new InstancesTrackerListener()
		{
			@Override
			public void backgroundTrackingValueChanged(boolean newState)
			{
				if(myIsBackgroundTrackingEnabled != newState)
				{
					myIsBackgroundTrackingEnabled = newState;
					debugProcess.getManagerThread().schedule(new DebuggerCommandImpl()
					{
						@Override
						protected void action() throws Exception
						{
							if(newState)
							{
								myBreakpoint.enable();
							}
							else
							{
								myBreakpoint.disable();
							}
						}
					});
				}
			}
		}, this);

		final JavaLineBreakpointType breakPointType = new JavaLineBreakpointType();

		final XBreakpoint bpn = new XLineBreakpointImpl<>(breakPointType, ((consulo.ide.impl.idea.xdebugger.impl.XDebuggerManagerImpl) XDebuggerManager.getInstance(myProject)).getBreakpointManager(), new JavaLineBreakpointProperties(),
				new LineBreakpointState<>());

		myBreakpoint = new MyConstructorBreakpoints(myProject, bpn);
		myBreakpoint.createRequestForPreparedClass(debugProcess, ref);
	}

	public void obsolete()
	{
		if(myNewObjects != null)
		{
			myNewObjects.forEach(ObjectReference::enableCollection);
		}

		myNewObjects = null;
		if(!myIsBackgroundMode || myIsBackgroundTrackingEnabled)
		{
			myBreakpoint.enable();
		}

		final XDebugSession session = XDebuggerManager.getInstance(myProject).getCurrentSession();
		if(session != null)
		{
			final DebugProcess process = DebuggerManager.getInstance(myProject).getDebugProcess(session.getDebugProcess().getProcessHandler());
			final MemoryViewDebugProcessData data = process.getUserData(MemoryViewDebugProcessData.KEY);
			if(data != null)
			{
				data.getTrackedStacks().release();
			}
		}
	}

	public void commitTracked()
	{
		myNewObjects = myTrackedObjects;
		myTrackedObjects = new HashSet<>();
	}

	@Nonnull
	@Override
	public List<ObjectReference> getNewInstances()
	{
		return myNewObjects == null ? Collections.EMPTY_LIST : new ArrayList<>(myNewObjects);
	}

	@Override
	public int getCount()
	{
		return myNewObjects == null ? 0 : myNewObjects.size();
	}

	public void enable()
	{
		myBreakpoint.enable();
	}

	public void disable()
	{
		myBreakpoint.disable();
	}

	@Override
	public boolean isReady()
	{
		return myNewObjects != null;
	}

	@Override
	public void dispose()
	{
		myBreakpoint.delete();
		myTrackedObjects.clear();
		myNewObjects = null;
	}

	@Override
	public void setBackgroundMode(boolean isBackgroundMode)
	{
		if(myIsBackgroundMode == isBackgroundMode)
		{
			return;
		}

		myIsBackgroundMode = isBackgroundMode;
		if(isBackgroundMode)
		{
			doEnableBackgroundMode();
		}
		else
		{
			doDisableBackgroundMode();
		}
	}

	private void doDisableBackgroundMode()
	{
		myBreakpoint.enable();
	}

	private void doEnableBackgroundMode()
	{
		if(!myIsBackgroundTrackingEnabled)
		{
			myBreakpoint.disable();
		}
	}

	private final class MyConstructorBreakpoints extends LineBreakpoint<JavaLineBreakpointProperties>
	{
		private final List<BreakpointRequest> myRequests = new ArrayList<>();
		private volatile boolean myIsEnabled = false;
		private volatile boolean myIsDeleted = false;

		MyConstructorBreakpoints(Project project, XBreakpoint xBreakpoint)
		{
			super(project, xBreakpoint);
			setVisible(false);
		}

		@Override
		protected void createRequestForPreparedClass(DebugProcessImpl debugProcess, ReferenceType classType)
		{
			classType.methods().stream().filter(Method::isConstructor).forEach(cons ->
			{
				Location loc = cons.location();
				BreakpointRequest breakpointRequest = debugProcess.getRequestsManager().createBreakpointRequest(this, loc);
				myRequests.add(breakpointRequest);
			});

			if(!myIsBackgroundMode || myIsBackgroundTrackingEnabled)
			{
				enable();
			}
		}

		@Override
		public void reload()
		{
		}

		void delete()
		{
			// unable disable all requests here because VMDisconnectedException can be thrown
			myRequests.clear();
			myIsDeleted = true;
		}

		@Override
		public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException
		{
			if(myIsDeleted)
			{
				event.request().disable();
			}
			else
			{
				handleEvent(action, event);
			}

			return false;
		}

		void enable()
		{
			if(!myIsEnabled && !myIsDeleted)
			{
				myRequests.forEach(EventRequest::enable);
				myIsEnabled = true;
			}
		}

		void disable()
		{
			if(myIsEnabled && !myIsDeleted)
			{
				myRequests.forEach(EventRequest::disable);
				myIsEnabled = false;
			}
		}

		private void handleEvent(@Nonnull SuspendContextCommandImpl action, @Nonnull LocatableEvent event)
		{
			try
			{
				SuspendContextImpl suspendContext = action.getSuspendContext();
				if(suspendContext != null)
				{
					final MemoryViewDebugProcessData data = suspendContext.getDebugProcess().getUserData(MemoryViewDebugProcessData.KEY);
					ObjectReference thisRef = getThisObject(suspendContext, event);
					if(thisRef.referenceType().name().equals(myClassName) && data != null)
					{
						thisRef.disableCollection();
						myTrackedObjects.add(thisRef);
						data.getTrackedStacks().addStack(thisRef, StackFrameItem.createFrames(suspendContext, false));
					}
				}
			}
			catch(EvaluateException ignored)
			{
			}

			if(myTrackedObjects.size() >= TRACKED_INSTANCES_LIMIT)
			{
				disable();
			}
		}
	}
}
