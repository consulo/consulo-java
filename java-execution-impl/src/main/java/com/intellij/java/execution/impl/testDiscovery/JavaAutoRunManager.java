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
package com.intellij.java.execution.impl.testDiscovery;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.compiler.CompileContext;
import consulo.compiler.event.CompilationStatusListener;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.execution.test.autotest.AbstractAutoTestManager;
import consulo.execution.test.autotest.AutoTestWatcher;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;

@Singleton
@State(name = "JavaAutoRunManager", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class JavaAutoRunManager extends AbstractAutoTestManager {
  @Nonnull
  public static JavaAutoRunManager getInstance(Project project) {
    return ServiceManager.getService(project, JavaAutoRunManager.class);
  }

  @Inject
  public JavaAutoRunManager(@Nonnull Project project) {
    super(project);
  }

  @Nonnull
  @Override
  protected AutoTestWatcher createWatcher(Project project) {
    return new AutoTestWatcher() {
      private boolean myHasErrors = false;
      private Disposable myEventDisposable;

      @Override
      public void activate() {
        if (myEventDisposable != null) {
          return;
        }

        myEventDisposable = Disposable.newDisposable();
        Disposer.register(project, myEventDisposable);
        project.getMessageBus().connect(myEventDisposable).subscribe(CompilationStatusListener.class, new CompilationStatusListener() {
          private boolean myFoundFilesToMake = false;

          @Override
          public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
            if (!myFoundFilesToMake) {
              return;
            }
            if (errors == 0) {
              restartAllAutoTests(0);
            }
            myHasErrors = errors == 0;
            myFoundFilesToMake = false;
          }

          //@Override
          public void automakeCompilationFinished(int errors, int warnings, CompileContext compileContext) {
            compilationFinished(false, errors, warnings, compileContext);
          }

          //@Override
          public void fileGenerated(String outputRoot, String relativePath) {
            myFoundFilesToMake = true;
          }
        });
      }

      @Override
      public void deactivate() {
        Disposable eventDisposable = myEventDisposable;
        if (eventDisposable != null) {
          myEventDisposable = null;
          Disposer.dispose(eventDisposable);
        }
      }

      @Override
      public boolean isUpToDate(int modificationStamp) {
        return !myHasErrors;
      }
    };
  }
}
