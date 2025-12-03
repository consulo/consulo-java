/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.unneededThrows;

import com.intellij.java.analysis.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.impl.codeInspection.DeleteThrowsFix;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.impl.localize.JavaErrorLocalize;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author anna
 * @since 2005-11-15
 */
@ExtensionImpl
public class RedundantThrowsDeclaration extends BaseJavaBatchLocalInspectionTool {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesDeclarationRedundancy();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.redundantThrowsDeclaration();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return "RedundantThrowsDeclaration";
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkFile(
        @Nonnull PsiFile file,
        @Nonnull final InspectionManager manager,
        final boolean isOnTheFly,
        Object state
    ) {
        final Set<ProblemDescriptor> problems = new HashSet<>();
        file.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            @RequiredReadAction
            public void visitReferenceElement(@Nonnull PsiJavaCodeReferenceElement reference) {
                ProblemDescriptor descriptor = checkExceptionsNeverThrown(reference, manager, isOnTheFly);
                if (descriptor != null) {
                    problems.add(descriptor);
                }
            }
        });
        return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
    }

    @RequiredReadAction
    private static ProblemDescriptor checkExceptionsNeverThrown(
        PsiJavaCodeReferenceElement referenceElement,
        InspectionManager inspectionManager,
        boolean onTheFly
    ) {
        if (!(referenceElement.getParent() instanceof PsiReferenceList referenceList)) {
            return null;
        }
        if (!(referenceList.getParent() instanceof PsiMethod method)) {
            return null;
        }
        if (referenceList != method.getThrowsList()) {
            return null;
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return null;
        }

        PsiManager manager = referenceElement.getManager();
        PsiClassType exceptionType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(referenceElement);
        if (ExceptionUtil.isUncheckedExceptionOrSuperclass(exceptionType)) {
            return null;
        }

        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return null;
        }

        if (!method.isPrivate() && !method.isStatic() && !method.isFinal()
            && !method.isConstructor()
            && !(containingClass instanceof PsiAnonymousClass)
            && !containingClass.isFinal()) {
            return null;
        }

        Collection<PsiClassType> types = ExceptionUtil.collectUnhandledExceptions(body, method);
        Collection<PsiClassType> unhandled = new HashSet<>(types);
        if (method.isConstructor()) {
            // there may be field initializer throwing exception
            // that exception must be caught in the constructor
            PsiField[] fields = containingClass.getFields();
            for (PsiField field : fields) {
                if (field.isStatic()) {
                    continue;
                }
                PsiExpression initializer = field.getInitializer();
                if (initializer == null) {
                    continue;
                }
                unhandled.addAll(ExceptionUtil.collectUnhandledExceptions(initializer, field));
            }
        }

        for (PsiClassType unhandledException : unhandled) {
            if (unhandledException.isAssignableFrom(exceptionType) || exceptionType.isAssignableFrom(unhandledException)) {
                return null;
            }
        }

        if (JavaHighlightUtil.isSerializationRelatedMethod(method, containingClass)) {
            return null;
        }

        return inspectionManager.newProblemDescriptor(JavaErrorLocalize.exceptionIsNeverThrown(JavaHighlightUtil.formatType(exceptionType)))
            .range(referenceElement)
            .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
            .onTheFly(onTheFly)
            .withFix(new DeleteThrowsFix(method, exceptionType))
            .create();
    }
}
