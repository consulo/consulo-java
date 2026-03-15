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
package com.intellij.java.analysis.codeInspection;

import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.deadCodeNotWorking.OldStyleInspection;
import consulo.language.Language;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public abstract class AbstractBaseJavaLocalInspectionTool<State> extends LocalInspectionTool implements OldStyleInspection {
    /**
     * @return set of the features required for a given inspection. The inspection will not be launched on the files where
     * the corresponding features are not available.
     */
    public Set<JavaFeature> requiredFeatures() {
        return Set.of();
    }

    @Override
    @RequiredReadAction
    public boolean isAvailableForFile(PsiFile file) {
        for (JavaFeature feature : requiredFeatures()) {
            if (!PsiUtil.isAvailable(feature, file)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Override this to report problems at method level.
     *
     * @param method     to check.
     * @param manager    InspectionManager to ask for ProblemDescriptors from.
     * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
     * @param state
     * @return <code>null</code> if no problems found or not applicable at method level.
     */
    @Nullable
    public ProblemDescriptor[] checkMethod(PsiMethod method, InspectionManager manager, boolean isOnTheFly, State state) {
        return null;
    }

    /**
     * Override this to report problems at class level.
     *
     * @param aClass     to check.
     * @param manager    InspectionManager to ask for ProblemDescriptors from.
     * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
     * @param state
     * @return <code>null</code> if no problems found or not applicable at class level.
     */
    @Nullable
    public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly, State state) {
        return null;
    }

    /**
     * Override this to report problems at field level.
     *
     * @param field      to check.
     * @param manager    InspectionManager to ask for ProblemDescriptors from.
     * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
     * @param state
     * @return <code>null</code> if no problems found or not applicable at field level.
     */
    @Nullable
    public ProblemDescriptor[] checkField(PsiField field, InspectionManager manager, boolean isOnTheFly, State state) {
        return null;
    }

    @Override
    @Nullable
    public final ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly) {
        return null;
    }

    /**
     * Override this to report problems at file level.
     *
     * @param file       to check.
     * @param manager    InspectionManager to ask for ProblemDescriptors from.
     * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
     * @return <code>null</code> if no problems found or not applicable at file level.
     */
    @Nullable
    public ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly, State state) {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public InspectionToolState<? extends State> createStateProvider() {
        return (InspectionToolState<? extends State>) super.createStateProvider();
    }

    @Override
    public final PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
        return super.buildVisitor(holder, isOnTheFly);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final PsiElementVisitor buildVisitor(
        ProblemsHolder holder,
        boolean isOnTheFly,
        LocalInspectionToolSession session,
        Object state
    ) {
        return buildVisitorImpl(holder, isOnTheFly, session, (State) state);
    }

    public PsiElementVisitor buildVisitorImpl(
        final ProblemsHolder holder,
        final boolean isOnTheFly,
        LocalInspectionToolSession session,
        State state
    ) {
        return new JavaElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitMethod(PsiMethod method) {
                addDescriptors(checkMethod(method, holder.getManager(), isOnTheFly, state));
            }

            @Override
            @RequiredReadAction
            public void visitClass(PsiClass aClass) {
                addDescriptors(checkClass(aClass, holder.getManager(), isOnTheFly, state));
            }

            @Override
            @RequiredReadAction
            public void visitField(PsiField field) {
                addDescriptors(checkField(field, holder.getManager(), isOnTheFly, state));
            }

            @Override
            @RequiredReadAction
            public void visitFile(PsiFile file) {
                addDescriptors(checkFile(file, holder.getManager(), isOnTheFly, state));
            }

            @RequiredReadAction
            private void addDescriptors(ProblemDescriptor[] descriptors) {
                if (descriptors != null) {
                    for (ProblemDescriptor descriptor : descriptors) {
                        holder.registerProblem(descriptor);
                    }
                }
            }
        };
    }

    @Override
    public PsiNamedElement getProblemElement(PsiElement psiElement) {
        return PsiTreeUtil.getNonStrictParentOfType(psiElement, PsiFile.class, PsiClass.class, PsiMethod.class, PsiField.class);
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Nullable
    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.inspectionGeneralToolsGroupName();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }
}
