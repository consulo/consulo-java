/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.execution.impl.JavaTestConfigurationBase;
import com.intellij.java.execution.impl.junit.JavaRunConfigurationProducerBase;
import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.testIntegration.TestFramework;
import consulo.annotation.access.RequiredReadAction;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.Location;
import consulo.execution.action.PsiLocation;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Couple;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;

import java.io.IOException;
import java.util.*;

public abstract class TestDiscoveryConfigurationProducer extends JavaRunConfigurationProducerBase<JavaTestConfigurationBase> {
    protected TestDiscoveryConfigurationProducer(ConfigurationType type) {
        super(type);
    }

    protected abstract void setPosition(JavaTestConfigurationBase configuration, PsiLocation<PsiMethod> position);

    protected abstract Couple<String> getPosition(JavaTestConfigurationBase configuration);

    @Override
    @RequiredReadAction
    protected boolean setupConfigurationFromContext(
        JavaTestConfigurationBase configuration,
        ConfigurationContext configurationContext,
        SimpleReference<PsiElement> ref
    ) {
        if (!TestDiscoveryExtension.TESTDISCOVERY_ENABLED) {
            return false;
        }
        Location contextLocation = configurationContext.getLocation();
        assert contextLocation != null;
        Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
        if (location == null) {
            return false;
        }
        PsiMethod sourceMethod = getSourceMethod(location);
        Couple<String> position = getPosition(sourceMethod);
        if (sourceMethod != null && position != null) {
            try {
                Project project = configuration.getProject();
                TestDiscoveryIndex testDiscoveryIndex = TestDiscoveryIndex.getInstance(project);
                Collection<String> testsByMethodName = testDiscoveryIndex.getTestsByMethodName(position.first, position.second);
                if (testsByMethodName == null || ContainerUtil.filter(
                    testsByMethodName,
                    s -> s.startsWith(configuration.getFrameworkPrefix())
                ).isEmpty()) {
                    return false;
                }
                setPosition(configuration, new PsiLocation<>(sourceMethod));
                configuration.setName("Tests for " + StringUtil.getShortName(position.first) + "." + position.second);

                RunnerAndConfigurationSettings template =
                    configurationContext.getRunManager().getConfigurationTemplate(getConfigurationFactory());
                Module predefinedModule =
                    ((ModuleBasedConfiguration)template.getConfiguration()).getConfigurationModule().getModule();
                if (predefinedModule != null) {
                    configuration.setModule(predefinedModule);
                }

                //potentially this set won't be big, it reflects modules from where user starts his tests
                Collection<String> modules =
                    testDiscoveryIndex.getTestModulesByMethodName(position.first, position.second, configuration.getFrameworkPrefix());
                if (modules.isEmpty()) {
                    return true;
                }

                List<Module> survivedModules = new ArrayList<>();
                ModuleManager moduleManager = ModuleManager.getInstance(project);
                for (String moduleName : modules) {
                    Module moduleByName = moduleManager.findModuleByName(moduleName);
                    if (moduleByName != null) {
                        survivedModules.add(moduleByName);
                    }
                }
                if (survivedModules.isEmpty()) {
                    return true;
                }

                Set<Module> allModules = new HashSet<>(Arrays.asList(moduleManager.getModules()));
                survivedModules.forEach(module -> {
                    List<Module> dependentModules = ModuleUtilCore.getAllDependentModules(module);
                    dependentModules.add(module);
                    allModules.retainAll(dependentModules);
                });
                if (!allModules.isEmpty()) {
                    Module aModule = allModules.iterator().next();
                    for (Module module : survivedModules) {
                        if (allModules.contains(module)) {
                            aModule = module;
                        }
                    }
                    configuration.setModule(aModule);
                }

                return true;
            }
            catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    @Override
    protected Module findModule(JavaTestConfigurationBase configuration, Module contextModule) {
        return null;
    }

    private static PsiMethod getSourceMethod(Location location) {
        PsiElement psiElement = location.getPsiElement();
        PsiMethod psiMethod = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
        if (psiMethod != null) {
            PsiClass containingClass = psiMethod.getContainingClass();
            if (containingClass != null) {
                TestFramework testFramework = TestFrameworks.detectFramework(containingClass);
                if (testFramework != null) {
                    return null;
                }
                return psiMethod;
            }
        }
        return null;
    }

    private static Couple<String> getPosition(PsiMethod method) {
        if (method == null) {
            return null;
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return null;
        }
        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName != null) {
            return Couple.of(qualifiedName, method.getName());
        }
        return null;
    }

    @Override
    public boolean isConfigurationFromContext(JavaTestConfigurationBase configuration, ConfigurationContext configurationContext) {
        Couple<String> position = getPosition(getSourceMethod(configurationContext.getLocation()));
        return position != null && position.equals(getPosition(configuration));
    }
}
