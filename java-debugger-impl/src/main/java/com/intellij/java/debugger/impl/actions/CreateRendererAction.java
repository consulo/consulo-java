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
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.engine.events.DebuggerContextCommandImpl;
import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import com.intellij.java.debugger.impl.settings.UserRenderersConfigurable;
import com.intellij.java.debugger.impl.ui.tree.render.NodeRenderer;
import consulo.application.Application;
import consulo.configurable.IdeaConfigurableBase;
import consulo.ide.impl.idea.openapi.options.ex.SingleConfigurableEditor;
import consulo.internal.com.sun.jdi.Type;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;
import java.util.List;

public class CreateRendererAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    final List<JavaValue> values = ViewAsGroup.getSelectedValues(e);
    if (values.size() != 1) {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  public void actionPerformed(@Nonnull final AnActionEvent event) {
    final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(event.getDataContext());
    final List<JavaValue> values = ViewAsGroup.getSelectedValues(event);
    if (values.size() != 1) {
      return;
    }

    final JavaValue javaValue = values.get(0);

    final DebugProcessImpl process = debuggerContext.getDebugProcess();
    if (process == null) {
      return;
    }

    final Project project = event.getData(Project.KEY);

    process.getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
      public void threadAction() {
        Type type = javaValue.getDescriptor().getType();
        final String name = type != null ? type.name() : null;
        Application.get().invokeLater(() ->
        {
          final UserRenderersConfigurable ui = new UserRenderersConfigurable();
          IdeaConfigurableBase<UserRenderersConfigurable, NodeRendererSettings> configurable = new IdeaConfigurableBase<UserRenderersConfigurable, NodeRendererSettings>("reference.idesettings" +
              ".debugger.typerenderers", DebuggerBundle.message("user.renderers.configurable.display.name"), "reference.idesettings.debugger.typerenderers") {
            @Nonnull
            @Override
            protected NodeRendererSettings getSettings() {
              return NodeRendererSettings.getInstance();
            }

            @Override
            protected UserRenderersConfigurable createUi() {
              return ui;
            }
          };
          SingleConfigurableEditor editor = new SingleConfigurableEditor(project, configurable);
          if (name != null) {
            NodeRenderer renderer = NodeRendererSettings.getInstance().createCompoundTypeRenderer(StringUtil.getShortName(name), name, null, null);
            renderer.setEnabled(true);
            ui.addRenderer(renderer);
          }
          editor.show();
        });
      }
    });
  }
}
