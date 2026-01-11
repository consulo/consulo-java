/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInspection.equalsAndHashcode;

import com.intellij.java.analysis.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.localize.LocalizeValue;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.lang.Couple;
import jakarta.annotation.Nonnull;

import java.util.function.Supplier;

/**
 * @author max
 */
@ExtensionImpl
public class EqualsAndHashcode extends BaseJavaBatchLocalInspectionTool {
    private static record CheckResult(boolean hasEquals, boolean hasHashCode) {
        private static final String CODE_EQUALS = "<code>equals()</code>";
        private static final String CODE_HASH_CODE = "<code>hashCode()</code>";

        public boolean hasInconsistency() {
            return hasEquals != hasHashCode;
        }

        public LocalizeValue getErrorMessage() {
            if (!hasInconsistency()) {
                return LocalizeValue.empty();
            }
            return hasEquals
                ? InspectionLocalize.inspectionEqualsHashcodeOnlyOneDefinedProblemDescriptor(CODE_EQUALS, CODE_HASH_CODE)
                : InspectionLocalize.inspectionEqualsHashcodeOnlyOneDefinedProblemDescriptor(CODE_HASH_CODE, CODE_EQUALS);
        }
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiElementVisitor buildVisitorImpl(
        @Nonnull final ProblemsHolder holder,
        boolean isOnTheFly,
        LocalInspectionToolSession session,
        Object state
    ) {
        Project project = holder.getProject();
        Couple<PsiMethod> pair = CachedValuesManager.getManager(project).getCachedValue(
            project,
            () -> {
                JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
                PsiClass psiObjectClass = project.getApplication().runReadAction(
                    (Supplier<PsiClass>) () -> psiFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(project))
                );
                if (psiObjectClass == null) {
                    return CachedValueProvider.Result.create(null, ProjectRootManager.getInstance(project));
                }
                PsiMethod[] methods = psiObjectClass.getMethods();
                PsiMethod myEquals = null;
                PsiMethod myHashCode = null;
                for (PsiMethod method : methods) {
                    String name = method.getName();
                    if ("equals".equals(name)) {
                        myEquals = method;
                    }
                    else if ("hashCode".equals(name)) {
                        myHashCode = method;
                    }
                }
                return CachedValueProvider.Result.create(Couple.of(myEquals, myHashCode), psiObjectClass);
            }
        );

        if (pair == null) {
            return new PsiElementVisitor() {
            };
        }

        //jdk wasn't configured for the project
        final PsiMethod myEquals = pair.first;
        final PsiMethod myHashCode = pair.second;
        if (myEquals == null || myHashCode == null || !myEquals.isValid() || !myHashCode.isValid()) {
            return new PsiElementVisitor() {
            };
        }

        return new JavaElementVisitor() {
            @Override
            public void visitClass(@Nonnull PsiClass aClass) {
                super.visitClass(aClass);
                CheckResult checkResult = processClass(aClass, myEquals, myHashCode);
                if (checkResult.hasInconsistency()) {
                    PsiIdentifier identifier = aClass.getNameIdentifier();
                    holder.newProblem(checkResult.getErrorMessage())
                        .range(identifier != null ? identifier : aClass)
                        .create();
                }
            }
        };
    }

    private static CheckResult processClass(PsiClass aClass, PsiMethod equals, PsiMethod hashCode) {
        boolean hasEquals = false, hasHashCode = false;
        PsiMethod[] methods = aClass.getMethods();
        for (PsiMethod method : methods) {
            if (MethodSignatureUtil.areSignaturesEqual(method, equals)) {
                hasEquals = true;
            }
            else if (MethodSignatureUtil.areSignaturesEqual(method, hashCode)) {
                hasHashCode = true;
            }
        }
        return new CheckResult(hasEquals, hasHashCode);
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.inspectionEqualsHashcodeDisplayName();
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return LocalizeValue.empty();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return "EqualsAndHashcode";
    }
}
