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
package com.intellij.java.impl.codeInspection.unusedReturnValue;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionTool;
import com.intellij.java.analysis.codeInspection.reference.RefJavaVisitor;
import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.java.impl.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.java.deadCodeNotWorking.OldStyleInspection;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
@ExtensionImpl
public class UnusedReturnValue extends GlobalJavaInspectionTool implements OldStyleInspection {
    private MakeVoidQuickFix myQuickFix;

    public boolean IGNORE_BUILDER_PATTERN = false;

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
            if (refMethod.isConstructor()
                || !refMethod.getSuperMethods().isEmpty()
                || refMethod.getInReferences().size() == 0) {
                return null;
            }

            if (!refMethod.isReturnValueUsed()) {
                PsiMethod psiMethod = (PsiMethod) refMethod.getElement();
                if (IGNORE_BUILDER_PATTERN && PropertyUtil.isSimplePropertySetter(psiMethod)) {
                    return null;
                }

                boolean isNative = psiMethod.hasModifierProperty(PsiModifier.NATIVE);
                if (refMethod.isExternalOverride() && !isNative) {
                    return null;
                }
                return new ProblemDescriptor[]{
                    manager.newProblemDescriptor(InspectionLocalize.inspectionUnusedReturnValueProblemDescriptor())
                        .range(psiMethod.getNavigationElement())
                        .withOptionalFix(!isNative ? getFix(processor) : null)
                        .create()
                };
            }
        }

        return null;
    }

    @Override
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore simple setters", this, "IGNORE_BUILDER_PATTERN");
    }

    @Override
    protected boolean queryExternalUsagesRequests(
        RefManager manager,
        final GlobalJavaInspectionContext globalContext,
        final ProblemDescriptionsProcessor processor,
        Object state
    ) {
        manager.iterate(new RefJavaVisitor() {
            @Override
            public void visitElement(@Nonnull RefEntity refEntity) {
                if (refEntity instanceof RefElement && processor.getDescriptions(refEntity) != null) {
                    refEntity.accept(new RefJavaVisitor() {
                        @Override
                        public void visitMethod(@Nonnull RefMethod refMethod) {
                            globalContext.enqueueMethodUsagesProcessor(refMethod, psiReference -> {
                                processor.ignoreElement(refMethod);
                                return false;
                            });
                        }
                    });
                }
            }
        });

        return false;
    }

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.inspectionUnusedReturnValueDisplayName();
    }

    @Override
    @Nonnull
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesDeclarationRedundancy();
    }

    @Override
    @Nonnull
    public String getShortName() {
        return "UnusedReturnValue";
    }

    private LocalQuickFix getFix(ProblemDescriptionsProcessor processor) {
        if (myQuickFix == null) {
            myQuickFix = new MakeVoidQuickFix(processor);
        }
        return myQuickFix;
    }

    @Override
    @Nullable
    public QuickFix getQuickFix(String hint) {
        return getFix(null);
    }

    private static class MakeVoidQuickFix implements LocalQuickFix {
        private final ProblemDescriptionsProcessor myProcessor;
        private static final Logger LOG = Logger.getInstance(MakeVoidQuickFix.class);

        public MakeVoidQuickFix(ProblemDescriptionsProcessor processor) {
            myProcessor = processor;
        }

        @Override
        @Nonnull
        public LocalizeValue getName() {
            return InspectionLocalize.inspectionUnusedReturnValueMakeVoidQuickfix();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            PsiMethod psiMethod = null;
            if (myProcessor != null) {
                RefElement refElement = (RefElement) myProcessor.getElement(descriptor);
                if (refElement.isValid() && refElement instanceof RefMethod refMethod) {
                    psiMethod = (PsiMethod) refMethod.getElement();
                }
            }
            else {
                psiMethod = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
            }
            if (psiMethod == null) {
                return;
            }
            makeMethodHierarchyVoid(project, psiMethod);
        }

        @RequiredWriteAction
        private static void makeMethodHierarchyVoid(Project project, @Nonnull PsiMethod psiMethod) {
            replaceReturnStatements(psiMethod);
            for (PsiMethod oMethod : OverridingMethodsSearch.search(psiMethod)) {
                replaceReturnStatements(oMethod);
            }
            PsiParameter[] params = psiMethod.getParameterList().getParameters();
            ParameterInfoImpl[] infos = new ParameterInfoImpl[params.length];
            for (int i = 0; i < params.length; i++) {
                PsiParameter param = params[i];
                infos[i] = new ParameterInfoImpl(i, param.getName(), param.getType());
            }

            ChangeSignatureProcessor csp = new ChangeSignatureProcessor(
                project,
                psiMethod,
                false, null, psiMethod.getName(),
                PsiType.VOID,
                infos
            );

            csp.run();
        }

        @RequiredWriteAction
        private static void replaceReturnStatements(@Nonnull PsiMethod method) {
            PsiCodeBlock body = method.getBody();
            if (body != null) {
                final List<PsiReturnStatement> returnStatements = new ArrayList<>();
                body.accept(new JavaRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
                        super.visitReturnStatement(statement);
                        returnStatements.add(statement);
                    }
                });
                PsiStatement[] psiStatements = body.getStatements();
                PsiStatement lastStatement = psiStatements[psiStatements.length - 1];
                for (PsiReturnStatement returnStatement : returnStatements) {
                    try {
                        PsiExpression expression = returnStatement.getReturnValue();
                        if (expression instanceof PsiLiteralExpression || expression instanceof PsiThisExpression) {    //avoid side effects
                            if (returnStatement == lastStatement) {
                                returnStatement.delete();
                            }
                            else {
                                returnStatement.replace(
                                    JavaPsiFacade.getInstance(method.getProject()).getElementFactory()
                                        .createStatementFromText("return;", returnStatement)
                                );
                            }
                        }
                    }
                    catch (IncorrectOperationException e) {
                        LOG.error(e);
                    }
                }
            }
        }
    }
}
