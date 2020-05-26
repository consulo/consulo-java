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
package com.intellij.compiler.impl.javaCompiler;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

import javax.annotation.Nullable;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompileDriver;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.util.TimeoutUtil;
import consulo.logging.Logger;
import consulo.util.dataholder.Key;

/**
 * @author Eugene Zhuravlev
 * Date: Mar 30, 2004
 */
public class CompilerParsingThread implements Runnable, OutputParser.Callback
{
	private static final Logger LOG = Logger.getInstance(CompilerParsingThread.class);
	public static final String TERMINATION_STRING = "__terminate_read__";

	private ProcessHandler myProcessHandler;
	private final OutputParser myOutputParser;
	private final boolean myTrimLines;
	private Throwable myError = null;
	private final boolean myIsUnitTestMode;
	private FileObject myClassFileToProcess = null;
	private String myLastReadLine = null;
	private String myPushBackLine = null;
	private volatile boolean myProcessExited = false;
	private final CompileContext myContext;

	private final Deque<String> myLines = new ConcurrentLinkedDeque<>();

	public CompilerParsingThread(ProcessHandler processHandler, OutputParser outputParser, final boolean readErrorStream, boolean trimLines, CompileContext context)
	{
		myProcessHandler = processHandler;
		myOutputParser = outputParser;
		myTrimLines = trimLines;
		myContext = context;

		myIsUnitTestMode = false;

		processHandler.addProcessListener(new ProcessAdapter()
		{
			@Override
			public void onTextAvailable(ProcessEvent event, Key outputType)
			{
				if(!readErrorStream && outputType == ProcessOutputTypes.STDERR)
				{
					return;
				}

				myLines.add(event.getText().trim());
			}
		});
	}

	volatile boolean processing;

	@Override
	public void run()
	{
		processing = true;
		try
		{
			while(true)
			{
				if(isProcessTerminated())
				{
					break;
				}

				if(!myIsUnitTestMode && myProcessHandler == null)
				{
					break;
				}

				if(isCanceled())
				{
					break;
				}

				if(!myOutputParser.processMessageLine(this))
				{
					break;
				}
			}

			if(myClassFileToProcess != null)
			{
				processCompiledClass(myClassFileToProcess);
				myClassFileToProcess = null;
			}
		}
		catch(Throwable e)
		{
			myError = e;
			LOG.warn(e);
		}
		finally
		{
			killProcess();
			processing = false;
		}
	}

	private void killProcess()
	{
		if(myProcessHandler != null)
		{
			myProcessHandler.destroyProcess();
			myProcessHandler = null;
		}
	}

	public Throwable getError()
	{
		return myError;
	}

	@Override
	public String getCurrentLine()
	{
		return myLastReadLine;
	}

	@Override
	public final String getNextLine()
	{
		final String pushBack = myPushBackLine;
		if(pushBack != null)
		{
			myPushBackLine = null;
			myLastReadLine = pushBack;
			return pushBack;
		}
		final String line = readLine();
		if(LOG.isDebugEnabled())
		{
			LOG.debug("LIne read: #" + line + "#");
		}
		if(CompileDriver.ourDebugMode)
		{
			System.out.println("LIne read: #" + line + "#");
		}
		if(TERMINATION_STRING.equals(line))
		{
			myLastReadLine = null;
		}
		else
		{
			myLastReadLine = line == null ? null : myTrimLines ? line.trim() : line;
		}
		return myLastReadLine;
	}

	@Override
	public void pushBack(String line)
	{
		myLastReadLine = null;
		myPushBackLine = line;
	}

	@Override
	public final void fileGenerated(FileObject path)
	{
		// javac first logs file generated, then starts to write the file to disk,
		// so this thread sometimes can stumble on not yet existing file,
		// hence this complex logic
		FileObject previousPath = myClassFileToProcess;
		myClassFileToProcess = path;
		if(previousPath != null)
		{
			try
			{
				processCompiledClass(previousPath);
			}
			catch(CacheCorruptedException e)
			{
				if(CompileDriver.ourDebugMode)
				{
					e.printStackTrace();
				}
				myError = e;
				LOG.info(e);
				killProcess();
			}
		}
	}

	protected boolean isCanceled()
	{
		return myContext.getProgressIndicator().isCanceled();
	}

	@Override
	public void setProgressText(String text)
	{
		myContext.getProgressIndicator().setText(text);
	}

	@Override
	public void fileProcessed(String path)
	{
	}

	@Override
	public void message(CompilerMessageCategory category, String message, String url, int lineNum, int columnNum)
	{
		myContext.addMessage(category, message, url, lineNum, columnNum);
	}

	protected void processCompiledClass(final FileObject classFileToProcess) throws CacheCorruptedException
	{
	}

	@Nullable
	private String readLine()
	{
		if(isProcessTerminated())
		{
			return null;
		}

		String line = myLines.pollFirst();
		if(line == null)
		{
			while(!isProcessTerminated())
			{
				line = myLines.pollFirst();
				if(line != null)
				{
					return line;
				}

				TimeoutUtil.sleep(100);
			}
		}

		return line;
	}

	private boolean isProcessTerminated()
	{
		return myProcessExited;
	}

	public void setProcessTerminated(final boolean procesExited)
	{
		myProcessExited = procesExited;
	}
}
