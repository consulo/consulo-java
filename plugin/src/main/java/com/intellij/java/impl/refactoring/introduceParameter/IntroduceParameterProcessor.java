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
package com.intellij.java.impl.refactoring.introduceParameter;

import com.intellij.java.impl.refactoring.IntroduceParameterRefactoring;
import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.impl.refactoring.util.FieldConflictsResolver;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.java.impl.refactoring.util.occurrences.LocalVariableOccurrenceManager;
import com.intellij.java.impl.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.java.impl.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.java.impl.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import consulo.util.collection.MultiMap;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author dsl
 * @since 2002-05-07
 */
public class IntroduceParameterProcessor extends BaseRefactoringProcessor implements IntroduceParameterData {
    private static final Logger LOG = Logger.getInstance(IntroduceParameterProcessor.class);

    private final PsiMethod myMethodToReplaceIn;
    private final PsiMethod myMethodToSearchFor;
    private PsiExpression myParameterInitializer;
    private final PsiExpression myExpressionToSearch;
    private final PsiLocalVariable myLocalVariable;
    private final boolean myRemoveLocalVariable;
    private final String myParameterName;
    private final boolean myReplaceAllOccurrences;

    private int myReplaceFieldsWithGetters;
    private final boolean myDeclareFinal;
    private final boolean myGenerateDelegate;
    private PsiType myForcedType;
    private final IntList myParametersToRemove;
    private final PsiManager myManager;
    private JavaExpressionWrapper myInitializerWrapper;
    private boolean myHasConflicts;

    /**
     * if expressionToSearch is null, search for localVariable
     */
    public IntroduceParameterProcessor(
        @Nonnull Project project,
        PsiMethod methodToReplaceIn,
        @Nonnull PsiMethod methodToSearchFor,
        PsiExpression parameterInitializer,
        PsiExpression expressionToSearch,
        PsiLocalVariable localVariable,
        boolean removeLocalVariable,
        String parameterName,
        boolean replaceAllOccurrences,
        int replaceFieldsWithGetters,
        boolean declareFinal,
        boolean generateDelegate,
        PsiType forcedType,
        @Nonnull IntList parametersToRemove
    ) {
        super(project);

        myMethodToReplaceIn = methodToReplaceIn;
        myMethodToSearchFor = methodToSearchFor;
        myParameterInitializer = parameterInitializer;
        myExpressionToSearch = expressionToSearch;

        myLocalVariable = localVariable;
        myRemoveLocalVariable = removeLocalVariable;
        myParameterName = parameterName;
        myReplaceAllOccurrences = replaceAllOccurrences;
        myReplaceFieldsWithGetters = replaceFieldsWithGetters;
        myDeclareFinal = declareFinal;
        myGenerateDelegate = generateDelegate;
        myForcedType = forcedType;
        myManager = PsiManager.getInstance(project);

        myParametersToRemove = parametersToRemove;

        myInitializerWrapper = expressionToSearch == null ? null : new JavaExpressionWrapper(expressionToSearch);
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new IntroduceParameterViewDescriptor(myMethodToSearchFor);
    }

    @Nonnull
    @Override
    public PsiType getForcedType() {
        return myForcedType;
    }

    public void setForcedType(PsiType forcedType) {
        myForcedType = forcedType;
    }

    @Override
    public int getReplaceFieldsWithGetters() {
        return myReplaceFieldsWithGetters;
    }

    public void setReplaceFieldsWithGetters(int replaceFieldsWithGetters) {
        myReplaceFieldsWithGetters = replaceFieldsWithGetters;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected UsageInfo[] findUsages() {
        List<UsageInfo> result = new ArrayList<>();

        PsiMethod[] overridingMethods =
            OverridingMethodsSearch.search(myMethodToSearchFor, true).toArray(PsiMethod.EMPTY_ARRAY);
        for (PsiMethod overridingMethod : overridingMethods) {
            result.add(new UsageInfo(overridingMethod));
        }
        if (!myGenerateDelegate) {
            PsiReference[] refs = MethodReferencesSearch.search(myMethodToSearchFor, GlobalSearchScope.projectScope(myProject), true)
                .toArray(PsiReference.EMPTY_ARRAY);


            for (PsiReference ref1 : refs) {
                PsiElement ref = ref1.getElement();
                if (ref instanceof PsiMethod method && method.isConstructor()) {
                    DefaultConstructorImplicitUsageInfo implicitUsageInfo =
                        new DefaultConstructorImplicitUsageInfo(method, method.getContainingClass(), myMethodToSearchFor);
                    result.add(implicitUsageInfo);
                }
                else if (ref instanceof PsiClass psiClass) {
                    result.add(new NoConstructorClassUsageInfo(psiClass));
                }
                else if (!IntroduceParameterUtil.insideMethodToBeReplaced(ref, myMethodToReplaceIn)) {
                    result.add(new ExternalUsageInfo(ref));
                }
                else {
                    result.add(new ChangedMethodCallInfo(ref));
                }
            }
        }

        if (myReplaceAllOccurrences) {
            for (PsiElement expr : getOccurrences()) {
                result.add(new InternalUsageInfo(expr));
            }
        }
        else {
            if (myExpressionToSearch != null && myExpressionToSearch.isValid()) {
                result.add(new InternalUsageInfo(myExpressionToSearch));
            }
        }

        UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
        return UsageViewUtil.removeDuplicatedUsages(usageInfos);
    }

    protected PsiElement[] getOccurrences() {
        OccurrenceManager occurrenceManager;
        if (myLocalVariable == null) {
            occurrenceManager = new ExpressionOccurrenceManager(myExpressionToSearch, myMethodToReplaceIn, null);
        }
        else {
            occurrenceManager = new LocalVariableOccurrenceManager(myLocalVariable, null);
        }
        return occurrenceManager.getOccurrences();
    }

    public boolean hasConflicts() {
        return myHasConflicts;
    }

    private static class ReferencedElementsCollector extends JavaRecursiveElementWalkingVisitor {
        private final Set<PsiElement> myResult = new HashSet<>();

        @Override
        @RequiredReadAction
        public void visitReferenceExpression(PsiReferenceExpression expression) {
            visitReferenceElement(expression);
        }

        @Override
        @RequiredReadAction
        public void visitReferenceElement(@Nonnull PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);
            PsiElement element = reference.resolve();
            if (element != null) {
                myResult.add(element);
            }
        }
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(SimpleReference<UsageInfo[]> refUsages) {
        UsageInfo[] usagesIn = refUsages.get();
        MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();

        AnySameNameVariables anySameNameVariables = new AnySameNameVariables();
        myMethodToReplaceIn.accept(anySameNameVariables);
        Pair<PsiElement, LocalizeValue> conflictPair = anySameNameVariables.getConflict();
        if (conflictPair != null) {
            conflicts.putValue(conflictPair.first, conflictPair.second);
        }

        if (!myGenerateDelegate) {
            detectAccessibilityConflicts(usagesIn, conflicts);
        }

        if (myParameterInitializer != null && !myMethodToReplaceIn.isPrivate()) {
            AnySupers anySupers = new AnySupers();
            myParameterInitializer.accept(anySupers);
            if (anySupers.isResult()) {
                for (UsageInfo usageInfo : usagesIn) {
                    if (!(usageInfo.getElement() instanceof PsiMethod)
                        && !(usageInfo instanceof InternalUsageInfo)
                        && !PsiTreeUtil.isAncestor(myMethodToReplaceIn.getContainingClass(), usageInfo.getElement(), false)) {
                        conflicts.putValue(
                            myParameterInitializer,
                            RefactoringLocalize.parameterInitializerContains0ButNotAllCallsToMethodAreInItsClass(
                                CommonRefactoringUtil.htmlEmphasize(PsiKeyword.SUPER)
                            )
                        );
                        break;
                    }
                }
            }
        }

        myProject.getApplication().getExtensionPoint(IntroduceParameterMethodUsagesProcessor.class)
            .forEach(processor -> processor.findConflicts(this, refUsages.get(), conflicts));

        myHasConflicts = !conflicts.isEmpty();
        return showConflicts(conflicts, usagesIn);
    }

    @RequiredReadAction
    private void detectAccessibilityConflicts(UsageInfo[] usageArray, MultiMap<PsiElement, LocalizeValue> conflicts) {
        if (myParameterInitializer != null) {
            ReferencedElementsCollector collector = new ReferencedElementsCollector();
            myParameterInitializer.accept(collector);
            Set<PsiElement> result = collector.myResult;
            if (!result.isEmpty()) {
                for (UsageInfo usageInfo : usageArray) {
                    if (usageInfo instanceof ExternalUsageInfo && IntroduceParameterUtil.isMethodUsage(usageInfo)) {
                        PsiElement place = usageInfo.getElement();
                        for (PsiElement element : result) {
                            if (element instanceof PsiField field
                                && myReplaceFieldsWithGetters != IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE) {
                                //check getter access instead
                                PsiClass psiClass = field.getContainingClass();
                                LOG.assertTrue(psiClass != null);
                                PsiMethod method =
                                    psiClass.findMethodBySignature(PropertyUtil.generateGetterPrototype(field), true);
                                if (method != null) {
                                    element = method;
                                }
                            }
                            if (element instanceof PsiMember member
                                && !JavaPsiFacade.getInstance(myProject).getResolveHelper().isAccessible(member, place, null)) {
                                LocalizeValue message =
                                    RefactoringLocalize.zeroIsNotAccessibleFrom1ValueForIntroducedParameterInThatMethodCallWillBeIncorrect(
                                        RefactoringUIUtil.getDescription(element, true),
                                        RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(place), true)
                                    );
                                conflicts.putValue(element, message);
                            }
                        }
                    }
                }
            }
        }
    }

    public static class AnySupers extends JavaRecursiveElementWalkingVisitor {
        private boolean myResult = false;

        @Override
        public void visitSuperExpression(@Nonnull PsiSuperExpression expression) {
            myResult = true;
        }

        public boolean isResult() {
            return myResult;
        }

        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
            visitElement(expression);
        }
    }

    public class AnySameNameVariables extends JavaRecursiveElementWalkingVisitor {
        private Pair<PsiElement, LocalizeValue> conflict = null;

        public Pair<PsiElement, LocalizeValue> getConflict() {
            return conflict;
        }

        @Override
        @RequiredReadAction
        public void visitVariable(@Nonnull PsiVariable variable) {
            if (variable == myLocalVariable) {
                return;
            }
            if (myParameterName.equals(variable.getName())) {
                LocalizeValue descr = RefactoringLocalize.thereIsAlreadyA0ItWillConflictWithAnIntroducedParameter(
                    RefactoringUIUtil.getDescription(variable, true)
                );

                conflict = Pair.create(variable, descr);
            }
        }

        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
        }

        @Override
        public void visitElement(PsiElement element) {
            if (conflict != null) {
                return;
            }
            super.visitElement(element);
        }
    }

    @Override
    @RequiredWriteAction
    protected void performRefactoring(@Nonnull UsageInfo[] usages) {
        try {
            PsiElementFactory factory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
            PsiType initializerType = getInitializerType(myForcedType, myParameterInitializer, myLocalVariable);
            setForcedType(initializerType);

            // Converting myParameterInitializer
            if (myParameterInitializer == null) {
                LOG.assertTrue(myLocalVariable != null);
                myParameterInitializer = factory.createExpressionFromText(myLocalVariable.getName(), myLocalVariable);
            }
            else if (myParameterInitializer instanceof PsiArrayInitializerExpression arrayInitializerExpr) {
                PsiExpression newExprArrayInitializer = RefactoringUtil.createNewExpressionFromArrayInitializer(
                    arrayInitializerExpr,
                    initializerType
                );
                myParameterInitializer = (PsiExpression) myParameterInitializer.replace(newExprArrayInitializer);
            }

            myInitializerWrapper = new JavaExpressionWrapper(myParameterInitializer);

            // Changing external occurrences (the tricky part)

            IntroduceParameterUtil.processUsages(usages, this);

            if (myGenerateDelegate) {
                generateDelegate(myMethodToReplaceIn);
                if (myMethodToReplaceIn != myMethodToSearchFor) {
                    PsiMethod method = generateDelegate(myMethodToSearchFor);
                    if (method.getContainingClass().isInterface()) {
                        PsiCodeBlock block = method.getBody();
                        if (block != null) {
                            block.delete();
                        }
                    }
                }
            }

            // Changing signature of initial method
            // (signature of myMethodToReplaceIn will be either changed now or have already been changed)
            LOG.assertTrue(initializerType.isValid());
            FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(myParameterName, myMethodToReplaceIn.getBody());
            IntroduceParameterUtil.changeMethodSignatureAndResolveFieldConflicts(new UsageInfo(myMethodToReplaceIn), usages, this);
            if (myMethodToSearchFor != myMethodToReplaceIn) {
                IntroduceParameterUtil.changeMethodSignatureAndResolveFieldConflicts(new UsageInfo(myMethodToSearchFor), usages, this);
            }
            ChangeContextUtil.clearContextInfo(myParameterInitializer);

            // Replacing expression occurrences
            for (UsageInfo usage : usages) {
                if (usage instanceof ChangedMethodCallInfo) {
                    processChangedMethodCall(usage.getElement());
                }
                else if (usage instanceof InternalUsageInfo) {
                    PsiElement element = usage.getElement();
                    if (element instanceof PsiExpression expression) {
                        element = RefactoringUtil.outermostParenthesizedExpression(expression);
                    }
                    if (element != null) {
                        if (element.getParent() instanceof PsiExpressionStatement exprStmt) {
                            exprStmt.delete();
                        }
                        else {
                            PsiExpression newExpr = factory.createExpressionFromText(myParameterName, element);
                            IntroduceVariableBase.replace((PsiExpression) element, newExpr, myProject);
                        }
                    }
                }
            }

            if (myLocalVariable != null && myRemoveLocalVariable) {
                myLocalVariable.normalizeDeclaration();
                myLocalVariable.getParent().delete();
            }
            fieldConflictsResolver.fix();
        }
        catch (IncorrectOperationException ex) {
            LOG.error(ex);
        }
    }

    @RequiredWriteAction
    private PsiMethod generateDelegate(PsiMethod methodToReplaceIn) throws IncorrectOperationException {
        PsiMethod delegate = (PsiMethod) methodToReplaceIn.copy();
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
        ChangeSignatureProcessor.makeEmptyBody(elementFactory, delegate);
        PsiCallExpression callExpression = ChangeSignatureProcessor.addDelegatingCallTemplate(delegate, delegate.getName());
        PsiExpressionList argumentList = callExpression.getArgumentList();
        assert argumentList != null;
        PsiParameter[] psiParameters = methodToReplaceIn.getParameterList().getParameters();

        PsiParameter anchorParameter = getAnchorParameter(methodToReplaceIn);
        if (psiParameters.length == 0) {
            argumentList.add(myParameterInitializer);
        }
        else {
            if (anchorParameter == null) {
                argumentList.add(myParameterInitializer);
            }
            for (int i = 0; i < psiParameters.length; i++) {
                PsiParameter psiParameter = psiParameters[i];
                if (!myParametersToRemove.contains(i)) {
                    PsiExpression expression = elementFactory.createExpressionFromText(psiParameter.getName(), delegate);
                    argumentList.add(expression);
                }
                if (psiParameter == anchorParameter) {
                    argumentList.add(myParameterInitializer);
                }
            }
        }

        return (PsiMethod) methodToReplaceIn.getContainingClass().addBefore(delegate, methodToReplaceIn);
    }

    static PsiType getInitializerType(PsiType forcedType, PsiExpression parameterInitializer, PsiLocalVariable localVariable) {
        PsiType initializerType;
        if (forcedType == null) {
            if (parameterInitializer == null) {
                if (localVariable != null) {
                    initializerType = localVariable.getType();
                }
                else {
                    LOG.assertTrue(false);
                    initializerType = null;
                }
            }
            else if (localVariable == null) {
                initializerType = RefactoringUtil.getTypeByExpressionWithExpectedType(parameterInitializer);
            }
            else {
                initializerType = localVariable.getType();
            }
        }
        else {
            initializerType = forcedType;
        }
        return initializerType;
    }

    @RequiredWriteAction
    private void processChangedMethodCall(PsiElement element) throws IncorrectOperationException {
        if (element.getParent() instanceof PsiMethodCallExpression methodCall) {
            if (myMethodToReplaceIn == myMethodToSearchFor && PsiTreeUtil.isAncestor(methodCall, myParameterInitializer, false)) {
                return;
            }

            PsiElementFactory factory = JavaPsiFacade.getInstance(methodCall.getProject()).getElementFactory();
            PsiExpression expression = factory.createExpressionFromText(myParameterName, null);
            PsiExpressionList argList = methodCall.getArgumentList();
            PsiExpression[] exprs = argList.getExpressions();

            boolean first = false;
            PsiElement anchor = null;
            if (myMethodToSearchFor.isVarArgs()) {
                int oldParamCount = myMethodToSearchFor.getParameterList().getParametersCount() - 1;
                if (exprs.length >= oldParamCount) {
                    if (oldParamCount > 1) {
                        anchor = exprs[oldParamCount - 2];
                    }
                    else {
                        first = true;
                        anchor = null;
                    }
                }
                else {
                    anchor = exprs[exprs.length - 1];
                }
            }
            else if (exprs.length > 0) {
                anchor = exprs[exprs.length - 1];
            }

            if (anchor != null) {
                argList.addAfter(expression, anchor);
            }
            else if (first && exprs.length > 0) {
                argList.addBefore(expression, exprs[0]);
            }
            else {
                argList.add(expression);
            }

            removeParametersFromCall(argList);
        }
        else {
            LOG.error(element.getParent());
        }
    }

    @RequiredWriteAction
    private void removeParametersFromCall(PsiExpressionList argList) {
        PsiExpression[] exprs = argList.getExpressions();
        for (int i = myParametersToRemove.size() - 1; i >= 0; i--) {
            int paramNum = myParametersToRemove.get(i);
            if (paramNum < exprs.length) {
                try {
                    exprs[paramNum].delete();
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            }
        }
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected String getCommandName() {
        return RefactoringLocalize.introduceParameterCommand(DescriptiveNameUtil.getDescriptiveName(myMethodToReplaceIn)).get();
    }

    @Nullable
    private static PsiParameter getAnchorParameter(PsiMethod methodToReplaceIn) {
        PsiParameterList parameterList = methodToReplaceIn.getParameterList();
        PsiParameter anchorParameter;
        PsiParameter[] parameters = parameterList.getParameters();
        int length = parameters.length;
        if (!methodToReplaceIn.isVarArgs()) {
            anchorParameter = length > 0 ? parameters[length - 1] : null;
        }
        else {
            LOG.assertTrue(length > 0);
            LOG.assertTrue(parameters[length - 1].isVarArgs());
            anchorParameter = length > 1 ? parameters[length - 2] : null;
        }
        return anchorParameter;
    }

    @Override
    public PsiMethod getMethodToReplaceIn() {
        return myMethodToReplaceIn;
    }

    @Nonnull
    @Override
    public PsiMethod getMethodToSearchFor() {
        return myMethodToSearchFor;
    }

    @Override
    public JavaExpressionWrapper getParameterInitializer() {
        return myInitializerWrapper;
    }

    @Nonnull
    @Override
    public String getParameterName() {
        return myParameterName;
    }

    @Override
    public boolean isDeclareFinal() {
        return myDeclareFinal;
    }

    @Override
    public boolean isGenerateDelegate() {
        return myGenerateDelegate;
    }

    @Nonnull
    @Override
    public IntList getParametersToRemove() {
        return myParametersToRemove;
    }

    @Nonnull
    @Override
    public Project getProject() {
        return myProject;
    }
}
