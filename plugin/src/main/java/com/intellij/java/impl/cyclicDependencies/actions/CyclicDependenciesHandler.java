/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.cyclicDependencies.actions;

import consulo.language.editor.scope.AnalysisScope;
import consulo.language.editor.scope.AnalysisScopeBundle;
import consulo.ide.impl.idea.analysis.PerformAnalysisInBackgroundOption;
import com.intellij.java.impl.cyclicDependencies.CyclicDependenciesBuilder;
import com.intellij.java.impl.cyclicDependencies.ui.CyclicDependenciesPanel;
import consulo.application.progress.ProgressManager;
import consulo.project.Project;
import consulo.ide.impl.idea.packageDependencies.DependenciesToolWindow;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentFactory;

import javax.swing.*;

/**
 * User: anna
 * Date: Jan 31, 2005
 */
public class CyclicDependenciesHandler {
  private final Project myProject;
  private final AnalysisScope myScope;

  public CyclicDependenciesHandler(Project project, AnalysisScope scope) {
    myProject = project;
    myScope = scope;
  }

  public void analyze() {
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myProject, myScope);
    final Runnable process = new Runnable() {
      public void run() {
        builder.analyze();
      }
    };
    final Runnable successRunnable = new Runnable() {
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            CyclicDependenciesPanel panel = new CyclicDependenciesPanel(myProject, builder);
            Content content = ContentFactory.SERVICE.getInstance().createContent(panel, AnalysisScopeBundle.message(
              "action.analyzing.cyclic.dependencies.in.scope", builder.getScope().getDisplayName()), false);
            content.setDisposer(panel);
            panel.setContent(content);
            DependenciesToolWindow.getInstance(myProject).addContent(content);
          }
        });
      }
    };
    ProgressManager.getInstance()
      .runProcessWithProgressAsynchronously(myProject, AnalysisScopeBundle.message("package.dependencies.progress.title"),
                                            process, successRunnable, null, new PerformAnalysisInBackgroundOption(myProject));
  }
}
