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
package com.intellij.java.impl.ide.macro;

import consulo.annotation.component.ExtensionImpl;
import consulo.ide.IdeBundle;
import consulo.pathMacro.Macro;
import consulo.dataContext.DataContext;
import consulo.platform.base.localize.IdeLocalize;
import consulo.project.Project;
import consulo.module.content.layer.OrderEnumerator;

@ExtensionImpl
public final class ClasspathMacro extends Macro {
  public String getName() {
    return "Classpath";
  }

  public String getDescription() {
    return IdeLocalize.macroProjectClasspath().get();
  }

  public String expand(DataContext dataContext) {
    Project project = dataContext.getData(Project.KEY);
    if (project == null) return null;
    return OrderEnumerator.orderEntries(project).getPathsList().getPathsString();
  }
}
