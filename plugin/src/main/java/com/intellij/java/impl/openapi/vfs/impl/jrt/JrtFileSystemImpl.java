// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.openapi.vfs.impl.jrt;

import com.intellij.java.language.vfs.jrt.JrtFileSystem;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.java.execution.projectRoots.OwnJdkUtil;
import consulo.util.collection.Maps;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.NewVirtualFile;
import consulo.virtualFileSystem.RefreshQueue;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveHandler;
import consulo.virtualFileSystem.archive.BaseArchiveFileSystem;
import consulo.virtualFileSystem.event.BulkFileListener;
import consulo.virtualFileSystem.event.VFileContentChangeEvent;
import consulo.virtualFileSystem.event.VFileDeleteEvent;
import consulo.virtualFileSystem.event.VFileEvent;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@ExtensionImpl
public class JrtFileSystemImpl extends BaseArchiveFileSystem implements JrtFileSystem {
  private final Map<String, ArchiveHandler> myHandlers = Collections.synchronizedMap(Maps.newHashMap(FileUtil.PATH_HASHING_STRATEGY));
  private final AtomicBoolean mySubscribed = new AtomicBoolean(false);

  @Nonnull
  @Override
  public String getProtocol() {
    return PROTOCOL;
  }

  @Nonnull
  @Override
  public String normalize(@Nonnull String path) {
    int p = path.indexOf(SEPARATOR);
    return p > 0 ? FileUtil.normalize(path.substring(0, p)) + path.substring(p) : super.normalize(path);
  }

  @Nonnull
  @Override
  public String extractLocalPath(@Nonnull String rootPath) {
    return StringUtil.trimEnd(rootPath, SEPARATOR);
  }

  @Nonnull
  @Override
  public String composeRootPath(@Nonnull String localPath) {
    return localPath + SEPARATOR;
  }

  @Nonnull
  @Override
  public String extractRootPath(@Nonnull String entryPath) {
    int separatorIndex = entryPath.indexOf(SEPARATOR);
    assert separatorIndex >= 0 : "Path passed to JrtFileSystem must have a separator '!/' but got: " + entryPath;
    return entryPath.substring(0, separatorIndex + SEPARATOR.length());
  }

  @Nonnull
  @Override
  public ArchiveHandler getHandler(@Nonnull VirtualFile entryFile) {
    checkSubscription();

    String homePath = extractLocalPath(extractRootPath(entryFile.getPath()));
    return myHandlers.computeIfAbsent(homePath, key -> {
      JrtHandler handler = new JrtHandler(key);
      ApplicationManager.getApplication().invokeLater(
          () -> LocalFileSystem.getInstance().refreshAndFindFileByPath(key + "/release"),
          Application.get().getDefaultModalityState());
      return handler;
    });
  }

  private void checkSubscription() {
    if (mySubscribed.getAndSet(true)) {
      return;
    }

    Application app = ApplicationManager.getApplication();
    if (app.isDisposeInProgress()) {
      return;  // we might perform a shutdown activity that includes visiting archives (IDEA-181620)
    }
    app.getMessageBus().connect(app).subscribe(BulkFileListener.class, new BulkFileListener() {
      @Override
      public void after(@Nonnull List<? extends VFileEvent> events) {
        Set<VirtualFile> toRefresh = null;

        for (VFileEvent e : events) {
          if (e.getFileSystem() instanceof LocalFileSystem) {
            String homePath = null;

            if (e instanceof VFileContentChangeEvent) {
              VirtualFile file = e.getFile();
              if ("release".equals(file.getName())) {
                homePath = file.getParent().getPath();
              }
            } else if (e instanceof VFileDeleteEvent) {
              homePath = e.getFile().getPath();
            }

            if (homePath != null) {
              ArchiveHandler handler = myHandlers.remove(homePath);
              if (handler != null) {
                handler.dispose();
                VirtualFile root = findFileByPath(composeRootPath(homePath));
                if (root != null) {
                  ((NewVirtualFile) root).markDirtyRecursively();
                  if (toRefresh == null) {
                    toRefresh = new HashSet<>();
                  }
                  toRefresh.add(root);
                }
              }
            }
          }
        }

        if (toRefresh != null) {
          boolean async = !ApplicationManager.getApplication().isUnitTestMode();
          RefreshQueue.getInstance().refresh(async, true, null, toRefresh);
        }
      }
    });
  }

  @Override
  protected boolean isCorrectFileType(@Nonnull VirtualFile local) {
    String path = local.getPath();
    return OwnJdkUtil.isModularRuntime(path) && !OwnJdkUtil.isExplodedModularRuntime(path);
  }

  @Nullable
  @Override
  public VirtualFile getLocalVirtualFileFor(@Nullable VirtualFile virtualFile) {
    return getLocalByEntry(virtualFile);
  }

  @Nullable
  @Override
  public VirtualFile findLocalVirtualFileByPath(@Nonnull String s) {
    return findLocalByRootPath(s);
  }

  @Override
  public void setNoCopyJarForPath(String s) {

  }

  @Override
  public boolean isMakeCopyOfJar(File file) {
    return false;
  }
}