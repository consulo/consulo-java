/*
 * Copyright 2013-2026 consulo.io
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
package com.intellij.java.execution.impl.ui;

import com.intellij.java.execution.configurations.JavaRunConfigurationModule;
import com.intellij.java.language.psi.PsiClass;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class BaseConfigurationModuleSelector {
    private final Project myProject;

    protected BaseConfigurationModuleSelector(Project project) {
        myProject = project;
    }

    public abstract void applyTo(ModuleBasedConfiguration configurationModule);

    public abstract void reset(ModuleBasedConfiguration configuration);

    public abstract @Nullable Module getModule();

    protected abstract void setModules(Collection<Module> modules);

    public void reset() {
        Module[] modules = ModuleManager.getInstance(getProject()).getModules();
        List<Module> list = new ArrayList<>();
        for (Module module : modules) {
            if (isModuleAccepted(module)) {
                list.add(module);
            }
        }
        setModules(list);
    }

    public boolean isModuleAccepted(Module module) {
        return ModuleUtilCore.getExtension(module, JavaModuleExtension.class) != null;
    }

    public Project getProject() {
        return myProject;
    }

    public JavaRunConfigurationModule getConfigurationModule() {
        JavaRunConfigurationModule configurationModule = new JavaRunConfigurationModule(getProject(), false);
        configurationModule.setModule(getModule());
        return configurationModule;
    }

    public @Nullable PsiClass findClass(String className) {
        return getConfigurationModule().findClass(className);
    }

    public String getModuleName() {
        Module module = getModule();
        return module == null ? "" : module.getName();
    }
}
