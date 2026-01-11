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
package com.intellij.java.impl.refactoring.inline;

import com.intellij.java.analysis.impl.codeInspection.SideEffectChecker;
import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.JavaLangClassMemberReference;
import com.intellij.java.impl.refactoring.introduceParameter.Util;
import com.intellij.java.impl.refactoring.rename.RenameJavaVariableProcessor;
import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.impl.refactoring.util.InlineUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.impl.psi.controlFlow.*;
import com.intellij.java.language.impl.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.infos.MethodCandidateInfo;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.LogicalPosition;
import consulo.java.localize.JavaRefactoringLocalize;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.inline.GenericInlineHandler;
import consulo.language.editor.refactoring.inline.InlineHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.NonCodeUsageInfoFactory;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.refactoring.util.NonCodeSearchDescriptionLocation;
import consulo.language.editor.refactoring.util.TextOccurrencesUtil;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.impl.psi.CodeEditUtil;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localHistory.LocalHistory;
import consulo.localHistory.LocalHistoryAction;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.NonCodeUsageInfo;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

public class InlineMethodProcessor extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance(InlineMethodProcessor.class);

    private PsiMethod myMethod;
    private PsiJavaCodeReferenceElement myReference;
    private final Editor myEditor;
    private final boolean myInlineThisOnly;
    private final boolean mySearchInComments;
    private final boolean mySearchForTextOccurrences;

    private final PsiManager myManager;
    private final PsiElementFactory myFactory;
    private final CodeStyleManager myCodeStyleManager;
    private final JavaCodeStyleManager myJavaCodeStyle;

    private PsiCodeBlock[] myAddedBraces;
    private final String myDescriptiveName;
    private Map<PsiField, PsiClassInitializer> myAddedClassInitializers;
    private PsiMethod myMethodCopy;
    private Map<Language, InlineHandler.Inliner> myInliners;

    @RequiredReadAction
    public InlineMethodProcessor(
        @Nonnull Project project,
        @Nonnull PsiMethod method,
        @Nullable PsiJavaCodeReferenceElement reference,
        Editor editor,
        boolean isInlineThisOnly
    ) {
        this(project, method, reference, editor, isInlineThisOnly, false, false);
    }

    @RequiredReadAction
    public InlineMethodProcessor(
        @Nonnull Project project,
        @Nonnull PsiMethod method,
        @Nullable PsiJavaCodeReferenceElement reference,
        Editor editor,
        boolean isInlineThisOnly,
        boolean searchInComments,
        boolean searchForTextOccurrences
    ) {
        super(project);
        myMethod = method;
        myReference = reference;
        myEditor = editor;
        myInlineThisOnly = isInlineThisOnly;
        mySearchInComments = searchInComments;
        mySearchForTextOccurrences = searchForTextOccurrences;

        myManager = PsiManager.getInstance(myProject);
        myFactory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
        myCodeStyleManager = CodeStyleManager.getInstance(myProject);
        myJavaCodeStyle = JavaCodeStyleManager.getInstance(myProject);
        myDescriptiveName = DescriptiveNameUtil.getDescriptiveName(myMethod);
    }

    @Nonnull
    @Override
    protected LocalizeValue getCommandName() {
        return RefactoringLocalize.inlineMethodCommand(myDescriptiveName);
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new InlineViewDescriptor(myMethod);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected UsageInfo[] findUsages() {
        if (myInlineThisOnly) {
            return new UsageInfo[]{new UsageInfo(myReference)};
        }
        Set<UsageInfo> usages = new HashSet<>();
        if (myReference != null) {
            usages.add(new UsageInfo(myReference));
        }
        for (PsiReference reference : ReferencesSearch.search(myMethod)) {
            usages.add(new UsageInfo(reference.getElement()));
        }

        if (mySearchInComments || mySearchForTextOccurrences) {
            NonCodeUsageInfoFactory infoFactory = new NonCodeUsageInfoFactory(myMethod, myMethod.getName()) {
                @Override
                public UsageInfo createUsageInfo(@Nonnull PsiElement usage, int startOffset, int endOffset) {
                    if (PsiTreeUtil.isAncestor(myMethod, usage, false)) {
                        return null;
                    }
                    return super.createUsageInfo(usage, startOffset, endOffset);
                }
            };
            if (mySearchInComments) {
                String stringToSearch =
                    ElementDescriptionUtil.getElementDescription(myMethod, NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS);
                TextOccurrencesUtil.addUsagesInStringsAndComments(myMethod, stringToSearch, usages, infoFactory);
            }

            if (mySearchForTextOccurrences) {
                String stringToSearch = ElementDescriptionUtil.getElementDescription(myMethod, NonCodeSearchDescriptionLocation.NON_JAVA);
                TextOccurrencesUtil
                    .addTextOccurences(myMethod, stringToSearch, GlobalSearchScope.projectScope(myProject), usages, infoFactory);
            }
        }

        return usages.toArray(new UsageInfo[usages.size()]);
    }

    @Override
    protected boolean isPreviewUsages(UsageInfo[] usages) {
        for (UsageInfo usage : usages) {
            if (usage instanceof NonCodeUsageInfo) {
                return true;
            }
        }
        return super.isPreviewUsages(usages);
    }

    @Override
    protected void refreshElements(PsiElement[] elements) {
        boolean condition = elements.length == 1 && elements[0] instanceof PsiMethod;
        LOG.assertTrue(condition);
        myMethod = (PsiMethod) elements[0];
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        if (!myInlineThisOnly && checkReadOnly()) {
            if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, myMethod)) {
                return false;
            }
        }
        UsageInfo[] usagesIn = refUsages.get();
        MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();

        if (!myInlineThisOnly) {
            PsiMethod[] superMethods = myMethod.findSuperMethods();
            for (PsiMethod method : superMethods) {
                String className = Objects.requireNonNull(method.getContainingClass()).getQualifiedName();
                LocalizeValue message = method.isAbstract()
                    ? RefactoringLocalize.inlinedMethodImplementsMethodFrom0(className)
                    : RefactoringLocalize.inlinedMethodOverridesMethodFrom0(className);
                conflicts.putValue(method, message);
            }

            for (UsageInfo info : usagesIn) {
                PsiElement element = info.getElement();
                if (element instanceof PsiDocMethodOrFieldRef memberRef && !PsiTreeUtil.isAncestor(myMethod, memberRef, false)) {
                    conflicts.putValue(memberRef, JavaRefactoringLocalize.inlineMethodUsedInJavadoc());
                }
                if (element instanceof PsiLiteralExpression literal
                    && ContainerUtil.or(literal.getReferences(), JavaLangClassMemberReference.class::isInstance)) {
                    conflicts.putValue(literal, JavaRefactoringLocalize.inlineMethodUsedInReflection());
                }
                if (element instanceof PsiMethodReferenceExpression methodRef) {
                    processSideEffectsInMethodReferenceQualifier(conflicts, methodRef);
                }

                LocalizeValue errorMessage = checkCalledInSuperOrThisExpr(myMethod.getBody(), element);
                if (errorMessage.isNotEmpty()) {
                    conflicts.putValue(element, errorMessage);
                }
            }
        }

        List<PsiReference> refs = convertUsagesToRefs(usagesIn);
        myInliners = GenericInlineHandler.initializeInliners(myMethod, () -> myInlineThisOnly, refs);

        //hack to prevent conflicts 'Cannot inline reference from Java'
        myInliners.put(
            JavaLanguage.INSTANCE,
            new InlineHandler.Inliner() {
                @Nonnull
                @Override
                @RequiredReadAction
                public MultiMap<PsiElement, LocalizeValue> getConflicts(@Nonnull PsiReference reference, @Nonnull PsiElement referenced) {
                    return MultiMap.empty();
                }

                @Override
                @RequiredReadAction
                public void inlineUsage(@Nonnull UsageInfo usage, @Nonnull PsiElement referenced) {
                    if (usage instanceof NonCodeUsageInfo) {
                        return;
                    }

                    throw new UnsupportedOperationException(
                        "usage: " + usage.getClass().getName() +
                            ", referenced: " + referenced.getClass().getName() +
                            ", text: " + referenced.getText()
                    );
                }
            }
        );

        for (PsiReference ref : refs) {
            GenericInlineHandler.collectConflicts(ref, myMethod, myInliners, conflicts);
        }

        PsiReturnStatement[] returnStatements = RefactoringUtil.findReturnStatements(myMethod);
        for (PsiReturnStatement statement : returnStatements) {
            PsiExpression value = statement.getReturnValue();
            if (value != null && !(value instanceof PsiCallExpression)) {
                for (UsageInfo info : usagesIn) {
                    PsiReference reference = info.getReference();
                    if (reference != null) {
                        InlineUtil.TailCallType type = InlineUtil.getTailCallType(reference);
                        if (type == InlineUtil.TailCallType.Simple) {
                            conflicts.putValue(statement, LocalizeValue.localizeTODO("Inlined result would contain parse errors"));
                            break;
                        }
                    }
                }
            }
        }

        addInaccessibleMemberConflicts(myMethod, usagesIn, new ReferencedElementsCollector(), conflicts);

        addInaccessibleSuperCallsConflicts(usagesIn, conflicts);

        return showConflicts(conflicts, usagesIn);
    }

    private static void processSideEffectsInMethodReferenceQualifier(
        @Nonnull MultiMap<PsiElement, LocalizeValue> conflicts,
        @Nonnull PsiMethodReferenceExpression methodRef
    ) {
        PsiExpression qualifierExpression = methodRef.getQualifierExpression();
        if (qualifierExpression != null) {
            List<PsiElement> sideEffects = new ArrayList<>();
            SideEffectChecker.checkSideEffects(qualifierExpression, sideEffects);
            if (!sideEffects.isEmpty()) {
                conflicts.putValue(methodRef, JavaRefactoringLocalize.inlineMethodQualifierUsageSideEffect());
            }
        }
    }

    @RequiredReadAction
    private static List<PsiReference> convertUsagesToRefs(UsageInfo[] usagesIn) {
        List<PsiReference> refs = new ArrayList<>();
        for (UsageInfo info : usagesIn) {
            PsiReference ref = info.getReference();
            if (ref != null) { //ref can be null if it is conflict usage info
                refs.add(ref);
            }
        }
        return refs;
    }

    private boolean checkReadOnly() {
        return myMethod.isWritable() || myMethod instanceof PsiCompiledElement;
    }

    private void addInaccessibleSuperCallsConflicts(final UsageInfo[] usagesIn, final MultiMap<PsiElement, LocalizeValue> conflicts) {
        myMethod.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitClass(@Nonnull PsiClass aClass) {
            }

            @Override
            public void visitAnonymousClass(@Nonnull PsiAnonymousClass aClass) {
            }

            @Override
            @RequiredReadAction
            public void visitSuperExpression(@Nonnull PsiSuperExpression expression) {
                super.visitSuperExpression(expression);
                PsiType type = expression.getType();
                PsiClass superClass = PsiUtil.resolveClassInType(type);
                if (superClass != null) {
                    Set<PsiClass> targetContainingClasses = new HashSet<>();
                    for (UsageInfo info : usagesIn) {
                        PsiElement element = info.getElement();
                        if (element != null) {
                            PsiClass targetContainingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
                            if (targetContainingClass != null
                                && !InheritanceUtil.isInheritorOrSelf(targetContainingClass, superClass, true)) {
                                targetContainingClasses.add(targetContainingClass);
                            }
                        }
                    }
                    if (!targetContainingClasses.isEmpty()) {
                        PsiMethodCallExpression methodCallExpression =
                            PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
                        LOG.assertTrue(methodCallExpression != null);
                        conflicts.putValue(
                            expression,
                            LocalizeValue.localizeTODO(
                                "Inlined method calls " + methodCallExpression.getText() + " which won't be accessed in " +
                                    StringUtil.join(
                                        targetContainingClasses,
                                        psiClass -> RefactoringUIUtil.getDescription(psiClass, false),
                                        ","
                                    )
                            )
                        );
                    }
                }
            }
        });
    }

    @RequiredReadAction
    public static void addInaccessibleMemberConflicts(
        PsiElement element,
        UsageInfo[] usages,
        ReferencedElementsCollector collector,
        MultiMap<PsiElement, LocalizeValue> conflicts
    ) {
        element.accept(collector);
        Map<PsiMember, Set<PsiMember>> containersToReferenced = getInaccessible(collector.myReferencedMembers, usages, element);

        Set<PsiMember> containers = containersToReferenced.keySet();
        for (PsiMember container : containers) {
            Set<PsiMember> referencedInaccessible = containersToReferenced.get(container);
            for (PsiMember referenced : referencedInaccessible) {
                String referencedDescription = RefactoringUIUtil.getDescription(referenced, true);
                String containerDescription = RefactoringUIUtil.getDescription(container, true);
                LocalizeValue message = RefactoringLocalize.zeroThatIsUsedInInlinedMethodIsNotAccessibleFromCallSiteSIn1(
                    referencedDescription,
                    containerDescription
                );
                conflicts.putValue(container, message.capitalize());
            }
        }
    }

    /**
     * Given a set of referencedElements, returns a map from containers (in a sense of ConflictsUtil.getContainer)
     * to subsets of referencedElements that are not accessible from that container
     */
    @RequiredReadAction
    private static Map<PsiMember, Set<PsiMember>> getInaccessible(
        Set<PsiMember> referencedElements,
        UsageInfo[] usages,
        PsiElement elementToInline
    ) {
        Map<PsiMember, Set<PsiMember>> result = new HashMap<>();

        for (UsageInfo usage : usages) {
            PsiElement usageElement = usage.getElement();
            if (usageElement == null) {
                continue;
            }
            PsiElement container = ConflictsUtil.getContainer(usageElement);
            if (!(container instanceof PsiMember memberContainer)) {
                continue;    // usage in import statement
            }
            Set<PsiMember> inaccessibleReferenced = result.get(memberContainer);
            if (inaccessibleReferenced == null) {
                inaccessibleReferenced = new HashSet<>();
                result.put(memberContainer, inaccessibleReferenced);
                for (PsiMember member : referencedElements) {
                    if (PsiTreeUtil.isAncestor(elementToInline, member, false)) {
                        continue;
                    }
                    if (!PsiUtil.isAccessible(usage.getProject(), member, usageElement, null)) {
                        inaccessibleReferenced.add(member);
                    }
                }
            }
        }

        return result;
    }

    @Override
    @RequiredWriteAction
    protected void performRefactoring(@Nonnull UsageInfo[] usages) {
        int col = -1;
        int line = -1;
        if (myEditor != null) {
            col = myEditor.getCaretModel().getLogicalPosition().column;
            line = myEditor.getCaretModel().getLogicalPosition().line;
            LogicalPosition pos = new LogicalPosition(0, 0);
            myEditor.getCaretModel().moveToLogicalPosition(pos);
        }

        LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName());
        try {
            doRefactoring(usages);
        }
        finally {
            a.finish();
        }

        if (myEditor != null) {
            LogicalPosition pos = new LogicalPosition(line, col);
            myEditor.getCaretModel().moveToLogicalPosition(pos);
        }
    }

    @RequiredWriteAction
    private void doRefactoring(UsageInfo[] usages) {
        try {
            if (myInlineThisOnly) {
                if (myMethod.isConstructor() && InlineMethodHandler.isChainingConstructor(myMethod)) {
                    PsiCall constructorCall = RefactoringUtil.getEnclosingConstructorCall(myReference);
                    if (constructorCall != null) {
                        inlineConstructorCall(constructorCall);
                    }
                }
                else {
                    myReference = addBracesWhenNeeded(new PsiReferenceExpression[]{(PsiReferenceExpression) myReference})[0];
                    inlineMethodCall((PsiReferenceExpression) myReference);
                }
            }
            else {
                CommonRefactoringUtil.sortDepthFirstRightLeftOrder(usages);
                if (myMethod.isConstructor()) {
                    for (UsageInfo usage : usages) {
                        PsiElement element = usage.getElement();
                        if (element instanceof PsiJavaCodeReferenceElement codeRefElem) {
                            PsiCall constructorCall = RefactoringUtil.getEnclosingConstructorCall(codeRefElem);
                            if (constructorCall != null) {
                                inlineConstructorCall(constructorCall);
                            }
                        }
                        else if (element instanceof PsiEnumConstant enumConst) {
                            inlineConstructorCall(enumConst);
                        }
                        else {
                            GenericInlineHandler.inlineReference(usage, myMethod, myInliners);
                        }
                    }
                    myMethod.delete();
                }
                else {
                    List<PsiReferenceExpression> refExprList = new ArrayList<>();
                    List<PsiElement> imports2Delete = new ArrayList<>();
                    for (UsageInfo usage : usages) {
                        PsiElement element = usage.getElement();
                        if (element instanceof PsiReferenceExpression refExpr) {
                            refExprList.add(refExpr);
                        }
                        else if (element instanceof PsiImportStaticReferenceElement) {
                            imports2Delete.add(PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class));
                        }
                        else if (JavaLanguage.INSTANCE != element.getLanguage()) {
                            GenericInlineHandler.inlineReference(usage, myMethod, myInliners);
                        }
                    }
                    PsiReferenceExpression[] refs = refExprList.toArray(new PsiReferenceExpression[refExprList.size()]);
                    refs = addBracesWhenNeeded(refs);
                    for (PsiReferenceExpression ref : refs) {
                        if (ref instanceof PsiMethodReferenceExpression) {
                            continue;
                        }
                        inlineMethodCall(ref);
                    }
                    for (PsiElement psiElement : imports2Delete) {
                        if (psiElement != null && psiElement.isValid()) {
                            psiElement.delete();
                        }
                    }
                    if (myMethod.isWritable()) {
                        myMethod.delete();
                    }
                }
            }
            removeAddedBracesWhenPossible();
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @RequiredWriteAction
    public static void inlineConstructorCall(PsiCall constructorCall) {
        PsiMethod oldConstructor = constructorCall.resolveMethod();
        LOG.assertTrue(oldConstructor != null);
        PsiExpression[] instanceCreationArguments = constructorCall.getArgumentList().getExpressions();
        if (oldConstructor.isVarArgs()) { //wrap with explicit array
            PsiParameter[] parameters = oldConstructor.getParameterList().getParameters();
            PsiType varargType = parameters[parameters.length - 1].getType();
            if (varargType instanceof PsiEllipsisType ellipsisType) {
                PsiType arrayType =
                    constructorCall.resolveMethodGenerics().getSubstitutor().substitute(ellipsisType.getComponentType());
                PsiExpression[] exprs = new PsiExpression[parameters.length];
                System.arraycopy(instanceCreationArguments, 0, exprs, 0, parameters.length - 1);
                StringBuilder varargs = new StringBuilder();
                for (int i = parameters.length - 1; i < instanceCreationArguments.length; i++) {
                    if (varargs.length() > 0) {
                        varargs.append(", ");
                    }
                    varargs.append(instanceCreationArguments[i].getText());
                }

                exprs[parameters.length - 1] = JavaPsiFacade.getElementFactory(constructorCall.getProject())
                    .createExpressionFromText("new " + arrayType.getCanonicalText() + "[]{" + varargs.toString() + "}", constructorCall);

                instanceCreationArguments = exprs;
            }
        }

        PsiStatement[] statements = oldConstructor.getBody().getStatements();
        LOG.assertTrue(statements.length == 1 && statements[0] instanceof PsiExpressionStatement exprStmt);
        PsiExpression expression = ((PsiExpressionStatement) statements[0]).getExpression();
        LOG.assertTrue(expression instanceof PsiMethodCallExpression);
        ChangeContextUtil.encodeContextInfo(expression, true);

        PsiMethodCallExpression methodCall = (PsiMethodCallExpression) expression.copy();
        PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        for (PsiExpression arg : args) {
            replaceParameterReferences(arg, oldConstructor, instanceCreationArguments);
        }

        try {
            PsiExpressionList exprList = (PsiExpressionList) constructorCall.getArgumentList().replace(methodCall.getArgumentList());
            ChangeContextUtil.decodeContextInfo(exprList, PsiTreeUtil.getParentOfType(constructorCall, PsiClass.class), null);
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
        ChangeContextUtil.clearContextInfo(expression);
    }

    @RequiredWriteAction
    private static void replaceParameterReferences(
        PsiElement element,
        PsiMethod oldConstructor,
        PsiExpression[] instanceCreationArguments
    ) {
        boolean isParameterReference = false;
        if (element instanceof PsiReferenceExpression expression
            && expression.resolve() instanceof PsiParameter parameter
            && expression.getManager().areElementsEquivalent(parameter.getDeclarationScope(), oldConstructor)) {
            isParameterReference = true;
            PsiElement declarationScope = parameter.getDeclarationScope();
            PsiParameter[] declarationParameters = ((PsiMethod) declarationScope).getParameterList().getParameters();
            for (int j = 0; j < declarationParameters.length; j++) {
                if (declarationParameters[j] == parameter) {
                    try {
                        expression.replace(instanceCreationArguments[j]);
                    }
                    catch (IncorrectOperationException e) {
                        LOG.error(e);
                    }
                }
            }
        }
        if (!isParameterReference) {
            PsiElement child = element.getFirstChild();
            while (child != null) {
                PsiElement next = child.getNextSibling();
                replaceParameterReferences(child, oldConstructor, instanceCreationArguments);
                child = next;
            }
        }
    }

    @RequiredWriteAction
    public void inlineMethodCall(PsiReferenceExpression ref) throws IncorrectOperationException {
        InlineUtil.TailCallType tailCall = InlineUtil.getTailCallType(ref);
        ChangeContextUtil.encodeContextInfo(myMethod, false);
        myMethodCopy = (PsiMethod) myMethod.copy();
        ChangeContextUtil.clearContextInfo(myMethod);

        PsiMethodCallExpression methodCall = (PsiMethodCallExpression) ref.getParent();

        PsiSubstitutor callSubstitutor = getCallSubstitutor(methodCall);
        BlockData blockData = prepareBlock(ref, callSubstitutor, methodCall.getArgumentList(), tailCall);
        solveVariableNameConflicts(blockData.block, ref);
        if (callSubstitutor != PsiSubstitutor.EMPTY) {
            substituteMethodTypeParams(blockData.block, callSubstitutor);
        }
        addParamAndThisVarInitializers(blockData, methodCall);

        PsiElement anchor = RefactoringUtil.getParentStatement(methodCall, true);
        if (anchor == null) {
            PsiEnumConstant enumConstant = PsiTreeUtil.getParentOfType(methodCall, PsiEnumConstant.class);
            if (enumConstant != null) {
                PsiExpression returnExpr = getSimpleReturnedExpression(myMethod);
                if (returnExpr != null) {
                    methodCall.replace(returnExpr);
                }
            }
            return;
        }
        PsiElement anchorParent = anchor.getParent();
        PsiLocalVariable thisVar = null;
        PsiLocalVariable[] paramVars = new PsiLocalVariable[blockData.paramVars.length];
        PsiLocalVariable resultVar = null;
        PsiStatement[] statements = blockData.block.getStatements();
        if (statements.length > 0) {
            int last = statements.length - 1;
            /*PsiElement first = statements[0];
            PsiElement last = statements[statements.length - 1];*/

            if (statements.length > 0 && statements[statements.length - 1] instanceof PsiReturnStatement
                && tailCall != InlineUtil.TailCallType.Return) {
                last--;
            }

            int first = 0;
            if (first <= last) {
                PsiElement rBraceOrReturnStatement =
                    PsiTreeUtil.skipSiblingsForward(statements[last], PsiWhiteSpace.class, PsiComment.class);
                LOG.assertTrue(rBraceOrReturnStatement != null);
                PsiElement beforeRBraceStatement = rBraceOrReturnStatement.getPrevSibling();
                LOG.assertTrue(beforeRBraceStatement != null);
                PsiElement firstAdded = anchorParent.addRangeBefore(statements[first], beforeRBraceStatement, anchor);

                PsiElement current = firstAdded.getPrevSibling();
                LOG.assertTrue(current != null);
                if (blockData.thisVar != null) {
                    PsiDeclarationStatement statement = PsiTreeUtil.getNextSiblingOfType(current, PsiDeclarationStatement.class);
                    thisVar = (PsiLocalVariable) statement.getDeclaredElements()[0];
                    current = statement;
                }
                for (int i = 0; i < paramVars.length; i++) {
                    PsiDeclarationStatement statement = PsiTreeUtil.getNextSiblingOfType(current, PsiDeclarationStatement.class);
                    paramVars[i] = (PsiLocalVariable) statement.getDeclaredElements()[0];
                    current = statement;
                }
                if (blockData.resultVar != null) {
                    PsiDeclarationStatement statement = PsiTreeUtil.getNextSiblingOfType(current, PsiDeclarationStatement.class);
                    resultVar = (PsiLocalVariable) statement.getDeclaredElements()[0];
                }
            }
            if (statements[statements.length - 1] instanceof PsiReturnStatement returnStmt
                && tailCall != InlineUtil.TailCallType.Return) {
                PsiExpression returnValue = returnStmt.getReturnValue();
                if (returnValue != null && PsiUtil.isStatement(returnValue)) {
                    PsiExpressionStatement exprStatement = (PsiExpressionStatement) myFactory.createStatementFromText("a;", null);
                    exprStatement.getExpression().replace(returnValue);
                    anchorParent.addBefore(exprStatement, anchor);
                }
            }
        }

        PsiClass thisClass = myMethod.getContainingClass();
        PsiExpression thisAccessExpr;
        if (thisVar != null) {
            if (!canInlineParamOrThisVariable(thisVar)) {
                thisAccessExpr = myFactory.createExpressionFromText(thisVar.getName(), null);
            }
            else {
                thisAccessExpr = thisVar.getInitializer();
            }
        }
        else {
            thisAccessExpr = null;
        }
        ChangeContextUtil.decodeContextInfo(anchorParent, thisClass, thisAccessExpr);

        if (methodCall.getParent() instanceof PsiLambdaExpression) {
            methodCall.delete();
        }
        else if (methodCall.getParent() instanceof PsiExpressionStatement || tailCall == InlineUtil.TailCallType.Return) {
            methodCall.getParent().delete();
        }
        else if (blockData.resultVar != null) {
            PsiExpression expr = myFactory.createExpressionFromText(blockData.resultVar.getName(), null);
            methodCall.replace(expr);
        }
        else {
            //??
        }

        if (thisVar != null) {
            inlineParamOrThisVariable(thisVar, false);
        }
        PsiParameter[] parameters = myMethod.getParameterList().getParameters();
        for (int i = 0; i < paramVars.length; i++) {
            PsiParameter parameter = parameters[i];
            boolean strictlyFinal = parameter.hasModifierProperty(PsiModifier.FINAL) && isStrictlyFinal(parameter);
            inlineParamOrThisVariable(paramVars[i], strictlyFinal);
        }
        if (resultVar != null) {
            inlineResultVariable(resultVar);
        }

        ChangeContextUtil.clearContextInfo(anchorParent);
    }

    private PsiSubstitutor getCallSubstitutor(PsiMethodCallExpression methodCall) {
        JavaResolveResult resolveResult = methodCall.getMethodExpression().advancedResolve(false);
        LOG.assertTrue(myManager.areElementsEquivalent(resolveResult.getElement(), myMethod));
        if (resolveResult.getSubstitutor() != PsiSubstitutor.EMPTY) {
            Iterator<PsiTypeParameter> oldTypeParameters = PsiUtil.typeParametersIterator(myMethod);
            Iterator<PsiTypeParameter> newTypeParameters = PsiUtil.typeParametersIterator(myMethodCopy);
            PsiSubstitutor substitutor = resolveResult.getSubstitutor();
            while (newTypeParameters.hasNext()) {
                PsiTypeParameter newTypeParameter = newTypeParameters.next();
                PsiTypeParameter oldTypeParameter = oldTypeParameters.next();
                substitutor = substitutor.put(newTypeParameter, resolveResult.getSubstitutor().substitute(oldTypeParameter));
            }
            return substitutor;
        }

        return PsiSubstitutor.EMPTY;
    }

    private void substituteMethodTypeParams(PsiElement scope, PsiSubstitutor substitutor) {
        InlineUtil.substituteTypeParams(scope, substitutor, myFactory);
    }

    @RequiredReadAction
    private boolean isStrictlyFinal(PsiParameter parameter) {
        for (PsiReference reference : ReferencesSearch.search(parameter, GlobalSearchScope.projectScope(myProject), false)) {
            PsiElement refElement = reference.getElement();
            PsiElement anonymousClass = PsiTreeUtil.getParentOfType(refElement, PsiAnonymousClass.class);
            if (anonymousClass != null && PsiTreeUtil.isAncestor(myMethod, anonymousClass, true)) {
                return true;
            }
        }
        return false;
    }

    private boolean syncNeeded(PsiReferenceExpression ref) {
        if (!myMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
            return false;
        }
        PsiMethod containingMethod = Util.getContainingMethod(ref);
        if (containingMethod == null) {
            return true;
        }
        if (!containingMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
            return true;
        }
        PsiClass sourceContainingClass = myMethod.getContainingClass();
        PsiClass targetContainingClass = containingMethod.getContainingClass();
        return !sourceContainingClass.equals(targetContainingClass);
    }

    @RequiredWriteAction
    private BlockData prepareBlock(
        PsiReferenceExpression ref,
        PsiSubstitutor callSubstitutor,
        PsiExpressionList argumentList,
        InlineUtil.TailCallType tailCallType
    ) throws IncorrectOperationException {
        PsiCodeBlock block = myMethodCopy.getBody();
        PsiStatement[] originalStatements = block.getStatements();

        PsiLocalVariable resultVar = null;
        PsiType returnType = callSubstitutor.substitute(myMethod.getReturnType());
        String resultName = null;
        int applicabilityLevel = PsiUtil.getApplicabilityLevel(myMethod, callSubstitutor, argumentList);
        if (returnType != null && !PsiType.VOID.equals(returnType) && tailCallType == InlineUtil.TailCallType.None) {
            resultName = myJavaCodeStyle.propertyNameToVariableName("result", VariableKind.LOCAL_VARIABLE);
            resultName = myJavaCodeStyle.suggestUniqueVariableName(resultName, block.getFirstChild(), true);
            PsiDeclarationStatement declaration = myFactory.createVariableDeclarationStatement(resultName, returnType, null);
            declaration = (PsiDeclarationStatement) block.addAfter(declaration, null);
            resultVar = (PsiLocalVariable) declaration.getDeclaredElements()[0];
        }

        PsiParameter[] params = myMethodCopy.getParameterList().getParameters();
        PsiLocalVariable[] paramVars = new PsiLocalVariable[params.length];
        for (int i = params.length - 1; i >= 0; i--) {
            PsiParameter param = params[i];
            String paramName = param.getName();
            String name = paramName;
            name = myJavaCodeStyle.variableNameToPropertyName(name, VariableKind.PARAMETER);
            name = myJavaCodeStyle.propertyNameToVariableName(name, VariableKind.LOCAL_VARIABLE);
            if (!name.equals(paramName)) {
                name = myJavaCodeStyle.suggestUniqueVariableName(name, block.getFirstChild(), true);
            }
            RefactoringUtil.renameVariableReferences(param, name, new LocalSearchScope(myMethodCopy.getBody()), true);
            PsiType paramType = param.getType();
            String defaultValue;
            if (paramType instanceof PsiEllipsisType ellipsisType) {
                paramType = callSubstitutor.substitute(ellipsisType.toArrayType());
                if (applicabilityLevel == MethodCandidateInfo.ApplicabilityLevel.VARARGS) {
                    defaultValue = "new " + ellipsisType.getComponentType().getCanonicalText() + "[]{}";
                }
                else {
                    defaultValue = PsiTypesUtil.getDefaultValueOfType(paramType);
                }
            }
            else {
                defaultValue = PsiTypesUtil.getDefaultValueOfType(paramType);
            }

            PsiExpression initializer = myFactory.createExpressionFromText(defaultValue, null);
            PsiDeclarationStatement declaration =
                myFactory.createVariableDeclarationStatement(name, callSubstitutor.substitute(paramType), initializer);
            declaration = (PsiDeclarationStatement) block.addAfter(declaration, null);
            paramVars[i] = (PsiLocalVariable) declaration.getDeclaredElements()[0];
            PsiUtil.setModifierProperty(paramVars[i], PsiModifier.FINAL, param.hasModifierProperty(PsiModifier.FINAL));
        }

        PsiLocalVariable thisVar = null;
        PsiClass containingClass = myMethod.getContainingClass();
        if (!myMethod.isStatic() && containingClass != null) {
            PsiType thisType = myFactory.createType(containingClass, callSubstitutor);
            String[] names = myJavaCodeStyle.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, thisType)
                .names;
            String thisVarName = names[0];
            thisVarName = myJavaCodeStyle.suggestUniqueVariableName(thisVarName, block.getFirstChild(), true);
            PsiExpression initializer = myFactory.createExpressionFromText("null", null);
            PsiDeclarationStatement declaration = myFactory.createVariableDeclarationStatement(thisVarName, thisType, initializer);
            declaration = (PsiDeclarationStatement) block.addAfter(declaration, null);
            thisVar = (PsiLocalVariable) declaration.getDeclaredElements()[0];
        }

        String lockName = null;
        if (thisVar != null) {
            lockName = thisVar.getName();
        }
        else if (myMethod.isStatic() && containingClass != null) {
            lockName = containingClass.getQualifiedName() + ".class";
        }

        if (lockName != null && syncNeeded(ref)) {
            PsiSynchronizedStatement synchronizedStatement =
                (PsiSynchronizedStatement) myFactory.createStatementFromText("synchronized(" + lockName + "){}", block);
            synchronizedStatement = (PsiSynchronizedStatement) CodeStyleManager.getInstance(myProject).reformat(synchronizedStatement);
            synchronizedStatement = (PsiSynchronizedStatement) block.add(synchronizedStatement);
            PsiCodeBlock synchronizedBody = synchronizedStatement.getBody();
            for (PsiStatement originalStatement : originalStatements) {
                synchronizedBody.add(originalStatement);
                originalStatement.delete();
            }
        }

        if (resultName != null || tailCallType == InlineUtil.TailCallType.Simple) {
            PsiReturnStatement[] returnStatements = RefactoringUtil.findReturnStatements(myMethodCopy);
            for (PsiReturnStatement returnStatement : returnStatements) {
                PsiExpression returnValue = returnStatement.getReturnValue();
                if (returnValue == null) {
                    continue;
                }
                PsiStatement statement;
                if (tailCallType == InlineUtil.TailCallType.Simple) {
                    if (returnValue instanceof PsiExpression
                        && returnStatement.getNextSibling() == myMethodCopy.getBody().getLastBodyElement()) {
                        PsiExpressionStatement exprStatement = (PsiExpressionStatement) myFactory.createStatementFromText("a;", null);
                        exprStatement.getExpression().replace(returnValue);
                        returnStatement.getParent().addBefore(exprStatement, returnStatement);
                        statement = myFactory.createStatementFromText("return;", null);
                    }
                    else {
                        statement = (PsiStatement) returnStatement.copy();
                    }
                }
                else {
                    statement = myFactory.createStatementFromText(resultName + "=0;", null);
                    statement = (PsiStatement) myCodeStyleManager.reformat(statement);
                    PsiAssignmentExpression assignment = (PsiAssignmentExpression) ((PsiExpressionStatement) statement).getExpression();
                    assignment.getRExpression().replace(returnValue);
                }
                returnStatement.replace(statement);
            }
        }

        return new BlockData(block, thisVar, paramVars, resultVar);
    }

    @RequiredWriteAction
    private void solveVariableNameConflicts(PsiElement scope, PsiElement placeToInsert) throws IncorrectOperationException {
        if (scope instanceof PsiVariable variable) {
            String name = variable.getName();
            String oldName = name;
            while (true) {
                String newName = myJavaCodeStyle.suggestUniqueVariableName(name, placeToInsert, true);
                if (newName.equals(name)) {
                    break;
                }
                name = newName;
                newName = myJavaCodeStyle.suggestUniqueVariableName(name, variable, true);
                if (newName.equals(name)) {
                    break;
                }
                name = newName;
            }
            if (!name.equals(oldName)) {
                RefactoringUtil.renameVariableReferences(variable, name, new LocalSearchScope(myMethodCopy.getBody()), true);
                variable.getNameIdentifier().replace(myFactory.createIdentifier(name));
            }
        }

        PsiElement[] children = scope.getChildren();
        for (PsiElement child : children) {
            solveVariableNameConflicts(child, placeToInsert);
        }
    }

    @RequiredWriteAction
    private void addParamAndThisVarInitializers(
        BlockData blockData,
        PsiMethodCallExpression methodCall
    ) throws IncorrectOperationException {
        PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        if (blockData.paramVars.length > 0) {
            for (int i = 0; i < args.length; i++) {
                int j = Math.min(i, blockData.paramVars.length - 1);
                PsiExpression initializer = blockData.paramVars[j].getInitializer();
                LOG.assertTrue(initializer != null);
                if (initializer instanceof PsiNewExpression newExpr && newExpr.getArrayInitializer() != null) {
                    //varargs initializer
                    PsiArrayInitializerExpression arrayInitializer = newExpr.getArrayInitializer();
                    arrayInitializer.add(args[i]);
                    continue;
                }

                initializer.replace(args[i]);
            }
        }

        if (blockData.thisVar != null) {
            PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
            if (qualifier == null) {
                PsiElement parent = methodCall.getContext();
                while (true) {
                    if (parent instanceof PsiClass || parent instanceof PsiFile) {
                        break;
                    }
                    assert parent != null : methodCall;
                    parent = parent.getContext();
                }
                if (parent instanceof PsiClass parentClass) {
                    PsiClass containingClass = myMethod.getContainingClass();
                    if (InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
                        qualifier = myFactory.createExpressionFromText("this", null);
                    }
                    else {
                        if (PsiTreeUtil.isAncestor(containingClass, parent, false)) {
                            String name = containingClass.getName();
                            if (name != null) {
                                qualifier = myFactory.createExpressionFromText(name + ".this", null);
                            }
                            else { //?
                                qualifier = myFactory.createExpressionFromText("this", null);
                            }
                        }
                        else { // we are inside the inheritor
                            do {
                                parentClass = PsiTreeUtil.getParentOfType(parentClass, PsiClass.class, true);
                                if (InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
                                    String childClassName = parentClass.getName();
                                    qualifier = myFactory.createExpressionFromText(
                                        childClassName != null ? childClassName + ".this" : "this",
                                        null
                                    );
                                    break;
                                }
                            }
                            while (parentClass != null);
                        }
                    }
                }
                else {
                    qualifier = myFactory.createExpressionFromText("this", null);
                }
            }
            else if (qualifier instanceof PsiSuperExpression) {
                qualifier = myFactory.createExpressionFromText("this", null);
            }
            blockData.thisVar.getInitializer().replace(qualifier);
        }
    }

    @RequiredReadAction
    private boolean canInlineParamOrThisVariable(PsiLocalVariable variable) {
        boolean isAccessedForWriting = false;
        for (PsiReference ref : ReferencesSearch.search(variable)) {
            if (ref.getElement() instanceof PsiExpression expression && PsiUtil.isAccessedForWriting(expression)) {
                isAccessedForWriting = true;
            }
        }

        PsiExpression initializer = variable.getInitializer();
        boolean shouldBeFinal = variable.hasModifierProperty(PsiModifier.FINAL) && false;
        return canInlineParamOrThisVariable(
            initializer,
            shouldBeFinal,
            false,
            ReferencesSearch.search(variable).findAll().size(),
            isAccessedForWriting
        );
    }

    @RequiredWriteAction
    private void inlineParamOrThisVariable(PsiLocalVariable variable, boolean strictlyFinal) throws IncorrectOperationException {
        PsiReference firstRef = ReferencesSearch.search(variable).findFirst();

        if (firstRef == null) {
            variable.getParent().delete(); //Q: side effects?
            return;
        }


        boolean isAccessedForWriting = false;
        Collection<PsiReference> refs = ReferencesSearch.search(variable).findAll();
        for (PsiReference ref : refs) {
            if (ref.getElement() instanceof PsiExpression expression && PsiUtil.isAccessedForWriting(expression)) {
                isAccessedForWriting = true;
            }
        }

        PsiExpression initializer = variable.getInitializer();
        boolean shouldBeFinal = variable.hasModifierProperty(PsiModifier.FINAL) && strictlyFinal;
        if (canInlineParamOrThisVariable(initializer, shouldBeFinal, strictlyFinal, refs.size(), isAccessedForWriting)) {
            if (shouldBeFinal) {
                declareUsedLocalsFinal(initializer, strictlyFinal);
            }
            for (PsiReference ref : refs) {
                PsiJavaCodeReferenceElement javaRef = (PsiJavaCodeReferenceElement) ref;
                if (initializer instanceof PsiThisExpression thisExpr && thisExpr.getQualifier() == null) {
                    PsiClass varThisClass = RefactoringChangeUtil.getThisClass(variable);
                    if (RefactoringChangeUtil.getThisClass(javaRef) != varThisClass) {
                        initializer = JavaPsiFacade.getInstance(myManager.getProject())
                            .getElementFactory()
                            .createExpressionFromText(varThisClass.getName() + ".this", variable);
                    }
                }

                PsiExpression expr = InlineUtil.inlineVariable(variable, initializer, javaRef);

                InlineUtil.tryToInlineArrayCreationForVarargs(expr);

                //Q: move the following code to some util? (addition to inline?)
                if (expr instanceof PsiThisExpression && expr.getParent() instanceof PsiReferenceExpression refExpr) {
                    PsiElement refElement = refExpr.resolve();
                    PsiExpression exprCopy = (PsiExpression) refExpr.copy();
                    refExpr =
                        (PsiReferenceExpression) refExpr.replace(myFactory.createExpressionFromText(refExpr.getReferenceName(), null));
                    if (refElement != null) {
                        PsiElement newRefElement = refExpr.resolve();
                        if (!refElement.equals(newRefElement)) {
                            // change back
                            refExpr.replace(exprCopy);
                        }
                    }
                }
            }
            variable.getParent().delete();
        }
    }

    @RequiredReadAction
    private boolean canInlineParamOrThisVariable(
        PsiExpression initializer,
        boolean shouldBeFinal,
        boolean strictlyFinal,
        int accessCount,
        boolean isAccessedForWriting
    ) {
        if (strictlyFinal) {
            class CanAllLocalsBeDeclaredFinal extends JavaRecursiveElementWalkingVisitor {
                boolean success = true;

                @Override
                @RequiredReadAction
                public void visitReferenceExpression(PsiReferenceExpression expression) {
                    PsiElement psiElement = expression.resolve();
                    if (psiElement instanceof PsiLocalVariable || psiElement instanceof PsiParameter) {
                        if (!RefactoringUtil.canBeDeclaredFinal((PsiVariable) psiElement)) {
                            success = false;
                        }
                    }
                }

                @Override
                public void visitElement(PsiElement element) {
                    if (success) {
                        super.visitElement(element);
                    }
                }
            }

            CanAllLocalsBeDeclaredFinal canAllLocalsBeDeclaredFinal = new CanAllLocalsBeDeclaredFinal();
            initializer.accept(canAllLocalsBeDeclaredFinal);
            if (!canAllLocalsBeDeclaredFinal.success) {
                return false;
            }
        }
        if (initializer instanceof PsiMethodReferenceExpression) {
            return true;
        }
        if (initializer instanceof PsiReferenceExpression refExpr) {
            PsiVariable refVar = (PsiVariable) refExpr.resolve();
            if (refVar == null) {
                return !isAccessedForWriting;
            }
            if (refVar instanceof PsiField) {
                if (isAccessedForWriting) {
                    return false;
                }
                /*
                PsiField field = (PsiField)refVar;
                if (isFieldNonModifiable(field)){
                    return true;
                }
                //TODO: other cases
                return false;
                */
                return true; //TODO: "suspicious" places to review by user!
            }
            else {
                if (isAccessedForWriting) {
                    if (refVar.hasModifierProperty(PsiModifier.FINAL) || shouldBeFinal) {
                        return false;
                    }
                    PsiReference[] refs =
                        ReferencesSearch.search(refVar, GlobalSearchScope.projectScope(myProject), false).toArray(new PsiReference[0]);
                    return refs.length == 1; //TODO: control flow
                }
                else {
                    return !shouldBeFinal || refVar.hasModifierProperty(PsiModifier.FINAL) || RefactoringUtil.canBeDeclaredFinal(refVar);
                }
            }
        }
        else if (isAccessedForWriting) {
            return false;
        }
        else if (initializer instanceof PsiCallExpression) {
            if (accessCount > 1) {
                return false;
            }
            if (initializer instanceof PsiNewExpression newExpr) {
                PsiArrayInitializerExpression arrayInitializer = newExpr.getArrayInitializer();
                if (arrayInitializer != null) {
                    for (PsiExpression expression : arrayInitializer.getInitializers()) {
                        if (!canInlineParamOrThisVariable(expression, shouldBeFinal, strictlyFinal, accessCount, false)) {
                            return false;
                        }
                    }
                    return true;
                }
            }
            PsiExpressionList argumentList = ((PsiCallExpression) initializer).getArgumentList();
            if (argumentList == null) {
                return false;
            }
            PsiExpression[] expressions = argumentList.getExpressions();
            for (PsiExpression expression : expressions) {
                if (!canInlineParamOrThisVariable(expression, shouldBeFinal, strictlyFinal, accessCount, false)) {
                    return false;
                }
            }
            return true; //TODO: "suspicious" places to review by user!
        }
        else if (initializer instanceof PsiLiteralExpression) {
            return true;
        }
        else if (initializer instanceof PsiArrayAccessExpression arrayAccess) {
            PsiExpression arrayExpression = arrayAccess.getArrayExpression();
            PsiExpression indexExpression = arrayAccess.getIndexExpression();
            return canInlineParamOrThisVariable(arrayExpression, shouldBeFinal, strictlyFinal, accessCount, false)
                && canInlineParamOrThisVariable(indexExpression, shouldBeFinal, strictlyFinal, accessCount, false);
        }
        else if (initializer instanceof PsiParenthesizedExpression parenthesized) {
            PsiExpression expr = parenthesized.getExpression();
            return expr == null || canInlineParamOrThisVariable(expr, shouldBeFinal, strictlyFinal, accessCount, false);
        }
        else if (initializer instanceof PsiTypeCastExpression typeCast) {
            PsiExpression operand = typeCast.getOperand();
            return operand != null && canInlineParamOrThisVariable(operand, shouldBeFinal, strictlyFinal, accessCount, false);
        }
        else if (initializer instanceof PsiPolyadicExpression binExpr) {
            for (PsiExpression op : binExpr.getOperands()) {
                if (!canInlineParamOrThisVariable(op, shouldBeFinal, strictlyFinal, accessCount, false)) {
                    return false;
                }
            }
            return true;
        }
        else if (initializer instanceof PsiClassObjectAccessExpression) {
            return true;
        }
        else if (initializer instanceof PsiThisExpression) {
            return true;
        }
        else if (initializer instanceof PsiSuperExpression) {
            return true;
        }
        else {
            return false;
        }
    }

    @RequiredWriteAction
    private static void declareUsedLocalsFinal(PsiElement expr, boolean strictlyFinal) throws IncorrectOperationException {
        if (expr instanceof PsiReferenceExpression refExpr && refExpr.resolve() instanceof PsiVariable variable
            && (variable instanceof PsiLocalVariable || variable instanceof PsiParameter)
            && (strictlyFinal || RefactoringUtil.canBeDeclaredFinal(variable))) {
            PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, true);
        }
        PsiElement[] children = expr.getChildren();
        for (PsiElement child : children) {
            declareUsedLocalsFinal(child, strictlyFinal);
        }
    }

    /*
    private boolean isFieldNonModifiable(PsiField field) {
        if (field.hasModifierProperty(PsiModifier.FINAL)){
            return true;
        }
        PsiElement[] refs = myManager.getSearchHelper().findReferences(field, null, false);
        for(int i = 0; i < refs.length; i++){
            PsiReferenceExpression ref = (PsiReferenceExpression)refs[i];
            if (PsiUtil.isAccessedForWriting(ref)) {
                PsiElement container = ref.getParent();
                while(true){
                    if (container instanceof PsiMethod
                        || container instanceof PsiField
                        || container instanceof PsiClassInitializer
                        || container instanceof PsiFile) {
                        break;
                    }
                    container = container.getParent();
                }
                if (container instanceof PsiMethod method && method.isConstructor()) continue;
                return false;
            }
        }
        return true;
    }
    */

    @RequiredWriteAction
    private void inlineResultVariable(PsiVariable resultVar) throws IncorrectOperationException {
        PsiAssignmentExpression assignment = null;
        PsiReferenceExpression resultUsage = null;
        for (PsiReference ref1 : ReferencesSearch.search(resultVar, GlobalSearchScope.projectScope(myProject), false)) {
            PsiReferenceExpression ref = (PsiReferenceExpression) ref1;
            if (ref.getParent() instanceof PsiAssignmentExpression assignmentExpr
                && assignmentExpr.getLExpression().equals(ref)) {
                if (assignment != null) {
                    assignment = null;
                    break;
                }
                else {
                    assignment = assignmentExpr;
                }
            }
            else {
                LOG.assertTrue(resultUsage == null, "old:" + resultUsage + "; new:" + ref);
                resultUsage = ref;
            }
        }

        if (assignment == null) {
            return;
        }
        boolean condition = assignment.getParent() instanceof PsiExpressionStatement;
        LOG.assertTrue(condition);
        // SCR3175 fixed: inline only if declaration and assignment is in the same code block.
        if (!(assignment.getParent().getParent() == resultVar.getParent().getParent())) {
            return;
        }
        if (resultUsage != null) {
            String name = resultVar.getName();
            PsiDeclarationStatement declaration =
                myFactory.createVariableDeclarationStatement(name, resultVar.getType(), assignment.getRExpression());
            declaration = (PsiDeclarationStatement) assignment.getParent().replace(declaration);
            resultVar.getParent().delete();
            resultVar = (PsiVariable) declaration.getDeclaredElements()[0];

            PsiElement parentStatement = RefactoringUtil.getParentStatement(resultUsage, true);
            PsiElement next = declaration.getNextSibling();
            boolean canInline = false;
            while (true) {
                if (next == null) {
                    break;
                }
                if (parentStatement.equals(next)) {
                    canInline = true;
                    break;
                }
                if (next instanceof PsiStatement) {
                    break;
                }
                next = next.getNextSibling();
            }

            if (canInline) {
                InlineUtil.inlineVariable(resultVar, resultVar.getInitializer(), resultUsage);
                declaration.delete();
            }
        }
        else {
            PsiExpression rExpression = assignment.getRExpression();
            while (rExpression instanceof PsiReferenceExpression refExpr) {
                rExpression = refExpr.getQualifierExpression();
            }
            if (rExpression == null || !PsiUtil.isStatement(rExpression)) {
                assignment.delete();
            }
            else {
                assignment.replace(rExpression);
            }
            resultVar.delete();
        }
    }

    private static final Key<String> MARK_KEY = Key.create("");

    @RequiredWriteAction
    private PsiReferenceExpression[] addBracesWhenNeeded(PsiReferenceExpression[] refs) throws IncorrectOperationException {
        List<PsiReferenceExpression> refsVector = new ArrayList<>();
        List<PsiCodeBlock> addedBracesVector = new ArrayList<>();
        myAddedClassInitializers = new HashMap<>();

        for (PsiReferenceExpression ref : refs) {
            ref.putCopyableUserData(MARK_KEY, "");
        }

        RefLoop:
        for (PsiReferenceExpression ref : refs) {
            if (!ref.isValid()) {
                continue;
            }

            PsiElement parentStatement = RefactoringUtil.getParentStatement(ref, true);
            if (parentStatement != null) {
                PsiElement parent = ref.getParent();
                while (!parent.equals(parentStatement)) {
                    if (parent instanceof PsiStatement && !(parent instanceof PsiDeclarationStatement)) {
                        String text = "{\n}";
                        PsiBlockStatement blockStatement = (PsiBlockStatement) myFactory.createStatementFromText(text, null);
                        blockStatement = (PsiBlockStatement) myCodeStyleManager.reformat(blockStatement);
                        blockStatement.getCodeBlock().add(parent);
                        blockStatement = (PsiBlockStatement) parent.replace(blockStatement);

                        PsiElement newStatement = blockStatement.getCodeBlock().getStatements()[0];
                        addMarkedElements(refsVector, newStatement);
                        addedBracesVector.add(blockStatement.getCodeBlock());
                        continue RefLoop;
                    }
                    parent = parent.getParent();
                }
                if (parentStatement.getParent() instanceof PsiLambdaExpression lambda) {
                    PsiLambdaExpression newLambdaExpr = (PsiLambdaExpression) myFactory.createExpressionFromText(
                        lambda.getParameterList().getText() + " -> " + "{\n}",
                        lambda
                    );
                    PsiStatement statementFromText;
                    if (PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType(lambda))) {
                        statementFromText = myFactory.createStatementFromText("a;", lambda);
                        ((PsiExpressionStatement) statementFromText).getExpression().replace(parentStatement);
                    }
                    else {
                        statementFromText = myFactory.createStatementFromText("return a;", lambda);
                        ((PsiReturnStatement) statementFromText).getReturnValue().replace(parentStatement);
                    }

                    newLambdaExpr.getBody().add(statementFromText);

                    PsiCodeBlock body = (PsiCodeBlock) ((PsiLambdaExpression) lambda.replace(newLambdaExpr)).getBody();
                    PsiElement newStatement = body.getStatements()[0];
                    addMarkedElements(refsVector, newStatement);
                    addedBracesVector.add(body);
                    continue;
                }
            }
            else {
                PsiField field = PsiTreeUtil.getParentOfType(ref, PsiField.class);
                if (field != null) {
                    if (field instanceof PsiEnumConstant) {
                        inlineEnumConstantParameter(refsVector, ref);
                        continue;
                    }
                    field.normalizeDeclaration();
                    PsiExpression initializer = field.getInitializer();
                    LOG.assertTrue(initializer != null);
                    PsiClassInitializer classInitializer = myFactory.createClassInitializer();
                    PsiClass containingClass = field.getContainingClass();
                    classInitializer = (PsiClassInitializer) containingClass.addAfter(classInitializer, field);
                    containingClass.addAfter(CodeEditUtil.createLineFeed(field.getManager()), field);
                    PsiCodeBlock body = classInitializer.getBody();
                    PsiExpressionStatement statement =
                        (PsiExpressionStatement) myFactory.createStatementFromText(field.getName() + " = 0;", body);
                    statement = (PsiExpressionStatement) body.add(statement);
                    PsiAssignmentExpression assignment = (PsiAssignmentExpression) statement.getExpression();
                    assignment.getLExpression().replace(RenameJavaVariableProcessor.createMemberReference(field, assignment));
                    assignment.getRExpression().replace(initializer);
                    addMarkedElements(refsVector, statement);
                    if (field.isStatic()) {
                        PsiUtil.setModifierProperty(classInitializer, PsiModifier.STATIC, true);
                    }
                    myAddedClassInitializers.put(field, classInitializer);
                    continue;
                }
            }

            refsVector.add(ref);
        }

        for (PsiReferenceExpression ref : refs) {
            ref.putCopyableUserData(MARK_KEY, null);
        }

        myAddedBraces = addedBracesVector.toArray(new PsiCodeBlock[addedBracesVector.size()]);
        return refsVector.toArray(new PsiReferenceExpression[refsVector.size()]);
    }

    @RequiredWriteAction
    private void inlineEnumConstantParameter(
        final List<PsiReferenceExpression> refsVector,
        PsiReferenceExpression ref
    ) throws IncorrectOperationException {
        PsiExpression expr = getSimpleReturnedExpression(myMethod);
        if (expr != null) {
            refsVector.add(ref);
        }
        else {
            PsiCall call = PsiTreeUtil.getParentOfType(ref, PsiCall.class);
            String text =
                "new Object() { " + myMethod.getReturnTypeElement().getText() + " evaluate() { return " + call.getText() + ";}}.evaluate";
            PsiExpression callExpr = JavaPsiFacade.getInstance(myProject).getParserFacade().createExpressionFromText(text, call);
            PsiElement classExpr = ref.replace(callExpr);
            classExpr.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
                    super.visitReturnStatement(statement);
                    if (statement.getReturnValue() instanceof PsiMethodCallExpression methodCall) {
                        refsVector.add(methodCall.getMethodExpression());
                    }
                }
            });
            if (classExpr.getParent() instanceof PsiMethodCallExpression methodCall) {
                PsiExpressionList args = methodCall.getArgumentList();
                PsiExpression[] argExpressions = args.getExpressions();
                if (argExpressions.length > 0) {
                    args.deleteChildRange(argExpressions[0], argExpressions[argExpressions.length - 1]);
                }
            }
        }
    }

    @Nullable
    private static PsiExpression getSimpleReturnedExpression(PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return null;
        }
        PsiStatement[] psiStatements = body.getStatements();
        if (psiStatements.length != 1) {
            return null;
        }
        return psiStatements[0] instanceof PsiReturnStatement returnStmt ? returnStmt.getReturnValue() : null;
    }

    private static void addMarkedElements(final List<PsiReferenceExpression> array, PsiElement scope) {
        scope.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if (element.getCopyableUserData(MARK_KEY) != null) {
                    array.add((PsiReferenceExpression) element);
                    element.putCopyableUserData(MARK_KEY, null);
                }
                super.visitElement(element);
            }
        });
    }

    @RequiredWriteAction
    private void removeAddedBracesWhenPossible() throws IncorrectOperationException {
        if (myAddedBraces == null) {
            return;
        }

        for (PsiCodeBlock codeBlock : myAddedBraces) {
            PsiStatement[] statements = codeBlock.getStatements();
            if (statements.length == 1) {
                PsiElement codeBlockParent = codeBlock.getParent();
                if (codeBlockParent instanceof PsiLambdaExpression) {
                    if (statements[0] instanceof PsiReturnStatement returnStmt) {
                        PsiExpression returnValue = returnStmt.getReturnValue();
                        if (returnValue != null) {
                            codeBlock.replace(returnValue);
                        }
                    }
                    else if (statements[0] instanceof PsiExpressionStatement expression) {
                        codeBlock.replace(expression.getExpression());
                    }
                }
                else if (codeBlockParent instanceof PsiBlockStatement) {
                    codeBlockParent.replace(statements[0]);
                }
                else {
                    codeBlock.replace(statements[0]);
                }
            }
        }

        Set<PsiField> fields = myAddedClassInitializers.keySet();

        for (PsiField psiField : fields) {
            PsiClassInitializer classInitializer = myAddedClassInitializers.get(psiField);
            PsiExpression initializer = getSimpleFieldInitializer(psiField, classInitializer);
            if (initializer != null) {
                psiField.getInitializer().replace(initializer);
                classInitializer.delete();
            }
            else {
                psiField.getInitializer().delete();
            }
        }
    }

    @Nullable
    @RequiredReadAction
    private PsiExpression getSimpleFieldInitializer(PsiField field, PsiClassInitializer initializer) {
        PsiStatement[] statements = initializer.getBody().getStatements();
        return statements.length == 1
            && statements[0] instanceof PsiExpressionStatement expressionStmt
            && expressionStmt.getExpression() instanceof PsiAssignmentExpression assignment
            && assignment.getLExpression() instanceof PsiReferenceExpression lExpression
            && myManager.areElementsEquivalent(field, lExpression.resolve()) ? assignment.getRExpression() : null;
    }

    @Nonnull
    public static LocalizeValue checkCalledInSuperOrThisExpr(PsiCodeBlock methodBody, PsiElement element) {
        if (methodBody.getStatements().length > 1) {
            PsiExpression expr = PsiTreeUtil.getParentOfType(element, PsiExpression.class);
            while (expr != null) {
                if (RefactoringChangeUtil.isSuperOrThisMethodCall(expr)) {
                    return JavaRefactoringLocalize.inlineMethodMultilineMethodInCtorCall();
                }
                expr = PsiTreeUtil.getParentOfType(expr, PsiExpression.class, true);
            }
        }
        return LocalizeValue.empty();
    }

    public static boolean checkBadReturns(PsiMethod method) {
        PsiReturnStatement[] returns = RefactoringUtil.findReturnStatements(method);
        if (returns.length == 0) {
            return false;
        }
        PsiCodeBlock body = method.getBody();
        ControlFlow controlFlow;
        try {
            controlFlow = ControlFlowFactory.getInstance(body.getProject()).getControlFlow(body, new LocalsControlFlowPolicy(body), false);
        }
        catch (AnalysisCanceledException e) {
            return false;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Control flow:");
            LOG.debug(controlFlow.toString());
        }

        List<Instruction> instructions = new ArrayList<>(controlFlow.getInstructions());

        // temporary replace all return's with empty statements in the flow
        for (PsiReturnStatement aReturn : returns) {
            int offset = controlFlow.getStartOffset(aReturn);
            int endOffset = controlFlow.getEndOffset(aReturn);
            while (offset <= endOffset && !(instructions.get(offset) instanceof GoToInstruction)) {
                offset++;
            }
            LOG.assertTrue(instructions.get(offset) instanceof GoToInstruction);
            instructions.set(offset, EmptyInstruction.INSTANCE);
        }

        for (PsiReturnStatement aReturn : returns) {
            int offset = controlFlow.getEndOffset(aReturn);
            while (true) {
                if (offset == instructions.size()) {
                    break;
                }
                Instruction instruction = instructions.get(offset);
                if (instruction instanceof GoToInstruction goToInsn) {
                    offset = goToInsn.offset;
                }
                else if (instruction instanceof ThrowToInstruction throwToInsn) {
                    offset = throwToInsn.offset;
                }
                else if (instruction instanceof ConditionalThrowToInstruction) {
                    // In case of "conditional throw to", control flow will not be altered
                    // If exception handler is in method, we will inline it to invocation site
                    // If exception handler is at invocation site, execution will continue to get there
                    offset++;
                }
                else {
                    return true;
                }
            }
        }

        return false;
    }

    private static class BlockData {
        final PsiCodeBlock block;
        final PsiLocalVariable thisVar;
        final PsiLocalVariable[] paramVars;
        final PsiLocalVariable resultVar;

        public BlockData(PsiCodeBlock block, PsiLocalVariable thisVar, PsiLocalVariable[] paramVars, PsiLocalVariable resultVar) {
            this.block = block;
            this.thisVar = thisVar;
            this.paramVars = paramVars;
            this.resultVar = resultVar;
        }
    }

    @Nonnull
    @Override
    protected Collection<? extends PsiElement> getElementsToWrite(@Nonnull UsageViewDescriptor descriptor) {
        if (myInlineThisOnly) {
            return Collections.singletonList(myReference);
        }
        else {
            if (!checkReadOnly()) {
                return Collections.emptyList();
            }
            return myReference == null ? Collections.singletonList(myMethod) : Arrays.asList(myReference, myMethod);
        }
    }
}
