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
package com.intellij.java.debugger.impl;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.jdi.VirtualMachineProxyImpl;
import com.intellij.java.debugger.impl.ui.breakpoints.BreakpointManager;
import consulo.component.ProcessCanceledException;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.MessageCategory;
import consulo.ui.ex.awt.UIUtil;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lex
 */
class ReloadClassesWorker
{
	private static final Logger LOG = Logger.getInstance(ReloadClassesWorker.class);
	private final DebuggerSession myDebuggerSession;
	private final HotSwapProgress myProgress;

	public ReloadClassesWorker(DebuggerSession session, HotSwapProgress progress)
	{
		myDebuggerSession = session;
		myProgress = progress;
	}

	private DebugProcessImpl getDebugProcess()
	{
		return myDebuggerSession.getProcess();
	}

	private void processException(Throwable e)
	{
		if(e.getMessage() != null)
		{
			myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, e.getMessage());
		}

		if(e instanceof ProcessCanceledException)
		{
			myProgress.addMessage(myDebuggerSession, MessageCategory.INFORMATION, DebuggerBundle.message("error.operation.canceled"));
			return;
		}

		if(e instanceof UnsupportedOperationException)
		{
			myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, DebuggerBundle.message("error.operation.not.supported.by.vm"));
		}
		else if(e instanceof NoClassDefFoundError)
		{
			myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, DebuggerBundle.message("error.class.def.not.found", e.getLocalizedMessage()));
		}
		else if(e instanceof VerifyError)
		{
			myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, DebuggerBundle.message("error.verification.error", e.getLocalizedMessage()));
		}
		else if(e instanceof UnsupportedClassVersionError)
		{
			myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, DebuggerBundle.message("error.unsupported.class.version", e.getLocalizedMessage()));
		}
		else if(e instanceof ClassFormatError)
		{
			myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, DebuggerBundle.message("error.class.format.error", e.getLocalizedMessage()));
		}
		else if(e instanceof ClassCircularityError)
		{
			myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, DebuggerBundle.message("error.class.circularity.error", e.getLocalizedMessage()));
		}
		else
		{
			myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, DebuggerBundle.message("error.exception.while.reloading", e.getClass().getName(), e.getLocalizedMessage()));
		}
	}

	public void reloadClasses(final Map<String, HotSwapFile> modifiedClasses)
	{
		DebuggerManagerThreadImpl.assertIsManagerThread();

		if(modifiedClasses == null || modifiedClasses.size() == 0)
		{
			myProgress.addMessage(myDebuggerSession, MessageCategory.INFORMATION, DebuggerBundle.message("status.hotswap.loaded.classes.up.to.date"));
			return;
		}

		final DebugProcessImpl debugProcess = getDebugProcess();
		final VirtualMachineProxyImpl virtualMachineProxy = debugProcess.getVirtualMachineProxy();

		final Project project = debugProcess.getProject();
		final BreakpointManager breakpointManager = (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager();
		breakpointManager.disableBreakpoints(debugProcess);

		//virtualMachineProxy.suspend();

		try
		{
			RedefineProcessor redefineProcessor = new RedefineProcessor(virtualMachineProxy);

			int processedClassesCount = 0;
			for(final String qualifiedName : modifiedClasses.keySet())
			{
				processedClassesCount++;
				if(qualifiedName != null)
				{
					myProgress.setText(qualifiedName);
					myProgress.setFraction(processedClassesCount / (double) modifiedClasses.size());
				}
				final HotSwapFile fileDescr = modifiedClasses.get(qualifiedName);
				final byte[] content;
				try
				{
					content = Files.readAllBytes(fileDescr.file);
				}
				catch(IOException e)
				{
					reportProblem(qualifiedName, e);
					continue;
				}
				redefineProcessor.processClass(qualifiedName, content);
			}
			redefineProcessor.processPending();
			myProgress.setFraction(1);

			final int partiallyRedefinedClassesCount = redefineProcessor.getPartiallyRedefinedClassesCount();
			if(partiallyRedefinedClassesCount == 0)
			{
				myProgress.addMessage(myDebuggerSession, MessageCategory.INFORMATION, DebuggerBundle.message("status.classes.reloaded", redefineProcessor.getProcessedClassesCount()));
			}
			else
			{
				final String message = DebuggerBundle.message("status.classes.not.all.versions.reloaded", partiallyRedefinedClassesCount, redefineProcessor.getProcessedClassesCount());
				myProgress.addMessage(myDebuggerSession, MessageCategory.WARNING, message);
			}

			if(LOG.isDebugEnabled())
			{
				LOG.debug("classes reloaded");
			}
		}
		catch(Throwable e)
		{
			processException(e);
		}

		//noinspection SSBasedInspection
		UIUtil.invokeAndWaitIfNeeded(new Runnable()
		{
			public void run()
			{
				if(project.isDisposed())
				{
					return;
				}
				final BreakpointManager breakpointManager = (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager();
				breakpointManager.reloadBreakpoints();
				debugProcess.getRequestsManager().clearWarnings();
				if(LOG.isDebugEnabled())
				{
					LOG.debug("requests updated");
					LOG.debug("time stamp set");
				}
				myDebuggerSession.refresh(false);

        /*
		debugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
          protected void action() throws Exception {
            try {
              breakpointManager.enableBreakpoints(debugProcess);
            }
            catch (Exception e) {
              processException(e);
            }
            //try {
            //  virtualMachineProxy.resume();
            //}
            //catch (Exception e) {
            //  processException(e);
            //}
          }

          public Priority getPriority() {
            return Priority.HIGH;
          }
        });
        */
			}
		});
		try
		{
			breakpointManager.enableBreakpoints(debugProcess);
		}
		catch(Exception e)
		{
			processException(e);
		}
	}

	private void reportProblem(final String qualifiedName, @Nullable Exception ex)
	{
		String reason = null;
		if(ex != null)
		{
			LOG.warn(ex);
			reason = ex.getLocalizedMessage();
		}
		if(reason == null || reason.length() == 0)
		{
			reason = DebuggerBundle.message("error.io.error");
		}
		final StringBuilder buf = new StringBuilder();
		buf.append(qualifiedName).append(" : ").append(reason);
		myProgress.addMessage(myDebuggerSession, MessageCategory.ERROR, buf.toString());
	}

	private static class RedefineProcessor
	{
		/**
		 * number of classes that will be reloaded in one go.
		 * Such restriction is needed to deal with big number of classes being reloaded
		 */
		private static final int CLASSES_CHUNK_SIZE = 100;
		private final VirtualMachineProxyImpl myVirtualMachineProxy;
		private final Map<ReferenceType, byte[]> myRedefineMap = new HashMap<ReferenceType, byte[]>();
		private int myProcessedClassesCount;
		private int myPartiallyRedefinedClassesCount;

		public RedefineProcessor(VirtualMachineProxyImpl virtualMachineProxy)
		{
			myVirtualMachineProxy = virtualMachineProxy;
		}

		public void processClass(String qualifiedName, byte[] content) throws Throwable
		{
			final List<ReferenceType> vmClasses = myVirtualMachineProxy.classesByName(qualifiedName);
			if(vmClasses.isEmpty())
			{
				return;
			}

			if(vmClasses.size() == 1)
			{
				myRedefineMap.put(vmClasses.get(0), content);
				if(myRedefineMap.size() >= CLASSES_CHUNK_SIZE)
				{
					processChunk();
				}
				return;
			}

			int redefinedVersionsCount = 0;
			Throwable error = null;
			for(ReferenceType vmClass : vmClasses)
			{
				try
				{
					myVirtualMachineProxy.redefineClasses(Collections.singletonMap(vmClass, content));
					redefinedVersionsCount++;
				}
				catch(Throwable t)
				{
					error = t;
				}
			}
			if(redefinedVersionsCount == 0)
			{
				throw error;
			}

			if(redefinedVersionsCount < vmClasses.size())
			{
				myPartiallyRedefinedClassesCount++;
			}
			myProcessedClassesCount++;
		}

		private void processChunk() throws Throwable
		{
			// reload this portion of classes and clear the map to free memory
			try
			{
				myVirtualMachineProxy.redefineClasses(myRedefineMap);
				myProcessedClassesCount += myRedefineMap.size();
			}
			finally
			{
				myRedefineMap.clear();
			}
		}

		public void processPending() throws Throwable
		{
			if(myRedefineMap.size() > 0)
			{
				processChunk();
			}
		}

		public int getProcessedClassesCount()
		{
			return myProcessedClassesCount;
		}

		public int getPartiallyRedefinedClassesCount()
		{
			return myPartiallyRedefinedClassesCount;
		}
	}
}
