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
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.debugger.MultiRequestPositionManager;
import com.intellij.java.debugger.NoDataException;
import com.intellij.java.debugger.PositionManager;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.DebuggerUtilsImpl;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.requests.ClassPrepareRequestor;
import consulo.application.ReadAction;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.ui.console.LineNumbersMapping;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.application.progress.ProgressManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiFile;
import consulo.util.lang.ThreeState;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.VMDisconnectedException;
import consulo.internal.com.sun.jdi.request.ClassPrepareRequest;
import consulo.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class CompoundPositionManager extends PositionManagerEx implements MultiRequestPositionManager
{
	private static final Logger LOG = Logger.getInstance(CompoundPositionManager.class);

	public static final CompoundPositionManager EMPTY = new CompoundPositionManager();

	private final ArrayList<PositionManager> myPositionManagers = new ArrayList<>();

	@SuppressWarnings("UnusedDeclaration")
	public CompoundPositionManager()
	{
	}

	public CompoundPositionManager(PositionManager manager)
	{
		appendPositionManager(manager);
	}

	public void appendPositionManager(PositionManager manager)
	{
		myPositionManagers.remove(manager);
		myPositionManagers.add(0, manager);
		clearCache();
	}

	public void clearCache()
	{
		mySourcePositionCache.clear();
	}

	private final Map<Location, SourcePosition> mySourcePositionCache = new WeakHashMap<>();

	private interface Processor<T>
	{
		T process(PositionManager positionManager) throws NoDataException;
	}

	private <T> T iterate(Processor<T> processor, T defaultValue, SourcePosition position)
	{
		return iterate(processor, defaultValue, position, true);
	}

	private <T> T iterate(Processor<T> processor, T defaultValue, SourcePosition position, boolean ignorePCE)
	{
		for(PositionManager positionManager : myPositionManagers)
		{
			if(position != null)
			{
				Set<? extends FileType> types = positionManager.getAcceptedFileTypes();
				if(types != null && !types.contains(position.getFile().getFileType()))
				{
					continue;
				}
			}
			try
			{
				if(!ignorePCE)
				{
					ProgressManager.checkCanceled();
				}
				return DebuggerUtilsImpl.suppressExceptions(() -> processor.process(positionManager), defaultValue, ignorePCE, NoDataException.class);
			}
			catch(NoDataException ignored)
			{
			}
		}
		return defaultValue;
	}

	@Nullable
	@Override
	public SourcePosition getSourcePosition(final Location location)
	{
		if(location == null)
		{
			return null;
		}

		return ReadAction.nonBlocking(() -> {
			SourcePosition res = null;
			try
			{
				res = mySourcePositionCache.get(location);
			}
			catch(IllegalArgumentException ignored)
			{ // Invalid method id
			}
			if(checkCacheEntry(res, location))
			{
				return res;
			}

			return iterate(positionManager -> {
				SourcePosition res1 = positionManager.getSourcePosition(location);
				try
				{
					mySourcePositionCache.put(location, res1);
				}
				catch(IllegalArgumentException ignored)
				{ // Invalid method id
				}
				return res1;
			}, null, null, false);
		}).executeSynchronously();
	}

	private static boolean checkCacheEntry(@Nullable SourcePosition position, @Nonnull Location location)
	{
		if(position == null)
		{
			return false;
		}
		PsiFile psiFile = position.getFile();
		if(!psiFile.isValid())
		{
			return false;
		}
		String url = DebuggerUtilsEx.getAlternativeSourceUrl(location.declaringType().name(), psiFile.getProject());
		if(url == null)
		{
			return true;
		}
		VirtualFile file = psiFile.getVirtualFile();
		return file != null && url.equals(file.getUrl());
	}

	@Override
	@Nonnull
	public List<ReferenceType> getAllClasses(@Nonnull final SourcePosition classPosition)
	{
		return iterate(positionManager -> positionManager.getAllClasses(classPosition), Collections.emptyList(), classPosition);
	}

	@Override
	@Nonnull
	public List<Location> locationsOfLine(@Nonnull final ReferenceType type, @Nonnull SourcePosition position)
	{
		VirtualFile file = position.getFile().getVirtualFile();
		if(file != null)
		{
			LineNumbersMapping mapping = file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY);
			if(mapping != null)
			{
				int line = mapping.sourceToBytecode(position.getLine() + 1);
				if(line > -1)
				{
					position = SourcePosition.createFromLine(position.getFile(), line - 1);
				}
			}
		}

		final SourcePosition finalPosition = position;
		return iterate(positionManager -> positionManager.locationsOfLine(type, finalPosition), Collections.emptyList(), position);
	}

	@Override
	public ClassPrepareRequest createPrepareRequest(@Nonnull final ClassPrepareRequestor requestor, @Nonnull final SourcePosition position)
	{
		return iterate(positionManager -> positionManager.createPrepareRequest(requestor, position), null, position);
	}

	@Nonnull
	@Override
	public List<ClassPrepareRequest> createPrepareRequests(@Nonnull final ClassPrepareRequestor requestor, @Nonnull final SourcePosition position)
	{
		return iterate(positionManager ->
		{
			if(positionManager instanceof MultiRequestPositionManager)
			{
				return ((MultiRequestPositionManager) positionManager).createPrepareRequests(requestor, position);
			}
			else
			{
				ClassPrepareRequest prepareRequest = positionManager.createPrepareRequest(requestor, position);
				if(prepareRequest == null)
				{
					return Collections.emptyList();
				}
				return Collections.singletonList(prepareRequest);
			}
		}, Collections.emptyList(), position);
	}

	@Nullable
	@Override
	public XStackFrame createStackFrame(@Nonnull StackFrameProxyImpl frame, @Nonnull DebugProcessImpl debugProcess, @Nonnull Location location)
	{
		for(PositionManager positionManager : myPositionManagers)
		{
			if(positionManager instanceof PositionManagerEx)
			{
				try
				{
					XStackFrame xStackFrame = ((PositionManagerEx) positionManager).createStackFrame(frame, debugProcess, location);
					if(xStackFrame != null)
					{
						return xStackFrame;
					}
				}
				catch(VMDisconnectedException e)
				{
					throw e;
				}
				catch(Throwable e)
				{
					LOG.error(e);
				}
			}
		}
		return null;
	}

	@Override
	public ThreeState evaluateCondition(@Nonnull EvaluationContext context, @Nonnull StackFrameProxyImpl frame, @Nonnull Location location, @Nonnull String expression)
	{
		for(PositionManager positionManager : myPositionManagers)
		{
			if(positionManager instanceof PositionManagerEx)
			{
				try
				{
					ThreeState result = ((PositionManagerEx) positionManager).evaluateCondition(context, frame, location, expression);
					if(result != ThreeState.UNSURE)
					{
						return result;
					}
				}
				catch(Throwable e)
				{
					LOG.error(e);
				}
			}
		}
		return ThreeState.UNSURE;
	}
}
