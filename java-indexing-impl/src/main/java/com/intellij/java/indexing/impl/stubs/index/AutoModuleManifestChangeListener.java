// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.indexing.impl.stubs.index;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicImpl;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.event.BulkFileListener;
import consulo.virtualFileSystem.event.VFileCreateEvent;
import consulo.virtualFileSystem.event.VFileDeleteEvent;
import consulo.virtualFileSystem.event.VFileEvent;

import java.util.List;

/**
 * Re-indexes the JAR root directory in {@link JavaAutoModuleNameIndex} when a MANIFEST.MF is
 * created or deleted inside that JAR, so the two-index split stays consistent.
 */
@TopicImpl(ComponentScope.APPLICATION)
public final class AutoModuleManifestChangeListener implements BulkFileListener {
  @Override
  public void after(List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      if (!(event instanceof VFileCreateEvent) && !(event instanceof VFileDeleteEvent)) continue;
      VirtualFile file = event.getFile();
      if (file == null || !JavaSourceModuleNameIndex.MANIFEST_FILE_NAME.equalsIgnoreCase(file.getName())) continue;
      VirtualFile metaInf = file.getParent();
      if (metaInf == null || !JavaSourceModuleNameIndex.META_INF_DIR_NAME.equalsIgnoreCase(metaInf.getName())) continue;
      VirtualFile jarRoot = metaInf.getParent();
      if (jarRoot != null && jarRoot.getParent() == null && "jar".equalsIgnoreCase(jarRoot.getExtension())) {
        FileBasedIndex.getInstance().requestRebuild(JavaAutoModuleNameIndex.NAME);
        return;
      }
    }
  }
}
