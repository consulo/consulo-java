// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl;

import com.intellij.java.language.psi.PsiJavaModule;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.project.DumbService;
import consulo.component.messagebus.MessageBusConnection;
import consulo.component.util.SimpleModificationTracker;
import consulo.disposer.Disposable;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.event.BulkFileListener;
import consulo.virtualFileSystem.event.VFileDeleteEvent;
import consulo.virtualFileSystem.event.VFileEvent;
import consulo.virtualFileSystem.event.VFilePropertyChangeEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public final class PsiJavaModuleModificationTracker extends SimpleModificationTracker implements Disposable {
  private final Project myProject;

  public static PsiJavaModuleModificationTracker getInstance(Project project) {
    return project.getInstance(PsiJavaModuleModificationTracker.class);
  }

  @Inject
  public PsiJavaModuleModificationTracker(Project project) {
    myProject = project;
    MessageBusConnection connect = project.getMessageBus().connect(this);
    connect.subscribe(BulkFileListener.class, new BulkFileListener() {
      @Override
      public void after(List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          VirtualFile file = event.getFile();
          if (file != null) {
            if (!file.isDirectory() && isModuleFile(file.getName()) ||
                event instanceof VFileDeleteEvent || //ensure inc when directory with MANIFEST.MF was deleted
                event instanceof VFilePropertyChangeEvent &&
                //ensure inc when directory with MANIFEST.MF was renamed or manifest was renamed to a new name
                VirtualFile.PROP_NAME.equals(((VFilePropertyChangeEvent)event).getPropertyName())) {
              incModificationCount();
              break;
            }
          }
        }
      }
    });
  }

  @Override
  public long getModificationCount() {
    return super.getModificationCount() + DumbService.getInstance(myProject).getModificationTracker().getModificationCount();
  }

  static boolean isModuleFile(String name) {
    return PsiJavaModule.MODULE_INFO_FILE.equals(name) || "MANIFEST.MF".equalsIgnoreCase(name);
  }

  @Override
  public void dispose() { }
}
