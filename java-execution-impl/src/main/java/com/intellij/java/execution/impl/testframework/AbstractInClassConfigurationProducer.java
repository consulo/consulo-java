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

package com.intellij.java.execution.impl.testframework;

import com.intellij.java.execution.impl.JavaTestConfigurationBase;
import com.intellij.java.execution.impl.junit.InheritorChooser;
import com.intellij.java.execution.impl.junit2.PsiMemberParameterizedLocation;
import com.intellij.java.execution.impl.junit2.info.MethodLocation;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.ConfigurationFromContext;
import consulo.execution.action.Location;
import consulo.execution.action.PsiLocation;
import consulo.execution.configuration.ConfigurationType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.util.List;

public abstract class AbstractInClassConfigurationProducer<T extends JavaTestConfigurationBase> extends AbstractJavaTestConfigurationProducer<T> {
    private static final Logger LOG = Logger.getInstance(AbstractInClassConfigurationProducer.class);

    protected AbstractInClassConfigurationProducer(ConfigurationType configurationType) {
        super(configurationType);
    }

    @Override
    @RequiredUIAccess
    public void onFirstRun(
        @Nonnull ConfigurationFromContext configuration,
        @Nonnull ConfigurationContext fromContext,
        @Nonnull Runnable performRunnable
    ) {
        PsiElement psiElement = configuration.getSourceElement();
        if (psiElement instanceof PsiMethod || psiElement instanceof PsiClass) {
            PsiMethod psiMethod;
            PsiClass containingClass;

            if (psiElement instanceof PsiMethod method) {
                psiMethod = method;
                containingClass = psiMethod.getContainingClass();
            }
            else {
                psiMethod = null;
                containingClass = (PsiClass)psiElement;
            }

            InheritorChooser inheritorChooser = new InheritorChooser() {
                @Override
                @SuppressWarnings("unchecked")
                protected void runForClasses(
                    List<PsiClass> classes,
                    PsiMethod method,
                    ConfigurationContext context,
                    Runnable performRunnable
                ) {
                    ((T)configuration.getConfiguration()).bePatternConfiguration(classes, method);
                    super.runForClasses(classes, method, context, performRunnable);
                }

                @Override
                @SuppressWarnings("unchecked")
                protected void runForClass(PsiClass aClass, PsiMethod psiMethod, ConfigurationContext context, Runnable performRunnable) {
                    if (psiElement instanceof PsiMethod) {
                        Project project = psiMethod.getProject();
                        MethodLocation methodLocation = new MethodLocation(project, psiMethod, PsiLocation.fromPsiElement(aClass));
                        ((T)configuration.getConfiguration()).beMethodConfiguration(methodLocation);
                    }
                    else {
                        ((T)configuration.getConfiguration()).beClassConfiguration(aClass);
                    }
                    super.runForClass(aClass, psiMethod, context, performRunnable);
                }
            };
            if (inheritorChooser.runMethodInAbstractClass(
                fromContext,
                performRunnable,
                psiMethod,
                containingClass,
                aClass -> aClass.isAbstract() && isTestClass(aClass)
            )) {
                return;
            }
        }
        super.onFirstRun(configuration, fromContext, performRunnable);
    }

    @Override
    @RequiredReadAction
    protected boolean setupConfigurationFromContext(
        T configuration,
        ConfigurationContext context,
        SimpleReference<PsiElement> sourceElement
    ) {
        if (isMultipleElementsSelected(context)) {
            return false;
        }

        Location contextLocation = context.getLocation();
        setupConfigurationParamName(configuration, contextLocation);

        PsiClass psiClass = null;
        PsiElement element = context.getPsiLocation();
        while (element != null) {
            if (element instanceof PsiClass aClass && isTestClass(aClass)) {
                psiClass = aClass;
                break;
            }
            else if (element instanceof PsiMember member) {
                psiClass = contextLocation instanceof MethodLocation methodLocation
                    ? methodLocation.getContainingClass()
                    : contextLocation instanceof PsiMemberParameterizedLocation memberParameterizedLocation
                    ? memberParameterizedLocation.getContainingClass()
                    : member.getContainingClass();
                if (isTestClass(psiClass)) {
                    break;
                }
            }
            else if (element instanceof PsiClassOwner classOwner) {
                PsiClass[] classes = classOwner.getClasses();
                if (classes.length == 1) {
                    psiClass = classes[0];
                    break;
                }
            }
            element = element.getParent();
        }
        if (!isTestClass(psiClass)) {
            return false;
        }

        PsiElement psiElement = psiClass;
        RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(context);
        setupConfigurationModule(context, configuration);
        Module originalModule = configuration.getConfigurationModule().getModule();
        configuration.beClassConfiguration(psiClass);

        PsiMethod method = PsiTreeUtil.getParentOfType(context.getPsiLocation(), PsiMethod.class, false);
        while (method != null) {
            if (isTestMethod(false, method)) {
                configuration.beMethodConfiguration(MethodLocation.elementInClass(method, psiClass));
                psiElement = method;
            }
            method = PsiTreeUtil.getParentOfType(method, PsiMethod.class);
        }

        configuration.restoreOriginalModule(originalModule);
        LOG.assertTrue(configuration.getConfigurationModule().getModule() != null);
        settings.setName(configuration.getName());
        sourceElement.set(psiElement);
        return true;
    }
}