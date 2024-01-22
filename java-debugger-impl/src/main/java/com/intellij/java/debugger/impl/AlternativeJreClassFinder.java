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

import com.intellij.java.debugger.DebuggerManager;
import com.intellij.java.execution.configurations.ConfigurationWithAlternativeJre;
import com.intellij.java.language.impl.psi.NonClasspathClassFinder;
import com.intellij.java.language.impl.psi.NonClasspathDirectoriesScope;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.execution.configuration.RunProfile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * @author egor
 */
@ExtensionImpl(order = "last")
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
    List<VirtualFile> res = new ArrayList<>();
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
    return Arrays.asList(jre.getRootProvider().getFiles(BinariesOrderRootType.getInstance()));
  }

  @Nonnull
  public static Collection<VirtualFile> getSourceRoots(@Nonnull Sdk jre) {
    return Arrays.asList(jre.getRootProvider().getFiles(SourcesOrderRootType.getInstance()));
  }

  @Nonnull
  public static GlobalSearchScope getSearchScope(@Nonnull Sdk jre) {
    return new NonClasspathDirectoriesScope(getClassRoots(jre));
  }
}
