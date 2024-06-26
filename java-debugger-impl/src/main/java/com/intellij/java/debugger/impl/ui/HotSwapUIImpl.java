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
package com.intellij.java.debugger.impl.ui;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.HotSwapFile;
import com.intellij.java.debugger.impl.HotSwapManager;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.compiler.CompilerManager;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationType;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.collection.Lists;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

/**
 * User: lex
 * Date: Oct 2, 2003
 * Time: 6:00:55 PM
 */
@Singleton
@ServiceImpl
public class HotSwapUIImpl extends HotSwapUI {

  protected final List<HotSwapVetoableListener> myListeners = Lists.newLockFreeCopyOnWriteList();
  private boolean myAskBeforeHotswap = true;
  private final Project myProject;
  protected boolean myPerformHotswapAfterThisCompilation = true;

  @Inject
  public HotSwapUIImpl(final Project project) {
    myProject = project;
  }

  @Override
  public void addListener(HotSwapVetoableListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(HotSwapVetoableListener listener) {
    myListeners.remove(listener);
  }

  private boolean shouldDisplayHangWarning(DebuggerSettings settings, List<DebuggerSession> sessions) {
    if (!settings.HOTSWAP_HANG_WARNING_ENABLED) {
      return false;
    }
    // todo: return false if yourkit agent is inactive
    for (DebuggerSession session : sessions) {
      if (session.isPaused()) {
        return true;
      }
    }
    return false;
  }

  protected void hotSwapSessions(final List<DebuggerSession> sessions, @Nullable final Map<String, List<String>> generatedPaths) {
    final boolean shouldAskBeforeHotswap = myAskBeforeHotswap;
    myAskBeforeHotswap = true;

    final DebuggerSettings settings = DebuggerSettings.getInstance();
    final String runHotswap = settings.RUN_HOTSWAP_AFTER_COMPILE;
    final boolean shouldDisplayHangWarning = shouldDisplayHangWarning(settings, sessions);

    if (shouldAskBeforeHotswap && DebuggerSettings.RUN_HOTSWAP_NEVER.equals(runHotswap)) {
      return;
    }

    final boolean shouldPerformScan = true;

    final HotSwapProgressImpl findClassesProgress;
    if (shouldPerformScan) {
      findClassesProgress = new HotSwapProgressImpl(myProject);
    }
    else {
      boolean createProgress = false;
      for (DebuggerSession session : sessions) {
        if (session.isModifiedClassesScanRequired()) {
          createProgress = true;
          break;
        }
      }
      findClassesProgress = createProgress ? new HotSwapProgressImpl(myProject) : null;
    }

    final Application application = myProject.getApplication();
    application.executeOnPooledThread(() -> {
      final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses;
      if (shouldPerformScan) {
        modifiedClasses = scanForModifiedClassesWithProgress(sessions, findClassesProgress);
      }
      else {
        final List<DebuggerSession> toScan = new ArrayList<>();
        final List<DebuggerSession> toUseGenerated = new ArrayList<>();
        for (DebuggerSession session : sessions) {
          (session.isModifiedClassesScanRequired() ? toScan : toUseGenerated).add(session);
          session.setModifiedClassesScanRequired(false);
        }
        modifiedClasses = new HashMap<>();
        if (!toUseGenerated.isEmpty()) {
          modifiedClasses.putAll(HotSwapManager.findModifiedClasses(toUseGenerated, generatedPaths));
        }
        if (!toScan.isEmpty()) {
          modifiedClasses.putAll(scanForModifiedClassesWithProgress(toScan, findClassesProgress));
        }
      }

      if (modifiedClasses.isEmpty()) {
        final String message = DebuggerBundle.message("status.hotswap.uptodate");
        HotSwapProgressImpl.NOTIFICATION_GROUP.createNotification(message, NotificationType.INFORMATION).notify(myProject);
        return;
      }

      application.invokeLater(() -> {
        if (shouldAskBeforeHotswap && !DebuggerSettings.RUN_HOTSWAP_ALWAYS.equals(runHotswap)) {
          final RunHotswapDialog dialog = new RunHotswapDialog(myProject, sessions, shouldDisplayHangWarning);
          dialog.show();
          if (!dialog.isOK()) {
            for (DebuggerSession session : modifiedClasses.keySet()) {
              session.setModifiedClassesScanRequired(true);
            }
            return;
          }
          final Set<DebuggerSession> toReload = new HashSet<>(dialog.getSessionsToReload());
          for (DebuggerSession session : modifiedClasses.keySet()) {
            if (!toReload.contains(session)) {
              session.setModifiedClassesScanRequired(true);
            }
          }
          modifiedClasses.keySet().retainAll(toReload);
        }
        else {
          if (shouldDisplayHangWarning) {
            final int answer = Messages.showCheckboxMessageDialog(
              DebuggerBundle.message("hotswap.dialog.hang.warning"),
              DebuggerBundle.message("hotswap.dialog.title"),
              new String[]{
                "Perform &Reload Classes",
                "&Skip Reload Classes"
              },
              CommonLocalize.dialogOptionsDoNotShow().get(),
              false,
              1,
              1,
              UIUtil.getWarningIcon(),
              (exitCode, cb) -> {
                settings.HOTSWAP_HANG_WARNING_ENABLED = !cb.isSelected();
                return exitCode == DialogWrapper.OK_EXIT_CODE ? exitCode : DialogWrapper.CANCEL_EXIT_CODE;
              }
            );
            if (answer == DialogWrapper.CANCEL_EXIT_CODE) {
              for (DebuggerSession session : modifiedClasses.keySet()) {
                session.setModifiedClassesScanRequired(true);
              }
              return;
            }
          }
        }

        if (!modifiedClasses.isEmpty()) {
          final HotSwapProgressImpl progress = new HotSwapProgressImpl(myProject);
          application.executeOnPooledThread(() -> reloadModifiedClasses(modifiedClasses, progress));
        }
      }, application.getNoneModalityState());
    });
  }

  private static Map<DebuggerSession, Map<String, HotSwapFile>> scanForModifiedClassesWithProgress(
    final List<DebuggerSession> sessions,
    final HotSwapProgressImpl progress
  ) {
    final Ref<Map<DebuggerSession, Map<String, HotSwapFile>>> result = Ref.create(null);
    ProgressManager.getInstance().runProcess(() -> {
      try {
        result.set(HotSwapManager.scanForModifiedClasses(sessions, progress));
      }
      finally {
        progress.finished();
      }
    }, progress.getProgressIndicator());
    return result.get();
  }

  private static void reloadModifiedClasses(final Map<DebuggerSession, Map<String, HotSwapFile>> modifiedClasses,
                                            final HotSwapProgressImpl progress) {
    ProgressManager.getInstance().runProcess(() -> {
      HotSwapManager.reloadModifiedClasses(modifiedClasses, progress);
      progress.finished();
    }, progress.getProgressIndicator());
  }

  @Override
  public void reloadChangedClasses(final DebuggerSession session, boolean compileBeforeHotswap) {
    dontAskHotswapAfterThisCompilation();
    if (compileBeforeHotswap) {
      CompilerManager.getInstance(session.getProject()).make(null);
    }
    else {
      if (session.isAttached()) {
        hotSwapSessions(Collections.singletonList(session), null);
      }
    }
  }

  @Override
  public void dontPerformHotswapAfterThisCompilation() {
    myPerformHotswapAfterThisCompilation = false;
  }

  public void dontAskHotswapAfterThisCompilation() {
    myAskBeforeHotswap = false;
  }
}
