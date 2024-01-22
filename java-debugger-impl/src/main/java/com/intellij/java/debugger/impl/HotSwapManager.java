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
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.ApplicationManager;
import consulo.application.util.SystemInfo;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.logging.Logger;
import consulo.module.content.layer.OrderEnumerator;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class HotSwapManager {
  private static final Logger LOG = Logger.getInstance(HotSwapManager.class);

  @Nonnull
  public static HotSwapManager getInstance(@Nonnull Project project) {
    return project.getInstance(HotSwapManager.class);
  }

  private final Map<DebuggerSession, Long> myTimeStamps = new HashMap<>();
  private static final String CLASS_EXTENSION = ".class";

  private long getTimeStamp(DebuggerSession session) {
    Long tStamp = myTimeStamps.get(session);
    return tStamp != null ? tStamp : 0;
  }

  void setTimeStamp(DebuggerSession session, long tStamp) {
    myTimeStamps.put(session, tStamp);
  }

  Map<DebuggerSession, Long> getTimeStamps() {
    return myTimeStamps;
  }

  private Map<String, HotSwapFile> scanForModifiedClasses(final DebuggerSession session, final HotSwapProgress progress) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    final long timeStamp = getTimeStamp(session);
    final Map<String, HotSwapFile> modifiedClasses = new HashMap<>();

    List<File> outputRoots = ApplicationManager.getApplication().runReadAction((Supplier<List<File>>)() -> {
      final List<VirtualFile> allDirs =
        OrderEnumerator.orderEntries(session.getProject()).withoutSdk().withoutLibraries().getPathsList().getRootDirs();
      return allDirs.stream().map(VirtualFileUtil::virtualToIoFile).collect(Collectors.toList());
    });

    for (File root : outputRoots) {
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
                                                long timeStamp) {
    if (progress.isCancelled()) {
      return false;
    }
    final File[] files = file.listFiles();
    if (files != null) {
      for (File child : files) {
        if (!collectModifiedClasses(child, path + "/" + child.getName(), rootPath, container, progress, timeStamp)) {
          return false;
        }
      }
    }
    else { // not a dir
      if (SystemInfo.isFileSystemCaseSensitive ? StringUtil.endsWith(path, CLASS_EXTENSION) : StringUtil.endsWithIgnoreCase(path,
                                                                                                                            CLASS_EXTENSION)) {
        if (file.lastModified() > timeStamp) {
          progress.setText(DebuggerBundle.message("progress.hotswap.scanning.path", path));
          //noinspection HardCodedStringLiteral
          final String qualifiedName = path.substring(rootPath.length(), path.length() - CLASS_EXTENSION.length()).replace('/', '.');
          container.put(qualifiedName, new HotSwapFile(file.toPath()));
        }
      }
    }
    return true;
  }

  private void reloadClasses(DebuggerSession session, Map<String, HotSwapFile> classesToReload, HotSwapProgress progress) {
    final long newSwapTime = System.currentTimeMillis();
    new ReloadClassesWorker(session, progress).reloadClasses(classesToReload);
    setTimeStamp(session, newSwapTime);
  }

  public static Map<DebuggerSession, Map<String, HotSwapFile>> findModifiedClasses(List<DebuggerSession> sessions,
                                                                                   Map<String, List<String>> generatedPaths) {
    final Map<DebuggerSession, Map<String, HotSwapFile>> result = new HashMap<>();
    List<Pair<DebuggerSession, Long>> sessionWithStamps = new ArrayList<>();
    for (DebuggerSession session : sessions) {
      sessionWithStamps.add(new Pair<>(session, getInstance(session.getProject()).getTimeStamp(session)));
    }
    for (Map.Entry<String, List<String>> entry : generatedPaths.entrySet()) {
      final Path root = Path.of(entry.getKey());
      for (String relativePath : entry.getValue()) {
        if (SystemInfo.isFileSystemCaseSensitive ? StringUtil.endsWith(relativePath, CLASS_EXTENSION) : StringUtil.endsWithIgnoreCase
          (relativePath, CLASS_EXTENSION)) {
          final String qualifiedName = relativePath.substring(0, relativePath.length() - CLASS_EXTENSION.length()).replace('/', '.');
          final HotSwapFile hotswapFile = new HotSwapFile(root.resolve(relativePath));
          try {
            final FileTime fileStamp = Files.getLastModifiedTime(hotswapFile.file);

            for (Pair<DebuggerSession, Long> pair : sessionWithStamps) {
              final DebuggerSession session = pair.first;
              if (fileStamp.toMillis() > pair.second) {
                Map<String, HotSwapFile> container = result.get(session);
                if (container == null) {
                  container = new HashMap<>();
                  result.put(session, container);
                }
                container.put(qualifiedName, hotswapFile);
              }
            }
          }
          catch (IOException e) {
            LOG.error(hotswapFile.file.toString(), e);
          }
        }
      }
    }
    return result;
  }

  public static Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClasses(final List<DebuggerSession> sessions,
                                                                                      final HotSwapProgress swapProgress) {
    final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses = new HashMap<>();

    final MultiProcessCommand scanClassesCommand = new MultiProcessCommand();

    swapProgress.setCancelWorker(scanClassesCommand::cancel);

    for (final DebuggerSession debuggerSession : sessions) {
      if (debuggerSession.isAttached()) {
        scanClassesCommand.addCommand(debuggerSession.getProcess(), new DebuggerCommandImpl() {
          @Override
          protected void action() throws Exception {
            swapProgress.setDebuggerSession(debuggerSession);
            final Map<String, HotSwapFile> sessionClasses = getInstance(swapProgress.getProject()).scanForModifiedClasses
              (debuggerSession, swapProgress);
            if (!sessionClasses.isEmpty()) {
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
                                           final HotSwapProgress reloadClassesProgress) {
    final MultiProcessCommand reloadClassesCommand = new MultiProcessCommand();

    reloadClassesProgress.setCancelWorker(reloadClassesCommand::cancel);

    for (final DebuggerSession debuggerSession : modifiedClasses.keySet()) {
      reloadClassesCommand.addCommand(debuggerSession.getProcess(), new DebuggerCommandImpl() {
        @Override
        protected void action() throws Exception {
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
