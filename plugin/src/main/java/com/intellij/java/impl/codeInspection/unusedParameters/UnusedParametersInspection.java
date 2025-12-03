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
package com.intellij.java.impl.codeInspection.unusedParameters;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.ex.EntryPointsManager;
import com.intellij.java.analysis.codeInspection.reference.RefJavaVisitor;
import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import com.intellij.java.analysis.codeInspection.reference.RefParameter;
import com.intellij.java.impl.codeInspection.GlobalJavaBatchInspectionTool;
import com.intellij.java.impl.codeInspection.HTMLJavaHTMLComposer;
import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.java.impl.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.openapi.project.ProjectUtil;
import consulo.java.deadCodeNotWorking.OldStyleInspection;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.resolve.PsiReferenceProcessorAdapter;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 * @since 2001-12-24
 */
@ExtensionImpl
public class UnusedParametersInspection extends GlobalJavaBatchInspectionTool implements OldStyleInspection {
    public static final String SHORT_NAME = "UnusedParameters";

    @Nullable
    @Override
    @RequiredReadAction
    public CommonProblemDescriptor[] checkElement(
        @Nonnull RefEntity refEntity,
        @Nonnull AnalysisScope scope,
        @Nonnull InspectionManager manager,
        @Nonnull GlobalInspectionContext globalContext,
        @Nonnull ProblemDescriptionsProcessor processor,
        @Nonnull Object state
    ) {
        if (refEntity instanceof RefMethod refMethod) {
            if (refMethod.isSyntheticJSP()
                || refMethod.isExternalOverride()
                || !refMethod.isStatic() && !refMethod.isConstructor() && !refMethod.getSuperMethods().isEmpty()
                || (refMethod.isAbstract() || refMethod.getOwnerClass().isInterface()) && refMethod.getDerivedMethods().isEmpty()
                || refMethod.isAppMain()) {
                return null;
            }

            List<RefParameter> unusedParameters = getUnusedParameters(refMethod);

            if (unusedParameters.isEmpty() || refMethod.isEntry()) {
                return null;
            }

            PsiModifierListOwner element = refMethod.getElement();
            if (element != null && EntryPointsManager.getInstance(manager.getProject()).isEntryPoint(element)) {
                return null;
            }

            List<ProblemDescriptor> result = new ArrayList<>();
            for (RefParameter refParameter : unusedParameters) {
                PsiIdentifier psiIdentifier = refParameter.getElement().getNameIdentifier();
                if (psiIdentifier != null) {
                    result.add(
                        manager.newProblemDescriptor(
                                refMethod.isAbstract()
                                    ? InspectionLocalize.inspectionUnusedParameterComposer()
                                    : InspectionLocalize.inspectionUnusedParameterComposer1()
                            )
                            .range(psiIdentifier)
                            .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                            .withFix(new AcceptSuggested(globalContext.getRefManager(), processor, refParameter.toString()))
                            .create()
                    );
                }
            }
            return result.toArray(new CommonProblemDescriptor[result.size()]);
        }
        return null;
    }

    @Override
    protected boolean queryExternalUsagesRequests(
        @Nonnull RefManager manager,
        @Nonnull GlobalJavaInspectionContext globalContext,
        @Nonnull final ProblemDescriptionsProcessor processor
    ) {
        Project project = manager.getProject();
        for (RefElement entryPoint : globalContext.getEntryPointsManager(manager).getEntryPoints()) {
            processor.ignoreElement(entryPoint);
        }

        final PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(project);
        final AnalysisScope scope = manager.getScope();
        manager.iterate(new RefJavaVisitor() {
            @Override
            public void visitElement(@Nonnull RefEntity refEntity) {
                if (refEntity instanceof RefMethod refMethod
                    && refMethod.getElement() instanceof PsiMethod psiMethod
                    //implicit constructors are invisible
                    && !refMethod.isStatic()
                    && !refMethod.isConstructor()
                    && !PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) {
                    List<RefParameter> unusedParameters = getUnusedParameters(refMethod);
                    if (unusedParameters.isEmpty()) {
                        return;
                    }
                    PsiMethod[] derived = OverridingMethodsSearch.search(psiMethod, true).toArray(PsiMethod.EMPTY_ARRAY);
                    for (RefParameter refParameter : unusedParameters) {
                        if (refMethod.isAbstract() && derived.length == 0) {
                            refParameter.parameterReferenced(false);
                            processor.ignoreElement(refParameter);
                        }
                        else {
                            int idx = refParameter.getIndex();
                            boolean[] found = {false};
                            for (int i = 0; i < derived.length && !found[0]; i++) {
                                if (!scope.contains(derived[i])) {
                                    PsiParameter[] parameters = derived[i].getParameterList().getParameters();
                                    if (parameters.length >= idx) {
                                        continue;
                                    }
                                    PsiParameter psiParameter = parameters[idx];
                                    ReferencesSearch.search(psiParameter, helper.getUseScope(psiParameter), false)
                                        .forEach(new PsiReferenceProcessorAdapter(
                                            element1 -> {
                                                refParameter.parameterReferenced(false);
                                                processor.ignoreElement(refParameter);
                                                found[0] = true;
                                                return false;
                                            }
                                        ));
                                }
                            }
                        }
                    }
                }
            }
        });
        return false;
    }

    @Nullable
    @Override
    public String getHint(@Nonnull QuickFix fix) {
        return ((AcceptSuggested) fix).getHint();
    }

    @Nullable
    @Override
    public QuickFix getQuickFix(String hint) {
        return new AcceptSuggested(null, null, hint);
    }

    @Override
    public void compose(@Nonnull StringBuffer buf, @Nonnull RefEntity refEntity, @Nonnull HTMLComposer composer) {
        if (refEntity instanceof RefMethod refMethod) {
            HTMLJavaHTMLComposer javaComposer = composer.getExtension(HTMLJavaHTMLComposer.COMPOSER);
            javaComposer.appendDerivedMethods(buf, refMethod);
            javaComposer.appendSuperMethods(buf, refMethod);
        }
    }

    public static List<RefParameter> getUnusedParameters(RefMethod refMethod) {
        boolean checkDeep = !refMethod.isStatic() && !refMethod.isConstructor();
        List<RefParameter> res = new ArrayList<>();
        RefParameter[] methodParameters = refMethod.getParameters();
        RefParameter[] result = new RefParameter[methodParameters.length];
        System.arraycopy(methodParameters, 0, result, 0, methodParameters.length);

        clearUsedParameters(refMethod, result, checkDeep);

        for (RefParameter parameter : result) {
            if (parameter != null) {
                res.add(parameter);
            }
        }

        return res;
    }

    private static void clearUsedParameters(@Nonnull RefMethod refMethod, RefParameter[] params, boolean checkDeep) {
        RefParameter[] methodParams = refMethod.getParameters();

        for (int i = 0; i < methodParams.length; i++) {
            if (methodParams[i].isUsedForReading()) {
                params[i] = null;
            }
        }

        if (checkDeep) {
            for (RefMethod refOverride : refMethod.getDerivedMethods()) {
                clearUsedParameters(refOverride, params, checkDeep);
            }
        }
    }

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.inspectionUnusedParameterDisplayName();
    }

    @Override
    @Nonnull
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesDeclarationRedundancy();
    }

    @Override
    @Nonnull
    public String getShortName() {
        return SHORT_NAME;
    }

    @Override
    public JComponent createOptionsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        Project project = ProjectUtil.guessCurrentProject(panel);
        panel.add(
            TargetAWT.to(EntryPointsManager.getInstance(project).createConfigureAnnotationsBtn()),
            new GridBagConstraints(
                0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                JBUI.emptyInsets(), 0, 0
            )
        );
        return panel;
    }

    private static class AcceptSuggested implements LocalQuickFix {
        private final RefManager myManager;
        private final String myHint;
        private final ProblemDescriptionsProcessor myProcessor;

        public AcceptSuggested(RefManager manager, ProblemDescriptionsProcessor processor, String hint) {
            myManager = manager;
            myProcessor = processor;
            myHint = hint;
        }

        public String getHint() {
            return myHint;
        }

        @Override
        @Nonnull
        public LocalizeValue getName() {
            return InspectionLocalize.inspectionUnusedParameterDeleteQuickfix();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            if (!FileModificationService.getInstance().preparePsiElementForWrite(descriptor.getPsiElement())) {
                return;
            }
            PsiMethod psiMethod = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
            if (psiMethod != null) {
                List<PsiElement> psiParameters = new ArrayList<>();
                RefElement refMethod = myManager != null ? myManager.getReference(psiMethod) : null;
                if (refMethod != null) {
                    for (RefParameter refParameter : getUnusedParameters((RefMethod) refMethod)) {
                        psiParameters.add(refParameter.getElement());
                    }
                }
                else {
                    PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
                    for (PsiParameter parameter : parameters) {
                        if (Comparing.strEqual(parameter.getName(), myHint)) {
                            psiParameters.add(parameter);
                            break;
                        }
                    }
                }
                PsiModificationTracker tracker = psiMethod.getManager().getModificationTracker();
                long startModificationCount = tracker.getModificationCount();

                removeUnusedParameterViaChangeSignature(psiMethod, psiParameters);
                if (refMethod != null && startModificationCount != tracker.getModificationCount()) {
                    myProcessor.ignoreElement(refMethod);
                }
            }
        }

        @RequiredUIAccess
        private static void removeUnusedParameterViaChangeSignature(PsiMethod psiMethod, Collection<PsiElement> parametersToDelete) {
            List<ParameterInfoImpl> newParameters = new ArrayList<>();
            PsiParameter[] oldParameters = psiMethod.getParameterList().getParameters();
            for (int i = 0; i < oldParameters.length; i++) {
                PsiParameter oldParameter = oldParameters[i];
                if (!parametersToDelete.contains(oldParameter)) {
                    newParameters.add(new ParameterInfoImpl(i, oldParameter.getName(), oldParameter.getType()));
                }
            }

            ParameterInfoImpl[] parameterInfos = newParameters.toArray(new ParameterInfoImpl[newParameters.size()]);

            ChangeSignatureProcessor csp = new ChangeSignatureProcessor(psiMethod.getProject(), psiMethod, false, null, psiMethod.getName(),
                psiMethod.getReturnType(), parameterInfos
            );

            csp.run();
        }
    }
}
