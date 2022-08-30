// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.jrt;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import consulo.java.execution.projectRoots.OwnJdkUtil;
import consulo.util.collection.Maps;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class JrtFileSystemImpl extends JrtFileSystem
{
	private final Map<String, ArchiveHandler> myHandlers =Collections.synchronizedMap(Maps.newHashMap(FileUtil.PATH_HASHING_STRATEGY));
	private final AtomicBoolean mySubscribed = new AtomicBoolean(false);

	@Nonnull
	@Override
	public String getProtocol()
	{
		return PROTOCOL;
	}

	@Nonnull
	@Override
	protected String normalize(@Nonnull String path)
	{
		int p = path.indexOf(SEPARATOR);
		return p > 0 ? FileUtil.normalize(path.substring(0, p)) + path.substring(p) : super.normalize(path);
	}

	@Nonnull
	@Override
	protected String extractLocalPath(@Nonnull String rootPath)
	{
		return StringUtil.trimEnd(rootPath, SEPARATOR);
	}

	@Nonnull
	@Override
	protected String composeRootPath(@Nonnull String localPath)
	{
		return localPath + SEPARATOR;
	}

	@Nonnull
	@Override
	protected String extractRootPath(@Nonnull String entryPath)
	{
		int separatorIndex = entryPath.indexOf(SEPARATOR);
		assert separatorIndex >= 0 : "Path passed to JrtFileSystem must have a separator '!/' but got: " + entryPath;
		return entryPath.substring(0, separatorIndex + SEPARATOR.length());
	}

	@Nonnull
	@Override
	protected ArchiveHandler getHandler(@Nonnull VirtualFile entryFile)
	{
		checkSubscription();

		String homePath = extractLocalPath(extractRootPath(entryFile.getPath()));
		return myHandlers.computeIfAbsent(homePath, key -> {
			JrtHandler handler = new JrtHandler(key);
			ApplicationManager.getApplication().invokeLater(
					() -> LocalFileSystem.getInstance().refreshAndFindFileByPath(key + "/release"),
					ModalityState.defaultModalityState());
			return handler;
		});
	}

	private void checkSubscription()
	{
		if(mySubscribed.getAndSet(true))
		{
			return;
		}

		Application app = ApplicationManager.getApplication();
		if(app.isDisposeInProgress())
		{
			return;  // we might perform a shutdown activity that includes visiting archives (IDEA-181620)
		}
		app.getMessageBus().connect(app).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener()
		{
			@Override
			public void after(@Nonnull List<? extends VFileEvent> events)
			{
				Set<VirtualFile> toRefresh = null;

				for(VFileEvent e : events)
				{
					if(e.getFileSystem() instanceof LocalFileSystem)
					{
						String homePath = null;

						if(e instanceof VFileContentChangeEvent)
						{
							VirtualFile file = ((VFileContentChangeEvent) e).getFile();
							if("release".equals(file.getName()))
							{
								homePath = file.getParent().getPath();
							}
						}
						else if(e instanceof VFileDeleteEvent)
						{
							homePath = ((VFileDeleteEvent) e).getFile().getPath();
						}

						if(homePath != null)
						{
							ArchiveHandler handler = myHandlers.remove(homePath);
							if(handler != null)
							{
								handler.dispose();
								VirtualFile root = findFileByPath(composeRootPath(homePath));
								if(root != null)
								{
									((NewVirtualFile) root).markDirtyRecursively();
									if(toRefresh == null)
									{
										toRefresh = new HashSet<>();
									}
									toRefresh.add(root);
								}
							}
						}
					}
				}

				if(toRefresh != null)
				{
					boolean async = !ApplicationManager.getApplication().isUnitTestMode();
					RefreshQueue.getInstance().refresh(async, true, null, toRefresh);
				}
			}
		});
	}

	@Override
	public VirtualFile findFileByPath(@Nonnull String path)
	{
		return VfsImplUtil.findFileByPath(this, path);
	}

	@Override
	public VirtualFile findFileByPathIfCached(@Nonnull String path)
	{
		return VfsImplUtil.findFileByPathIfCached(this, path);
	}

	@Override
	public VirtualFile refreshAndFindFileByPath(@Nonnull String path)
	{
		return VfsImplUtil.refreshAndFindFileByPath(this, path);
	}

	@Override
	public void refresh(boolean asynchronous)
	{
		VfsImplUtil.refresh(this, asynchronous);
	}

	@Override
	protected boolean isCorrectFileType(@Nonnull VirtualFile local)
	{
		String path = local.getPath();
		return OwnJdkUtil.isModularRuntime(path) && !OwnJdkUtil.isExplodedModularRuntime(path);
	}
}