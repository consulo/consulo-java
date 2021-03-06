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
package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class HotSwapManager
{
	@Nonnull
	public static HotSwapManager getInstance(@Nonnull Project project)
	{
		return ServiceManager.getService(project, HotSwapManager.class);
	}

	public static class Listener implements DebuggerManagerListener
	{
		@Override
		public void sessionCreated(DebuggerSession session)
		{
			HotSwapManager manager = HotSwapManager.getInstance(session.getProject());
			manager.myTimeStamps.put(session, System.currentTimeMillis());
		}

		@Override
		public void sessionRemoved(DebuggerSession session)
		{
			HotSwapManager manager = HotSwapManager.getInstance(session.getProject());
			manager.myTimeStamps.remove(session);
		}
	}

	private final Map<DebuggerSession, Long> myTimeStamps = new HashMap<>();
	private static final String CLASS_EXTENSION = ".class";

	private long getTimeStamp(DebuggerSession session)
	{
		Long tStamp = myTimeStamps.get(session);
		return tStamp != null ? tStamp : 0;
	}

	void setTimeStamp(DebuggerSession session, long tStamp)
	{
		myTimeStamps.put(session, tStamp);
	}

	private Map<String, HotSwapFile> scanForModifiedClasses(final DebuggerSession session, final HotSwapProgress progress)
	{
		DebuggerManagerThreadImpl.assertIsManagerThread();

		final long timeStamp = getTimeStamp(session);
		final Map<String, HotSwapFile> modifiedClasses = new HashMap<>();

		List<File> outputRoots = ApplicationManager.getApplication().runReadAction((Computable<List<File>>) () -> {
			final List<VirtualFile> allDirs = OrderEnumerator.orderEntries(session.getProject()).withoutSdk().withoutLibraries().getPathsList().getRootDirs();
			return allDirs.stream().map(VfsUtil::virtualToIoFile).collect(Collectors.toList());
		});

		for(File root : outputRoots)
		{
			final String rootPath = FileUtil.toCanonicalPath(root.getPath());
			collectModifiedClasses(root, rootPath, rootPath + "/", modifiedClasses, progress, timeStamp);
		}

		return modifiedClasses;
	}

	private static boolean collectModifiedClasses(File file,
			String path,
			String rootPath,
			Map<String, HotSwapFile> container,
			HotSwapProgress progress,
			long timeStamp)
	{
		if(progress.isCancelled())
		{
			return false;
		}
		final File[] files = file.listFiles();
		if(files != null)
		{
			for(File child : files)
			{
				if(!collectModifiedClasses(child, path + "/" + child.getName(), rootPath, container, progress, timeStamp))
				{
					return false;
				}
			}
		}
		else
		{ // not a dir
			if(SystemInfo.isFileSystemCaseSensitive ? StringUtil.endsWith(path, CLASS_EXTENSION) : StringUtil.endsWithIgnoreCase(path,
					CLASS_EXTENSION))
			{
				if(file.lastModified() > timeStamp)
				{
					progress.setText(DebuggerBundle.message("progress.hotswap.scanning.path", path));
					//noinspection HardCodedStringLiteral
					final String qualifiedName = path.substring(rootPath.length(), path.length() - CLASS_EXTENSION.length()).replace('/', '.');
					container.put(qualifiedName, new HotSwapFile(file));
				}
			}
		}
		return true;
	}

	private void reloadClasses(DebuggerSession session, Map<String, HotSwapFile> classesToReload, HotSwapProgress progress)
	{
		final long newSwapTime = System.currentTimeMillis();
		new ReloadClassesWorker(session, progress).reloadClasses(classesToReload);
		setTimeStamp(session, newSwapTime);
	}

	public static Map<DebuggerSession, Map<String, HotSwapFile>> findModifiedClasses(List<DebuggerSession> sessions,
			Map<String, List<String>> generatedPaths)
	{
		final Map<DebuggerSession, Map<String, HotSwapFile>> result = new java.util.HashMap<>();
		List<Pair<DebuggerSession, Long>> sessionWithStamps = new ArrayList<>();
		for(DebuggerSession session : sessions)
		{
			sessionWithStamps.add(new Pair<>(session, getInstance(session.getProject()).getTimeStamp(session)));
		}
		for(Map.Entry<String, List<String>> entry : generatedPaths.entrySet())
		{
			final File root = new File(entry.getKey());
			for(String relativePath : entry.getValue())
			{
				if(SystemInfo.isFileSystemCaseSensitive ? StringUtil.endsWith(relativePath, CLASS_EXTENSION) : StringUtil.endsWithIgnoreCase
						(relativePath, CLASS_EXTENSION))
				{
					final String qualifiedName = relativePath.substring(0, relativePath.length() - CLASS_EXTENSION.length()).replace('/', '.');
					final HotSwapFile hotswapFile = new HotSwapFile(new File(root, relativePath));
					final long fileStamp = hotswapFile.file.lastModified();

					for(Pair<DebuggerSession, Long> pair : sessionWithStamps)
					{
						final DebuggerSession session = pair.first;
						if(fileStamp > pair.second)
						{
							Map<String, HotSwapFile> container = result.get(session);
							if(container == null)
							{
								container = new java.util.HashMap<>();
								result.put(session, container);
							}
							container.put(qualifiedName, hotswapFile);
						}
					}
				}
			}
		}
		return result;
	}

	public static Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClasses(final List<DebuggerSession> sessions,
			final HotSwapProgress swapProgress)
	{
		final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses = new HashMap<>();

		final MultiProcessCommand scanClassesCommand = new MultiProcessCommand();

		swapProgress.setCancelWorker(scanClassesCommand::cancel);

		for(final DebuggerSession debuggerSession : sessions)
		{
			if(debuggerSession.isAttached())
			{
				scanClassesCommand.addCommand(debuggerSession.getProcess(), new DebuggerCommandImpl()
				{
					@Override
					protected void action() throws Exception
					{
						swapProgress.setDebuggerSession(debuggerSession);
						final Map<String, HotSwapFile> sessionClasses = getInstance(swapProgress.getProject()).scanForModifiedClasses
								(debuggerSession, swapProgress);
						if(!sessionClasses.isEmpty())
						{
							modifiedClasses.put(debuggerSession, sessionClasses);
						}
					}
				});
			}
		}

		swapProgress.setTitle(DebuggerBundle.message("progress.hotswap.scanning.classes"));
		scanClassesCommand.run();

		return swapProgress.isCancelled() ? new HashMap<>() : modifiedClasses;
	}

	public static void reloadModifiedClasses(final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses,
			final HotSwapProgress reloadClassesProgress)
	{
		final MultiProcessCommand reloadClassesCommand = new MultiProcessCommand();

		reloadClassesProgress.setCancelWorker(reloadClassesCommand::cancel);

		for(final DebuggerSession debuggerSession : modifiedClasses.keySet())
		{
			reloadClassesCommand.addCommand(debuggerSession.getProcess(), new DebuggerCommandImpl()
			{
				@Override
				protected void action() throws Exception
				{
					reloadClassesProgress.setDebuggerSession(debuggerSession);
					getInstance(reloadClassesProgress.getProject()).reloadClasses(debuggerSession, modifiedClasses.get(debuggerSession),
							reloadClassesProgress);
				}
			});
		}

		reloadClassesProgress.setTitle(DebuggerBundle.message("progress.hotswap.reloading"));
		reloadClassesCommand.run();
	}
}
