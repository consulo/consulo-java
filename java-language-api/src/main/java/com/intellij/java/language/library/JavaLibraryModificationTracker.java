// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.library;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.util.ModificationTracker;
import consulo.component.util.SimpleModificationTracker;
import consulo.content.scope.SearchScope;
import consulo.disposer.Disposable;
import consulo.document.event.FileDocumentManagerListener;
import consulo.logging.Logger;
import consulo.module.content.ProjectRootManager;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Use this modification tracker for {@link consulo.application.util.CachedValue} which may contain {@link consulo.language.psi.PsiElement}
 * instances from libraries only. If a cache value contains only simple non-PsiElement values, e.g. String, List of primitives,
 * Map of primitive values, etc., then you can use {@link ProjectRootManager} as a cache dependency.
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public final class JavaLibraryModificationTracker implements ModificationTracker, Disposable {
  private static final Logger LOG = Logger.getInstance(JavaLibraryModificationTracker.class);

  private final ModificationTracker myProjectRootManager;
  private final ModificationTracker myDumbServiceModificationTracker;
  private final SimpleModificationTracker myOnContentReloadModificationTracker = new SimpleModificationTracker();
  private final Throwable creationStack = new Throwable();

  @Inject
  public JavaLibraryModificationTracker(Project project) {
    myProjectRootManager = ProjectRootManager.getInstance(project);
    myDumbServiceModificationTracker = DumbService.getInstance(project).getModificationTracker();

    project.getMessageBus().connect(this).subscribe(FileDocumentManagerListener.class, new FileDocumentManagerListener() {
      private final SearchScope projectLibraryScope = ProjectScopes.getLibrariesScope(project);

      @Override
      public void fileWithNoDocumentChanged(VirtualFile file) {
        if (!project.isInitialized()) {
          LOG.warn("SearchScope.contains(file) would log an error because the project is not yet initialized. " +
                   "Probably JavaLibraryModificationTracker was created too early. " +
                   "See JavaLibraryModificationTracker creation stacktrace: ", creationStack);
          return;
        }
        if (projectLibraryScope.contains(file)) {
          myOnContentReloadModificationTracker.incModificationCount();
        }
      }
    });
  }

  @Override
  public long getModificationCount() {
    return myProjectRootManager.getModificationCount()
           + myDumbServiceModificationTracker.getModificationCount()
           + myOnContentReloadModificationTracker.getModificationCount();
  }

  public void incModificationCount() {
    myOnContentReloadModificationTracker.incModificationCount();
  }

  @Override
  public void dispose() {
  }

  public static ModificationTracker getInstance(Project project) {
    return project.getInstance(JavaLibraryModificationTracker.class);
  }

  public static void incModificationCount(Project project) {
    project.getInstance(JavaLibraryModificationTracker.class).incModificationCount();
  }
}
