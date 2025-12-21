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
package com.intellij.java.impl.find.findUsages;

import com.intellij.java.analysis.impl.find.findUsages.*;
import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import com.intellij.java.impl.lang.java.JavaFindUsagesProvider;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.find.FindBundle;
import consulo.find.FindUsagesHandler;
import consulo.find.FindUsagesHandlerFactory;
import consulo.find.localize.FindLocalize;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.inject.Inject;

import jakarta.annotation.Nonnull;

/**
 * @author peter
 */
@ExtensionImpl(id = "java", order = "last, before default")
public class JavaFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
    private final JavaClassFindUsagesOptions myFindClassOptions;
    private final JavaMethodFindUsagesOptions myFindMethodOptions;
    private final JavaPackageFindUsagesOptions myFindPackageOptions;
    private final JavaThrowFindUsagesOptions myFindThrowOptions;
    private final JavaVariableFindUsagesOptions myFindVariableOptions;

    public static JavaFindUsagesHandlerFactory getInstance(@Nonnull Project project) {
        return project.getExtensionPoint(FindUsagesHandlerFactory.class).findExtensionOrFail(JavaFindUsagesHandlerFactory.class);
    }

    @Inject
    public JavaFindUsagesHandlerFactory(Project project) {
        myFindClassOptions = new JavaClassFindUsagesOptions(project);
        myFindMethodOptions = new JavaMethodFindUsagesOptions(project);
        myFindPackageOptions = new JavaPackageFindUsagesOptions(project);
        myFindThrowOptions = new JavaThrowFindUsagesOptions(project);
        myFindVariableOptions = new JavaVariableFindUsagesOptions(project);
    }

    @Override
    public boolean canFindUsages(@Nonnull PsiElement element) {
        return new JavaFindUsagesProvider().canFindUsagesFor(element);
    }

    @Override
    @RequiredUIAccess
    public FindUsagesHandler createFindUsagesHandler(@Nonnull PsiElement element, boolean forHighlightUsages) {
        if (element instanceof PsiDirectory directory) {
            PsiJavaPackage psiPackage = JavaDirectoryService.getInstance().getPackage(directory);
            return psiPackage == null ? null : new JavaFindUsagesHandler(psiPackage, this);
        }

        if (element instanceof PsiMethod method && !forHighlightUsages) {
            PsiMethod[] methods = SuperMethodWarningUtil.checkSuperMethods(
                method,
                FindLocalize.findSuperMethodWarningActionVerb()
            );
            if (methods.length > 1) {
                return new JavaFindUsagesHandler(element, methods, this);
            }
            if (methods.length == 1) {
                return new JavaFindUsagesHandler(methods[0], this);
            }
            return FindUsagesHandler.NULL_HANDLER;
        }

        return new JavaFindUsagesHandler(element, this);
    }

    public JavaClassFindUsagesOptions getFindClassOptions() {
        return myFindClassOptions;
    }

    public JavaMethodFindUsagesOptions getFindMethodOptions() {
        return myFindMethodOptions;
    }

    public JavaPackageFindUsagesOptions getFindPackageOptions() {
        return myFindPackageOptions;
    }

    public JavaThrowFindUsagesOptions getFindThrowOptions() {
        return myFindThrowOptions;
    }

    public JavaVariableFindUsagesOptions getFindVariableOptions() {
        return myFindVariableOptions;
    }
}
