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
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * @author Danila Ponomarenko
 */
@ExtensionImpl
public class ParameterCanBeLocalInspection extends BaseJavaLocalInspectionTool {
    public static final String SHORT_NAME = "ParameterCanBeLocal";

    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesClassStructure();
    }

    @Override
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.inspectionParameterCanBeLocalDisplayName();
    }

    @Override
    public String getShortName() {
        return SHORT_NAME;
    }

    @Override
    @RequiredReadAction
    public ProblemDescriptor[] checkMethod(
        PsiMethod method,
        InspectionManager manager,
        boolean isOnTheFly,
        Object state
    ) {
        Collection<PsiParameter> parameters = filterFinal(method.getParameterList().getParameters());
        PsiCodeBlock body = method.getBody();
        if (body == null || parameters.isEmpty() || isOverrides(method)) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }

        List<ProblemDescriptor> result = new ArrayList<>();
        for (PsiParameter parameter : getWriteBeforeRead(parameters, body)) {
            PsiIdentifier identifier = parameter.getNameIdentifier();
            if (identifier != null && identifier.isPhysical()) {
                result.add(createProblem(manager, identifier, isOnTheFly));
            }
        }
        return result.toArray(new ProblemDescriptor[result.size()]);
    }

    private static List<PsiParameter> filterFinal(PsiParameter[] parameters) {
        List<PsiParameter> result = new ArrayList<>(parameters.length);
        for (PsiParameter parameter : parameters) {
            if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
                result.add(parameter);
            }
        }
        return result;
    }

    @RequiredReadAction
    private static ProblemDescriptor createProblem(
        InspectionManager manager,
        PsiIdentifier identifier,
        boolean isOnTheFly
    ) {
        return manager.newProblemDescriptor(InspectionLocalize.inspectionParameterCanBeLocalProblemDescriptor())
            .range(identifier)
            .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
            .onTheFly(isOnTheFly)
            .withFix(new ConvertParameterToLocalQuickFix())
            .create();
    }

    @RequiredReadAction
    private static Collection<PsiParameter> getWriteBeforeRead(Collection<PsiParameter> parameters, PsiCodeBlock body) {
        ControlFlow controlFlow = getControlFlow(body);
        if (controlFlow == null) {
            return Collections.emptyList();
        }

        Set<PsiParameter> result = filterParameters(controlFlow, parameters);
        result.retainAll(ControlFlowUtil.getWrittenVariables(controlFlow, 0, controlFlow.getSize(), false));
        for (PsiReferenceExpression readBeforeWrite : ControlFlowUtil.getReadBeforeWrite(controlFlow)) {
            if (readBeforeWrite.resolve() instanceof PsiParameter param) {
                result.remove(param);
            }
        }

        return result;
    }

    private static Set<PsiParameter> filterParameters(ControlFlow controlFlow, Collection<PsiParameter> parameters) {
        Set<PsiVariable> usedVars = new HashSet<>(ControlFlowUtil.getUsedVariables(controlFlow, 0, controlFlow.getSize()));

        Set<PsiParameter> result = new HashSet<>();
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
    private static ControlFlow getControlFlow( PsiElement context) {
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
        protected PsiParameter getVariable(ProblemDescriptor descriptor) {
            return (PsiParameter) descriptor.getPsiElement().getParent();
        }

        @Override
        @RequiredUIAccess
        protected PsiElement applyChanges(
            final Project project,
            final String localName,
            @Nullable final PsiExpression initializer,
            final PsiParameter parameter,
            final Collection<PsiReference> references,
            final Function<PsiDeclarationStatement, PsiElement> action
        ) {
            if (parameter.getDeclarationScope() instanceof PsiMethod method) {
                PsiParameter[] parameters = method.getParameterList().getParameters();

                List<ParameterInfoImpl> info = new ArrayList<>();
                for (int i = 0; i < parameters.length; i++) {
                    PsiParameter psiParameter = parameters[i];
                    if (psiParameter == parameter) {
                        continue;
                    }
                    info.add(new ParameterInfoImpl(i, psiParameter.getName(), psiParameter.getType()));
                }
                final ParameterInfoImpl[] newParams = info.toArray(new ParameterInfoImpl[info.size()]);
                final String visibilityModifier = VisibilityUtil.getVisibilityModifier(method.getModifierList());
                ChangeSignatureProcessor cp = new ChangeSignatureProcessor(
                    project,
                    method,
                    false,
                    visibilityModifier,
                    method.getName(),
                    method.getReturnType(),
                    newParams
                ) {
                    @Override
                    @RequiredReadAction
                    protected void performRefactoring(UsageInfo[] usages) {
                        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
                        PsiElement newDeclaration = moveDeclaration(elementFactory, localName, parameter, initializer, action, references);
                        super.performRefactoring(usages);
                        positionCaretToDeclaration(project, newDeclaration.getContainingFile(), newDeclaration);
                    }
                };
                cp.run();
            }
            return null;
        }

        @Override
        protected String suggestLocalName(Project project, PsiParameter parameter, PsiCodeBlock scope) {
            return parameter.getName();
        }
    }
}
