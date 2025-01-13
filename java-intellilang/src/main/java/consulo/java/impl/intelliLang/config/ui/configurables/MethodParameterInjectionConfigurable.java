/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.java.impl.intelliLang.config.ui.configurables;

import consulo.java.impl.intelliLang.config.MethodParameterInjection;
import consulo.java.impl.intelliLang.config.ui.MethodParameterPanel;
import consulo.language.inject.advanced.ui.InjectionConfigurable;
import consulo.project.Project;

public class MethodParameterInjectionConfigurable extends InjectionConfigurable<MethodParameterInjection, MethodParameterPanel> {
  public MethodParameterInjectionConfigurable(MethodParameterInjection injection, Runnable treeUpdater, Project project) {
    super(injection, treeUpdater, project);
  }

  protected MethodParameterPanel createOptionsPanelImpl() {
    return new MethodParameterPanel(myInjection, myProject);
  }

  public String getBannerSlogan() {
    return "Edit Method Parameter Injection";
  }
}
