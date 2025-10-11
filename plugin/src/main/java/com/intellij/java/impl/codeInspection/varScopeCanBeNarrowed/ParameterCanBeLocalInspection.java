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
package com.intellij.java.impl.codeInspection.varScopeCanBeNarrowed;

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.java.impl.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.java.language.impl.psi.controlFlow.*;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * @author Danila Ponomarenko
 */
@ExtensionImpl
public class ParameterCanBeLocalInspection extends BaseJavaLocalInspectionTool {
    public static final String SHORT_NAME = "ParameterCanBeLocal";

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesClassStructure();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.inspectionParameterCanBeLocalDisplayName();
    }

    @Override
    @Nonnull
    public String getShortName() {
        return SHORT_NAME;
    }

    @Override
    @RequiredReadAction
    public ProblemDescriptor[] checkMethod(
        @Nonnull PsiMethod method,
        @Nonnull InspectionManager manager,
        boolean isOnTheFly,
        Object state
    ) {
        final Collection<PsiParameter> parameters = filterFinal(method.getParameterList().getParameters());
        final PsiCodeBlock body = method.getBody();
        if (body == null || parameters.isEmpty() || isOverrides(method)) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        final List<ProblemDescriptor> result = new ArrayList<>();
        for (PsiParameter parameter : getWriteBeforeRead(parameters, body)) {
            final PsiIdentifier identifier = parameter.getNameIdentifier();
            if (identifier != null && identifier.isPhysical()) {
                result.add(createProblem(manager, identifier, isOnTheFly));
            }
        }
        return result.toArray(new ProblemDescriptor[result.size()]);
    }

    @Nonnull
    private static List<PsiParameter> filterFinal(PsiParameter[] parameters) {
        final List<PsiParameter> result = new ArrayList<>(parameters.length);
        for (PsiParameter parameter : parameters) {
            if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
                result.add(parameter);
            }
        }
        return result;
    }


    @Nonnull
    private static ProblemDescriptor createProblem(
        @Nonnull InspectionManager manager,
        @Nonnull PsiIdentifier identifier,
        boolean isOnTheFly
    ) {
        return manager.createProblemDescriptor(
            identifier,
            InspectionLocalize.inspectionParameterCanBeLocalProblemDescriptor().get(),
            true,
            ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            isOnTheFly,
            new ConvertParameterToLocalQuickFix()
        );
    }

    @RequiredReadAction
    private static Collection<PsiParameter> getWriteBeforeRead(@Nonnull Collection<PsiParameter> parameters, @Nonnull PsiCodeBlock body) {
        final ControlFlow controlFlow = getControlFlow(body);
        if (controlFlow == null) {
            return Collections.emptyList();
        }

        final Set<PsiParameter> result = filterParameters(controlFlow, parameters);
        result.retainAll(ControlFlowUtil.getWrittenVariables(controlFlow, 0, controlFlow.getSize(), false));
        for (final PsiReferenceExpression readBeforeWrite : ControlFlowUtil.getReadBeforeWrite(controlFlow)) {
            final PsiElement resolved = readBeforeWrite.resolve();
            if (resolved instanceof PsiParameter) {
                result.remove(resolved);
            }
        }

        return result;
    }

    private static Set<PsiParameter> filterParameters(@Nonnull ControlFlow controlFlow, @Nonnull Collection<PsiParameter> parameters) {
        final Set<PsiVariable> usedVars = new HashSet<>(ControlFlowUtil.getUsedVariables(controlFlow, 0, controlFlow.getSize()));

        final Set<PsiParameter> result = new HashSet<>();
        for (PsiParameter parameter : parameters) {
            if (usedVars.contains(parameter)) {
                result.add(parameter);
            }
        }
        return result;
    }

    private static boolean isOverrides(PsiMethod method) {
        return SuperMethodsSearch.search(method, null, true, false).findFirst() != null;
    }

    @Nullable
    private static ControlFlow getControlFlow(final PsiElement context) {
        try {
            return ControlFlowFactory.getInstance(context.getProject())
                .getControlFlow(context, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
        }
        catch (AnalysisCanceledException e) {
            return null;
        }
    }

    public static class ConvertParameterToLocalQuickFix extends BaseConvertToLocalQuickFix<PsiParameter> {
        @Override
        @RequiredReadAction
        protected PsiParameter getVariable(@Nonnull ProblemDescriptor descriptor) {
            return (PsiParameter) descriptor.getPsiElement().getParent();
        }

        @Override
        protected PsiElement applyChanges(
            @Nonnull final Project project,
            @Nonnull final String localName,
            @Nullable final PsiExpression initializer,
            @Nonnull final PsiParameter parameter,
            @Nonnull final Collection<PsiReference> references,
            @Nonnull final Function<PsiDeclarationStatement, PsiElement> action
        ) {
            final PsiElement scope = parameter.getDeclarationScope();
            if (scope instanceof PsiMethod) {
                final PsiMethod method = (PsiMethod) scope;
                final PsiParameter[] parameters = method.getParameterList().getParameters();

                final List<ParameterInfoImpl> info = new ArrayList<>();
                for (int i = 0; i < parameters.length; i++) {
                    PsiParameter psiParameter = parameters[i];
                    if (psiParameter == parameter) {
                        continue;
                    }
                    info.add(new ParameterInfoImpl(i, psiParameter.getName(), psiParameter.getType()));
                }
                final ParameterInfoImpl[] newParams = info.toArray(new ParameterInfoImpl[info.size()]);
                final String visibilityModifier = VisibilityUtil.getVisibilityModifier(method.getModifierList());
                final ChangeSignatureProcessor cp = new ChangeSignatureProcessor(project, method, false, visibilityModifier,
                    method.getName(), method.getReturnType(), newParams
                ) {
                    @Override
                    protected void performRefactoring(UsageInfo[] usages) {
                        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
                        final PsiElement newDeclaration =
                            moveDeclaration(elementFactory, localName, parameter, initializer, action, references);
                        super.performRefactoring(usages);
                        positionCaretToDeclaration(project, newDeclaration.getContainingFile(), newDeclaration);
                    }
                };
                cp.run();
            }
            return null;
        }

        @Nonnull
        @Override
        protected String suggestLocalName(@Nonnull Project project, @Nonnull PsiParameter parameter, @Nonnull PsiCodeBlock scope) {
            return parameter.getName();
        }
    }
}
