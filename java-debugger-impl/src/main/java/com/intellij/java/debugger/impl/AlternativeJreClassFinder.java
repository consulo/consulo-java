/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.java.execution.configurations.ConfigurationWithAlternativeJre;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.java.debugger.DebuggerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTable;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.java.language.impl.psi.NonClasspathClassFinder;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.NonClasspathDirectoriesScope;
import com.intellij.util.containers.ContainerUtil;
import jakarta.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author egor
 */
public class AlternativeJreClassFinder extends NonClasspathClassFinder {
  @Inject
  public AlternativeJreClassFinder(Project project, DebuggerManager manager) {
    super(project);
    ((DebuggerManagerEx) manager).addDebuggerManagerListener(new DebuggerManagerListener() {
      @Override
      public void sessionCreated(DebuggerSession session) {
        clearCache();
      }

      @Override
      public void sessionRemoved(DebuggerSession session) {
        clearCache();
      }
    });
  }

  @Override
  protected List<VirtualFile> calcClassRoots() {
    Collection<DebuggerSession> sessions = DebuggerManagerEx.getInstanceEx(myProject).getSessions();
    if (sessions.isEmpty()) {
      return Collections.emptyList();
    }
    List<VirtualFile> res = ContainerUtil.newSmartList();
    for (DebuggerSession session : sessions) {
      Sdk jre = session.getAlternativeJre();
      if (jre != null) {
        res.addAll(getClassRoots(jre));
      }
    }
    return res;
  }

  @Nullable
  public static Sdk getAlternativeJre(RunProfile profile) {
    if (profile instanceof ConfigurationWithAlternativeJre) {
      ConfigurationWithAlternativeJre appConfig = (ConfigurationWithAlternativeJre) profile;
      if (appConfig.isAlternativeJrePathEnabled()) {
        return SdkTable.getInstance().findSdk(appConfig.getAlternativeJrePath());
      }
    }
    return null;
  }

  @Nonnull
  private static Collection<VirtualFile> getClassRoots(@Nonnull Sdk jre) {
    return Arrays.asList(jre.getRootProvider().getFiles(OrderRootType.CLASSES));
  }

  @Nonnull
  public static Collection<VirtualFile> getSourceRoots(@Nonnull Sdk jre) {
    return Arrays.asList(jre.getRootProvider().getFiles(OrderRootType.SOURCES));
  }

  @Nonnull
  public static GlobalSearchScope getSearchScope(@Nonnull Sdk jre) {
    return new NonClasspathDirectoriesScope(getClassRoots(jre));
  }
}
