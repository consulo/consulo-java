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
package com.intellij.java.execution.impl.application;

import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.execution.configurations.ConfigurationUtil;
import com.intellij.java.execution.impl.junit.JavaRunConfigurationProducerBase;
import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PsiMethodUtil;
import consulo.application.ReadAction;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.Location;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.module.Module;
import consulo.util.lang.Comparing;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nullable;

public abstract class AbstractApplicationConfigurationProducer<T extends ApplicationConfiguration> extends JavaRunConfigurationProducerBase<T> {
    public AbstractApplicationConfigurationProducer(ApplicationConfigurationType configurationType) {
        super(configurationType);
    }

    @Override
    protected boolean setupConfigurationFromContext(
        T configuration,
        ConfigurationContext context,
        SimpleReference<PsiElement> sourceElement
    ) {
        Location contextLocation = context.getLocation();
        if (contextLocation == null) {
            return false;
        }
        Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
        if (location == null) {
            return false;
        }
        PsiElement element = location.getPsiElement();
        if (!element.isPhysical()) {
            return false;
        }
        PsiElement currentElement = element;
        PsiMethod method;
        while ((method = findMain(currentElement)) != null) {
            PsiClass aClass = method.getContainingClass();
            if (ConfigurationUtil.MAIN_CLASS.test(aClass)) {
                sourceElement.set(method);
                setupConfiguration(configuration, aClass, context);
                return true;
            }
            currentElement = method.getParent();
        }
        PsiClass aClass = ApplicationConfigurationType.getMainClass(element);
        if (aClass == null) {
            return false;
        }
        sourceElement.set(aClass);
        setupConfiguration(configuration, aClass, context);
        return true;
    }

    private void setupConfiguration(T configuration, PsiClass aClass, ConfigurationContext context) {
        configuration.MAIN_CLASS_NAME = ReadAction.compute(() -> JavaExecutionUtil.getRuntimeQualifiedName(aClass));
        configuration.setGeneratedName();
        setupConfigurationModule(context, configuration);
    }

    @Nullable
    private static PsiMethod findMain(PsiElement element) {
        PsiMethod method;
        while ((method = PsiTreeUtil.getParentOfType(element, PsiMethod.class)) != null) {
            if (PsiMethodUtil.isMainMethod(method)) {
                return method;
            }
            else {
                element = method.getParent();
            }
        }
        return null;
    }

    @Override
    public boolean isConfigurationFromContext(T appConfiguration, ConfigurationContext context) {
        PsiElement location = context.getPsiLocation();
        PsiClass aClass = ApplicationConfigurationType.getMainClass(location);
        if (aClass != null && Comparing.equal(JavaExecutionUtil.getRuntimeQualifiedName(aClass), appConfiguration.MAIN_CLASS_NAME)) {
            PsiMethod method = PsiTreeUtil.getParentOfType(location, PsiMethod.class, false);
            if (method != null && TestFrameworks.getInstance().isTestMethod(method)) {
                return false;
            }

            Module configurationModule = appConfiguration.getConfigurationModule().getModule();
            if (Comparing.equal(context.getModule(), configurationModule)) {
                return true;
            }

            ApplicationConfiguration template =
                (ApplicationConfiguration)context.getRunManager().getConfigurationTemplate(getConfigurationFactory()).getConfiguration();
            Module predefinedModule = template.getConfigurationModule().getModule();
            if (Comparing.equal(predefinedModule, configurationModule)) {
                return true;
            }
        }
        return false;
    }
}
