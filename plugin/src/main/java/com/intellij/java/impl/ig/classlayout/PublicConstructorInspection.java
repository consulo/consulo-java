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
package com.intellij.java.impl.ig.classlayout;

import com.intellij.java.analysis.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.java.impl.ig.psiutils.SerializationUtils;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameterList;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.concurrent.AsyncResult;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Bas Leijdekkers
 */
@ExtensionImpl
public class PublicConstructorInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.publicConstructorDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        return (Boolean) infos[0]
            ? InspectionGadgetsLocalize.publicDefaultConstructorProblemDescriptor().get()
            : InspectionGadgetsLocalize.publicConstructorProblemDescriptor().get();
    }

    @Override
    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    @Nullable
    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new ReplaceConstructorWithFactoryMethodFix();
    }

    private class ReplaceConstructorWithFactoryMethodFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.publicConstructorQuickfix();
        }

        @Override
        @RequiredWriteAction
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class, PsiMethod.class);
            AsyncResult<DataContext> context = DataManager.getInstance().getDataContextFromFocus();
            context.doWhenDone(dataContext -> {
                JavaRefactoringActionHandlerFactory factory = JavaRefactoringActionHandlerFactory.getInstance();
                RefactoringActionHandler handler = factory.createReplaceConstructorWithFactoryHandler();
                handler.invoke(project, new PsiElement[]{element}, dataContext);
            });
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new PublicConstructorVisitor();
    }

    private static class PublicConstructorVisitor extends BaseInspectionVisitor {
        @Override
        @RequiredReadAction
        public void visitMethod(@Nonnull PsiMethod method) {
            super.visitMethod(method);
            if (!method.isConstructor()) {
                return;
            }
            if (!method.isPublic()) {
                return;
            }
            PsiClass aClass = method.getContainingClass();
            if (aClass == null || aClass.isAbstract()) {
                return;
            }
            if (SerializationUtils.isExternalizable(aClass)) {
                PsiParameterList parameterList = method.getParameterList();
                if (parameterList.getParametersCount() == 0) {
                    return;
                }
            }
            registerMethodError(method, Boolean.FALSE);
        }

        @Override
        @RequiredReadAction
        public void visitClass(@Nonnull PsiClass aClass) {
            super.visitClass(aClass);
            if (aClass.isInterface() || aClass.isEnum()) {
                return;
            }
            if (!aClass.isPublic() || aClass.isAbstract()) {
                return;
            }
            PsiMethod[] constructors = aClass.getConstructors();
            if (constructors.length > 0) {
                return;
            }
            if (SerializationUtils.isExternalizable(aClass)) {
                return;
            }
            registerClassError(aClass, Boolean.TRUE);
        }
    }
}
