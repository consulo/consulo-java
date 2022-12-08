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
package com.intellij.java.compiler.impl.javaCompiler;

import com.intellij.java.compiler.impl.OutputParser;
import consulo.compiler.CacheCorruptedException;
import consulo.compiler.CompileContext;
import consulo.compiler.CompilerMessageCategory;
import consulo.logging.Logger;
import consulo.process.ProcessHandler;
import consulo.process.ProcessOutputTypes;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.util.dataholder.Key;

import javax.annotation.Nullable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

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

	private final BlockingQueue<String> myLines = new ArrayBlockingQueue<>(50);

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

				myLines.offer(event.getText().trim());
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
			while(!isProcessTerminated())
			{
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
			processing = false;
		}
	}

	private void killProcess()
	{
		if(myProcessHandler != null)
		{
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

		try
		{
			return myLines.take();
		}
		catch(InterruptedException e)
		{
			return null;
		}
	}

	public void stopParsing()
	{
		myLines.offer(TERMINATION_STRING);
		myProcessExited = true;
	}

	private boolean isProcessTerminated()
	{
		return myProcessExited;
	}
}
