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
package com.intellij.java.debugger.impl.ui.impl.watch;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.engine.ContextUtil;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.impl.settings.ThreadsViewSettings;
import com.intellij.java.debugger.impl.ui.tree.StackFrameDescriptor;
import com.intellij.java.debugger.impl.ui.tree.render.DescriptorLabelListener;
import consulo.application.AllIcons;
import consulo.application.ApplicationManager;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.frame.XValueMarkers;
import consulo.execution.debug.ui.ValueMarkup;
import consulo.internal.com.sun.jdi.*;
import consulo.language.editor.FileColorManager;
import consulo.language.psi.PsiFile;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.awt.*;

/**
 * Nodes of this type cannot be updated, because StackFrame objects become invalid as soon as VM has been resumed
 */
public class StackFrameDescriptorImpl extends NodeDescriptorImpl implements StackFrameDescriptor
{
	private final StackFrameProxyImpl myFrame;
	private int myUiIndex;
	private String myName = null;
	private Location myLocation;
	private MethodsTracker.MethodOccurrence myMethodOccurrence;
	private boolean myIsSynthetic;
	private boolean myIsInLibraryContent;
	private ObjectReference myThisObject;
	private Color myBackgroundColor;
	private SourcePosition mySourcePosition;

	private Image myIcon = AllIcons.Debugger.StackFrame;

	public StackFrameDescriptorImpl(@Nonnull StackFrameProxyImpl frame, @Nonnull MethodsTracker tracker)
	{
		myFrame = frame;

		try
		{
			myUiIndex = frame.getFrameIndex();
			myLocation = frame.location();
			try
			{
				myThisObject = frame.thisObject();
			}
			catch(EvaluateException e)
			{
				// catch internal exceptions here
				if(!(e.getCause() instanceof InternalException))
				{
					throw e;
				}
				LOG.info(e);
			}
			myMethodOccurrence = tracker.getMethodOccurrence(myUiIndex, myLocation.method());
			myIsSynthetic = DebuggerUtils.isSynthetic(myMethodOccurrence.getMethod());
			ApplicationManager.getApplication().runReadAction(new Runnable()
			{
				@Override
				public void run()
				{
					mySourcePosition = ContextUtil.getSourcePosition(StackFrameDescriptorImpl.this);
					final PsiFile file = mySourcePosition != null ? mySourcePosition.getFile() : null;
					if(file == null)
					{
						myIsInLibraryContent = true;
					}
					else
					{
						myBackgroundColor = FileColorManager.getInstance(file.getProject()).getFileColor(file);

						final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(StackFrameDescriptorImpl.this.getDebugProcess().getProject()).getFileIndex();
						final VirtualFile vFile = file.getVirtualFile();
						myIsInLibraryContent = vFile != null && (projectFileIndex.isInLibraryClasses(vFile) || projectFileIndex.isInLibrarySource(vFile));
					}
				}
			});
		}
		catch(InternalException e)
		{
			LOG.info(e);
			myLocation = null;
			myMethodOccurrence = tracker.getMethodOccurrence(0, null);
			myIsSynthetic = false;
			myIsInLibraryContent = false;
		}
		catch(EvaluateException e)
		{
			LOG.info(e);
			myLocation = null;
			myMethodOccurrence = tracker.getMethodOccurrence(0, null);
			myIsSynthetic = false;
			myIsInLibraryContent = false;
		}
	}

	public int getUiIndex()
	{
		return myUiIndex;
	}

	@Override
	@Nonnull
	public StackFrameProxyImpl getFrameProxy()
	{
		return myFrame;
	}

	@Nonnull
	@Override
	public DebugProcess getDebugProcess()
	{
		return myFrame.getVirtualMachine().getDebugProcess();
	}

	@Override
	public Color getBackgroundColor()
	{
		return myBackgroundColor;
	}

	@Nullable
	public Method getMethod()
	{
		return myMethodOccurrence.getMethod();
	}

	public int getOccurrenceIndex()
	{
		return myMethodOccurrence.getIndex();
	}

	public boolean isRecursiveCall()
	{
		return myMethodOccurrence.isRecursive();
	}

	@Nullable
	public ValueMarkup getValueMarkup()
	{
		if(myThisObject != null)
		{
			DebugProcess process = myFrame.getVirtualMachine().getDebugProcess();
			if(process instanceof DebugProcessImpl)
			{
				XDebugSession session = ((DebugProcessImpl) process).getSession().getXDebugSession();
				XValueMarkers<?, ?> markers = session == null ? null : session.getValueMarkers();
				if (markers != null)
				{
					return markers.getAllMarkers().get(myThisObject);
				}
			}
		}
		return null;
	}

	@Override
	public String getName()
	{
		return myName;
	}

	@Override
	protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener descriptorLabelListener) throws EvaluateException
	{
		DebuggerManagerThreadImpl.assertIsManagerThread();

		if(myLocation == null)
		{
			return "";
		}
		ThreadsViewSettings settings = ThreadsViewSettings.getInstance();
		final StringBuilder label = new StringBuilder();
		Method method = myMethodOccurrence.getMethod();
		if(method != null)
		{
			myName = method.name();
			label.append(settings.SHOW_ARGUMENTS_TYPES ? DebuggerUtilsEx.methodNameWithArguments(method) : myName);
		}
		if(settings.SHOW_LINE_NUMBER)
		{
			String lineNumber;
			try
			{
				lineNumber = Integer.toString(myLocation.lineNumber());
			}
			catch(InternalError e)
			{
				lineNumber = e.toString();
			}
			if(lineNumber != null)
			{
				label.append(':');
				label.append(lineNumber);
			}
		}
		if(settings.SHOW_CLASS_NAME)
		{
			String name;
			try
			{
				ReferenceType refType = myLocation.declaringType();
				name = refType != null ? refType.name() : null;
			}
			catch(InternalError e)
			{
				name = e.toString();
			}
			if(name != null)
			{
				label.append(", ");
				int dotIndex = name.lastIndexOf('.');
				if(dotIndex < 0)
				{
					label.append(name);
				}
				else
				{
					label.append(name.substring(dotIndex + 1));
					if(settings.SHOW_PACKAGE_NAME)
					{
						label.append(" {");
						label.append(name.substring(0, dotIndex));
						label.append("}");
					}
				}
			}
		}
		if(settings.SHOW_SOURCE_NAME)
		{
			try
			{
				String sourceName;
				try
				{
					sourceName = myLocation.sourceName();
				}
				catch(InternalError e)
				{
					sourceName = e.toString();
				}
				label.append(", ");
				label.append(sourceName);
			}
			catch(AbsentInformationException ignored)
			{
			}
		}
		return label.toString();
	}

	public final boolean stackFramesEqual(StackFrameDescriptorImpl d)
	{
		return getFrameProxy().equals(d.getFrameProxy());
	}

	@Override
	public boolean isExpandable()
	{
		return true;
	}

	@Override
	public final void setContext(EvaluationContextImpl context)
	{
		myIcon = calcIcon();
	}

	public boolean isSynthetic()
	{
		return myIsSynthetic;
	}

	public boolean isInLibraryContent()
	{
		return myIsInLibraryContent;
	}

	@Nullable
	public Location getLocation()
	{
		return myLocation;
	}

	public SourcePosition getSourcePosition()
	{
		return mySourcePosition;
	}

	@Nonnull
	private Image calcIcon()
	{
		try
		{
			if(myFrame.isObsolete())
			{
				return AllIcons.Debugger.Db_obsolete;
			}
		}
		catch(EvaluateException ignored)
		{
		}
		return AllIcons.Debugger.StackFrame;
	}

	public Image getIcon()
	{
		return myIcon;
	}

	public ObjectReference getThisObject()
	{
		return myThisObject;
	}
}
