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
package com.intellij.java.impl.refactoring.inline;

import com.intellij.java.analysis.impl.psi.controlFlow.DefUseUtil;
import com.intellij.java.impl.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.util.InlineUtil;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.language.editor.PsiEquivalenceUtil;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.refactoring.util.RefactoringMessageDialog;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author yole
 */
@ExtensionImpl
public class InlineParameterHandler extends JavaInlineActionHandler {
    private static final Logger LOG = Logger.getInstance(InlineParameterHandler.class);
    public static final LocalizeValue REFACTORING_NAME = RefactoringLocalize.inlineParameterRefactoring();

    @Override
    @RequiredReadAction
    public boolean canInlineElement(PsiElement element) {
        return element instanceof PsiParameter param
            && param.getParent() instanceof PsiParameterList paramList
            && paramList.getParent() instanceof PsiMethod
            && param.getLanguage() == JavaLanguage.INSTANCE;
    }

    @Override
    @RequiredUIAccess
    public void inlineElement(final Project project, Editor editor, PsiElement psiElement) {
        final PsiParameter psiParameter = (PsiParameter) psiElement;
        PsiParameterList parameterList = (PsiParameterList) psiParameter.getParent();
        if (!(parameterList.getParent() instanceof PsiMethod)) {
            return;
        }
        int index = parameterList.getParameterIndex(psiParameter);
        final PsiMethod method = (PsiMethod) parameterList.getParent();

        String errorMessage = getCannotInlineMessage(psiParameter, method);
        if (errorMessage != null) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                errorMessage,
                RefactoringLocalize.inlineParameterRefactoring().get(),
                null
            );
            return;
        }

        SimpleReference<PsiExpression> refInitializer = new SimpleReference<>();
        SimpleReference<PsiExpression> refConstantInitializer = new SimpleReference<>();
        SimpleReference<PsiCallExpression> refMethodCall = new SimpleReference<>();
        List<PsiReference> occurrences = Collections.synchronizedList(new ArrayList<PsiReference>());
        Collection<PsiFile> containingFiles = Collections.synchronizedSet(new HashSet<PsiFile>());
        containingFiles.add(psiParameter.getContainingFile());
        boolean result = ReferencesSearch.search(method).forEach(psiReference -> {
            PsiElement element = psiReference.getElement();
            PsiElement parent = element.getParent();
            if (parent instanceof PsiCallExpression methodCall) {
                occurrences.add(psiReference);
                containingFiles.add(element.getContainingFile());
                PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
                if (expressions.length <= index) {
                    return false;
                }
                PsiExpression argument = expressions[index];
                if (!refInitializer.isNull()) {
                    return argument != null
                        && PsiEquivalenceUtil.areElementsEquivalent(refInitializer.get(), argument)
                        && PsiEquivalenceUtil.areElementsEquivalent(refMethodCall.get(), methodCall);
                }
                if (InlineToAnonymousConstructorProcessor.isConstant(argument) || getReferencedFinalField(argument) != null) {
                    if (refConstantInitializer.isNull()) {
                        refConstantInitializer.set(argument);
                    }
                    else if (!isSameConstant(argument, refConstantInitializer.get())) {
                        return false;
                    }
                }
                else if (!isRecursiveReferencedParameter(argument, psiParameter)) {
                    if (!refConstantInitializer.isNull()) {
                        return false;
                    }
                    refInitializer.set(argument);
                    refMethodCall.set(methodCall);
                }
            }
            return true;
        });
        int offset = editor.getCaretModel().getOffset();
        PsiElement refExpr = psiElement.getContainingFile().findElementAt(offset);
        PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(refExpr, PsiCodeBlock.class);
        if (codeBlock != null) {
            PsiElement[] defs = DefUseUtil.getDefs(codeBlock, psiParameter, refExpr);
            if (defs.length == 1) {
                final PsiElement def = defs[0];
                if (def instanceof PsiReferenceExpression referenceExpression && PsiUtil.isOnAssignmentLeftHand(referenceExpression)) {
                    final PsiExpression rExpr = ((PsiAssignmentExpression) def.getParent()).getRExpression();
                    if (rExpr != null) {
                        final PsiElement[] refs = DefUseUtil.getRefs(codeBlock, psiParameter, refExpr);

                        if (InlineLocalHandler.checkRefsInAugmentedAssignmentOrUnaryModified(refs) == null) {
                            new WriteCommandAction(project) {
                                @Override
                                protected void run(Result result) throws Throwable {
                                    for (final PsiElement ref : refs) {
                                        InlineUtil.inlineVariable(psiParameter, rExpr, (PsiJavaCodeReferenceElement) ref);
                                    }
                                    def.getParent().delete();
                                }
                            }.execute();
                            return;
                        }
                    }
                }
            }
        }
        if (occurrences.isEmpty()) {
            CommonRefactoringUtil
                .showErrorHint(project, editor, "Method has no usages", RefactoringLocalize.inlineParameterRefactoring().get(), null);
            return;
        }
        if (!result) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                "Cannot find constant initializer for parameter",
                RefactoringLocalize.inlineParameterRefactoring().get(),
                null
            );
            return;
        }
        if (!refInitializer.isNull()) {
            if (project.getApplication().isUnitTestMode()) {
                InlineParameterExpressionProcessor processor = new InlineParameterExpressionProcessor(
                    refMethodCall.get(),
                    method,
                    psiParameter,
                    refInitializer.get(),
                    method.getProject().getUserData(InlineParameterExpressionProcessor.CREATE_LOCAL_FOR_TESTS)
                );
                processor.run();
            }
            else {
                boolean createLocal = ReferencesSearch.search(psiParameter).findAll().size() > 1;
                InlineParameterDialog dlg =
                    new InlineParameterDialog(refMethodCall.get(), method, psiParameter, refInitializer.get(), createLocal);
                dlg.show();
            }
            return;
        }
        if (refConstantInitializer.isNull()) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                "Cannot find constant initializer for parameter",
                RefactoringLocalize.inlineParameterRefactoring().get(),
                null
            );
            return;
        }

        final SimpleReference<Boolean> isNotConstantAccessible = new SimpleReference<>();
        PsiExpression constantExpression = refConstantInitializer.get();
        constantExpression.accept(new JavaRecursiveElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitReferenceExpression(PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                PsiElement resolved = expression.resolve();
                if (resolved instanceof PsiMember member && !PsiUtil.isAccessible(member, method, null)) {
                    isNotConstantAccessible.set(Boolean.TRUE);
                }
            }
        });
        if (!isNotConstantAccessible.isNull() && isNotConstantAccessible.get()) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                "Constant initializer is not accessible in method body",
                RefactoringLocalize.inlineParameterRefactoring().get(),
                null
            );
            return;
        }

        for (PsiReference psiReference : ReferencesSearch.search(psiParameter)) {
            PsiElement element = psiReference.getElement();
            if (element instanceof PsiExpression expression && PsiUtil.isAccessedForWriting(expression)) {
                CommonRefactoringUtil.showErrorHint(
                    project,
                    editor,
                    "Inline parameter which has write usages is not supported",
                    RefactoringLocalize.inlineParameterRefactoring().get(),
                    null
                );
                return;
            }
        }

        if (!project.getApplication().isUnitTestMode()) {
            LocalizeValue occurrencesString = RefactoringLocalize.occurencesString(occurrences.size());
            String question = RefactoringLocalize.inlineParameterConfirmation(psiParameter.getName(), constantExpression.getText()).get()
                + " " + occurrencesString;
            RefactoringMessageDialog dialog = new RefactoringMessageDialog(
                REFACTORING_NAME.get(),
                question,
                HelpID.INLINE_VARIABLE,
                "OptionPane.questionIcon",
                true,
                project
            );
            dialog.show();
            if (!dialog.isOK()) {
                return;
            }
        }

        SameParameterValueInspection.InlineParameterValueFix.inlineSameParameterValue(method, psiParameter, constantExpression);
    }

    @Nullable
    @RequiredReadAction
    private static PsiField getReferencedFinalField(PsiExpression argument) {
        if (argument instanceof PsiReferenceExpression referenceExpression && referenceExpression.resolve() instanceof PsiField field) {
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.FINAL)) {
                return field;
            }
        }
        return null;
    }

    @RequiredReadAction
    private static boolean isRecursiveReferencedParameter(PsiExpression argument, PsiParameter param) {
        return argument instanceof PsiReferenceExpression referenceExpression
            && referenceExpression.resolve() instanceof PsiParameter parameter
            && parameter.equals(param);
    }

    @RequiredReadAction
    private static boolean isSameConstant(PsiExpression expr1, PsiExpression expr2) {
        boolean expr1Null = InlineToAnonymousConstructorProcessor.ourNullPattern.accepts(expr1);
        boolean expr2Null = InlineToAnonymousConstructorProcessor.ourNullPattern.accepts(expr2);
        if (expr1Null || expr2Null) {
            return expr1Null && expr2Null;
        }
        PsiField field1 = getReferencedFinalField(expr1);
        PsiField field2 = getReferencedFinalField(expr2);
        if (field1 != null && field1 == field2) {
            return true;
        }
        Object value1 = JavaPsiFacade.getInstance(expr1.getProject()).getConstantEvaluationHelper().computeConstantExpression(expr1);
        Object value2 = JavaPsiFacade.getInstance(expr2.getProject()).getConstantEvaluationHelper().computeConstantExpression(expr2);
        return value1 != null && value2 != null && value1.equals(value2);
    }

    @Nullable
    private static String getCannotInlineMessage(PsiParameter psiParameter, PsiMethod method) {
        if (psiParameter.isVarArgs()) {
            return RefactoringLocalize.inlineParameterErrorVarargs().get();
        }
        if (method.findSuperMethods().length > 0 ||
            OverridingMethodsSearch.search(method, true).toArray(PsiMethod.EMPTY_ARRAY).length > 0) {
            return RefactoringLocalize.inlineParameterErrorHierarchy().get();
        }
        return null;
    }
}
