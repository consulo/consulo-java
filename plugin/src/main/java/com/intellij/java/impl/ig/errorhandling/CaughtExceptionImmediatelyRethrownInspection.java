/*
 * Copyright 2007-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.impl.ig.psiutils.VariableSearchUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.application.util.query.Query;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class CaughtExceptionImmediatelyRethrownInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.caughtExceptionImmediatelyRethrownDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.caughtExceptionImmediatelyRethrownProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        PsiTryStatement tryStatement = (PsiTryStatement) infos[0];
        boolean removeTryCatch = tryStatement.getCatchSections().length == 1 && tryStatement.getFinallyBlock() == null &&
            tryStatement.getResourceList() == null;
        return new DeleteCatchSectionFix(removeTryCatch);
    }

    private static class DeleteCatchSectionFix extends InspectionGadgetsFix {
        private final boolean removeTryCatch;

        DeleteCatchSectionFix(boolean removeTryCatch) {
            this.removeTryCatch = removeTryCatch;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
          return removeTryCatch
              ? InspectionGadgetsLocalize.removeTryCatchQuickfix()
              : InspectionGadgetsLocalize.deleteCatchSectionQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiElement parent = element.getParent();
            if (!(parent instanceof PsiParameter)) {
                return;
            }
            PsiParameter parameter = (PsiParameter) parent;
            PsiElement grandParent = parameter.getParent();
            if (!(grandParent instanceof PsiCatchSection)) {
                return;
            }
            PsiCatchSection catchSection = (PsiCatchSection) grandParent;
            PsiTryStatement tryStatement = catchSection.getTryStatement();
            if (removeTryCatch) {
                PsiCodeBlock codeBlock = tryStatement.getTryBlock();
                if (codeBlock == null) {
                    return;
                }
                PsiStatement[] statements = codeBlock.getStatements();
                if (statements.length == 0) {
                    tryStatement.delete();
                    return;
                }
                PsiElement containingElement = tryStatement.getParent();
                boolean keepBlock;
                if (containingElement instanceof PsiCodeBlock) {
                    PsiCodeBlock parentBlock = (PsiCodeBlock) containingElement;
                    keepBlock = VariableSearchUtils.containsConflictingDeclarations(codeBlock, parentBlock);
                }
                else {
                    keepBlock = true;
                }
                if (keepBlock) {
                    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
                    PsiElementFactory factory = psiFacade.getElementFactory();
                    PsiBlockStatement resultStatement = (PsiBlockStatement) factory.createStatementFromText("{}", element);
                    PsiCodeBlock resultBlock = resultStatement.getCodeBlock();
                    for (PsiStatement statement : statements) {
                        resultBlock.add(statement);
                    }
                    tryStatement.replace(resultStatement);
                }
                else {
                    for (PsiStatement statement : statements) {
                        containingElement.addBefore(statement, tryStatement);
                    }
                    tryStatement.delete();
                }
            }
            else {
                catchSection.delete();
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new CaughtExceptionImmediatelyRethrownVisitor();
    }

    private static class CaughtExceptionImmediatelyRethrownVisitor extends BaseInspectionVisitor {

        @Override
        public void visitThrowStatement(PsiThrowStatement statement) {
            super.visitThrowStatement(statement);
            PsiExpression expression = ParenthesesUtils.stripParentheses(statement.getException());
            if (!(expression instanceof PsiReferenceExpression)) {
                return;
            }
            PsiStatement previousStatement = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
            if (previousStatement != null) {
                return;
            }
            PsiElement parent = statement.getParent();
            if (parent instanceof PsiStatement) {
                // e.g. if (notsure) throw e;
                return;
            }
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
            PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiParameter)) {
                return;
            }
            PsiParameter parameter = (PsiParameter) target;
            PsiElement declarationScope = parameter.getDeclarationScope();
            if (!(declarationScope instanceof PsiCatchSection)) {
                return;
            }
            PsiCatchSection catchSection = (PsiCatchSection) declarationScope;
            PsiCodeBlock block = PsiTreeUtil.getParentOfType(statement, PsiCodeBlock.class);
            if (block == null) {
                return;
            }
            PsiElement blockParent = block.getParent();
            if (blockParent != catchSection) {
                // e.g. if (notsure) { throw e; }
                return;
            }
            if (isSuperClassExceptionCaughtLater(parameter, catchSection)) {
                return;
            }
            Query<PsiReference> query = ReferencesSearch.search(parameter);
            for (PsiReference reference : query) {
                PsiElement element = reference.getElement();
                if (element != expression) {
                    return;
                }
            }
            PsiTryStatement tryStatement = catchSection.getTryStatement();
            registerVariableError(parameter, tryStatement);
        }

        private static boolean isSuperClassExceptionCaughtLater(PsiVariable parameter, PsiCatchSection catchSection) {
            PsiTryStatement tryStatement = catchSection.getTryStatement();
            PsiCatchSection[] catchSections = tryStatement.getCatchSections();
            int index = 0;
            while (catchSections[index] != catchSection && index < catchSections.length) {
                index++;
            }
            PsiType type = parameter.getType();
            final Set<PsiClass> parameterClasses = new HashSet();
            processExceptionClasses(type, aClass -> {
                parameterClasses.add(aClass);
                return true;
            });
            if (parameterClasses.isEmpty()) {
                return false;
            }
            final Ref<Boolean> superClassExceptionType = new Ref(Boolean.FALSE);
            for (int i = index; i < catchSections.length; i++) {
                PsiCatchSection nextCatchSection = catchSections[i];
                PsiParameter nextParameter = nextCatchSection.getParameter();
                if (nextParameter == null) {
                    continue;
                }
                PsiType nextType = nextParameter.getType();
                processExceptionClasses(nextType, new Processor<PsiClass>() {
                    @Override
                    public boolean process(PsiClass aClass) {
                        for (PsiClass parameterClass : parameterClasses) {
                            if (parameterClass.isInheritor(aClass, true)) {
                                superClassExceptionType.set(Boolean.TRUE);
                                return false;
                            }
                        }
                        return true;
                    }
                });
                if (superClassExceptionType.get().booleanValue()) {
                    return true;
                }
            }
            return false;
        }

        private static void processExceptionClasses(PsiType type, Processor<PsiClass> processor) {
            if (type instanceof PsiClassType) {
                PsiClassType classType = (PsiClassType) type;
                PsiClass aClass = classType.resolve();
                if (aClass != null) {
                    processor.process(aClass);
                }
            }
            else if (type instanceof PsiDisjunctionType) {
                PsiDisjunctionType disjunctionType = (PsiDisjunctionType) type;
                for (PsiType disjunction : disjunctionType.getDisjunctions()) {
                    if (!(disjunction instanceof PsiClassType)) {
                        continue;
                    }
                    PsiClassType classType = (PsiClassType) disjunction;
                    PsiClass aClass = classType.resolve();
                    if (aClass != null) {
                        processor.process(aClass);
                    }
                }
            }
        }
    }
}