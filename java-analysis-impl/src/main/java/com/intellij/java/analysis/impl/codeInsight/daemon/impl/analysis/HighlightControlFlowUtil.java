/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import com.intellij.java.analysis.impl.psi.controlFlow.AllVariablesControlFlowPolicy;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.language.impl.psi.controlFlow.*;
import com.intellij.java.language.impl.psi.util.JavaPsiRecordUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.augment.PsiAugmentProvider;
import com.intellij.java.language.psi.util.FileTypeUtils;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.dumb.IndexNotReadyException;
import consulo.document.util.TextRange;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.java.language.impl.localize.JavaErrorLocalize;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.QuickFixAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * @author cdr
 * @since Aug 8, 2002
 */
public class HighlightControlFlowUtil {
    private HighlightControlFlowUtil() {
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkMissingReturnStatement(@Nullable PsiCodeBlock body, @Nullable PsiType returnType) {
        if (body == null || returnType == null || PsiType.VOID.equals(returnType.getDeepComponentType())) {
            return null;
        }

        // do not compute constant expressions for if() statement condition
        // see JLS 14.20 Unreachable Statements
        try {
            ControlFlow controlFlow = getControlFlowNoConstantEvaluate(body);
            if (!ControlFlowUtil.returnPresent(controlFlow)) {
                PsiJavaToken rBrace = body.getRBrace();
                HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(rBrace == null ? body.getLastChild() : rBrace)
                    .descriptionAndTooltip(JavaErrorLocalize.missingReturnStatement())
                    .create();
                PsiElement parent = body.getParent();
                if (parent instanceof PsiMethod method) {
                    QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createAddReturnFix(method));
                    QuickFixAction.registerQuickFixAction(
                        info,
                        QuickFixFactory.getInstance().createMethodReturnFix(method, PsiType.VOID, true)
                    );
                }
                return info;
            }
        }
        catch (AnalysisCanceledException ignored) {
        }

        return null;
    }

    @Nonnull
    public static ControlFlow getControlFlowNoConstantEvaluate(@Nonnull PsiElement body) throws AnalysisCanceledException {
        LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
        return ControlFlowFactory.getControlFlow(body, policy, ControlFlowOptions.NO_CONST_EVALUATE);
    }

    @Nonnull
    private static ControlFlow getControlFlow(@Nonnull PsiElement context) throws AnalysisCanceledException {
        LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
        return ControlFlowFactory.getInstance(context.getProject()).getControlFlow(context, policy);
    }

    @RequiredReadAction
    public static HighlightInfo checkUnreachableStatement(@Nullable PsiCodeBlock codeBlock) {
        if (codeBlock == null) {
            return null;
        }
        // do not compute constant expressions for if() statement condition
        // see JLS 14.20 Unreachable Statements
        try {
            AllVariablesControlFlowPolicy policy = AllVariablesControlFlowPolicy.getInstance();
            ControlFlow controlFlow = ControlFlowFactory.getControlFlow(codeBlock, policy, ControlFlowOptions.NO_CONST_EVALUATE);
            PsiElement unreachableStatement = ControlFlowUtil.getUnreachableStatement(controlFlow);
            if (unreachableStatement != null) {
                PsiElement keyword = null;
                if (unreachableStatement instanceof PsiIfStatement ||
                    unreachableStatement instanceof PsiSwitchBlock ||
                    unreachableStatement instanceof PsiLoopStatement) {
                    keyword = unreachableStatement.getFirstChild();
                }
                PsiElement element = keyword != null ? keyword : unreachableStatement;
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(element)
                    .descriptionAndTooltip(JavaErrorLocalize.unreachableStatement())
                    .registerFix(QuickFixFactory.getInstance().createDeleteFix(
                        unreachableStatement,
                        JavaQuickFixBundle.message("delete.unreachable.statement.fix.text")
                    ), null, null, null, null)
                    .create();
            }
        }
        catch (AnalysisCanceledException | IndexNotReadyException e) {
            // incomplete code
        }
        return null;
    }

    public static boolean isFieldInitializedAfterObjectConstruction(@Nonnull PsiField field) {
        if (field.hasInitializer()) {
            return true;
        }
        final boolean isFieldStatic = field.isStatic();
        final PsiClass aClass = field.getContainingClass();
        if (aClass != null) {
            // field might be assigned in the other field initializers
            if (isFieldInitializedInOtherFieldInitializer(aClass, field, isFieldStatic, __ -> true)) {
                return true;
            }
        }
        final PsiClassInitializer[] initializers;
        if (aClass != null) {
            initializers = aClass.getInitializers();
        }
        else {
            return false;
        }
        if (isFieldInitializedInClassInitializer(field, isFieldStatic, initializers)) {
            return true;
        }
        if (isFieldStatic) {
            return false;
        }
        else {
            // instance field should be initialized at the end of the each constructor
            final PsiMethod[] constructors = aClass.getConstructors();

            if (constructors.length == 0) {
                return false;
            }
            nextConstructor:
            for (PsiMethod constructor : constructors) {
                PsiCodeBlock ctrBody = constructor.getBody();
                if (ctrBody == null) {
                    return false;
                }
                final List<PsiMethod> redirectedConstructors = JavaHighlightUtil.getChainedConstructors(constructor);
                for (PsiMethod redirectedConstructor : redirectedConstructors) {
                    final PsiCodeBlock body = redirectedConstructor.getBody();
                    if (body != null && variableDefinitelyAssignedIn(field, body)) {
                        continue nextConstructor;
                    }
                }
                if (!ctrBody.isValid() || variableDefinitelyAssignedIn(field, ctrBody)) {
                    continue;
                }
                return false;
            }
            return true;
        }
    }

    private static boolean isFieldInitializedInOtherFieldInitializer(
        @Nonnull PsiClass aClass,
        @Nonnull PsiField field,
        final boolean fieldStatic,
        @Nonnull Predicate<? super PsiField> condition
    ) {
        PsiField[] fields = aClass.getFields();
        for (PsiField psiField : fields) {
            if (psiField != field
                && psiField.isStatic() == fieldStatic
                && variableDefinitelyAssignedIn(field, psiField)
                && condition.test(psiField)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRecursivelyCalledConstructor(@Nonnull PsiMethod constructor) {
        final JavaHighlightUtil.ConstructorVisitorInfo info = new JavaHighlightUtil.ConstructorVisitorInfo();
        JavaHighlightUtil.visitConstructorChain(constructor, info);
        if (info.recursivelyCalledConstructor == null) {
            return false;
        }
        // our constructor is reached from some other constructor by constructor chain
        return info.visitedConstructors.indexOf(info.recursivelyCalledConstructor) <= info.visitedConstructors.indexOf(constructor);
    }

    public static boolean isAssigned(@Nonnull PsiParameter parameter) {
        ParamWriteProcessor processor = new ParamWriteProcessor();
        ReferencesSearch.search(parameter, new LocalSearchScope(parameter.getDeclarationScope()), true).forEach(processor);
        return processor.isWriteRefFound();
    }

    private static class ParamWriteProcessor implements Predicate<PsiReference> {
        private volatile boolean myIsWriteRefFound;

        @Override
        @RequiredReadAction
        public boolean test(PsiReference reference) {
            PsiElement element = reference.getElement();
            if (element instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting((PsiExpression)element)) {
                myIsWriteRefFound = true;
                return false;
            }
            return true;
        }

        private boolean isWriteRefFound() {
            return myIsWriteRefFound;
        }
    }

    /**
     * see JLS chapter 16
     *
     * @return true if variable assigned (maybe more than once)
     */
    private static boolean variableDefinitelyAssignedIn(@Nonnull PsiVariable variable, @Nonnull PsiElement context) {
        try {
            ControlFlow controlFlow = getControlFlow(context);
            return ControlFlowUtil.isVariableDefinitelyAssigned(variable, controlFlow);
        }
        catch (AnalysisCanceledException e) {
            return false;
        }
    }

    private static boolean variableDefinitelyNotAssignedIn(@Nonnull PsiVariable variable, @Nonnull PsiElement context) {
        try {
            ControlFlow controlFlow = getControlFlow(context);
            return ControlFlowUtil.isVariableDefinitelyNotAssigned(variable, controlFlow);
        }
        catch (AnalysisCanceledException e) {
            return false;
        }
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkFinalFieldInitialized(@Nonnull PsiField field) {
        if (!field.isFinal()) {
            return null;
        }
        if (isFieldInitializedAfterObjectConstruction(field)) {
            return null;
        }

        String description = JavaErrorLocalize.variableNotInitialized(field.getName()).get();
        TextRange range = HighlightNamesUtil.getFieldDeclarationTextRange(field);
        HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(range)
            .descriptionAndTooltip(description)
            .create();
        QuickFixAction.registerQuickFixAction(
            highlightInfo,
            HighlightMethodUtil.getFixRange(field),
            QuickFixFactory.getInstance().createCreateConstructorParameterFromFieldFix(field)
        );
        QuickFixAction.registerQuickFixAction(
            highlightInfo,
            HighlightMethodUtil.getFixRange(field),
            QuickFixFactory.getInstance().createInitializeFinalFieldInConstructorFix(field)
        );
        final PsiClass containingClass = field.getContainingClass();
        if (containingClass != null && !containingClass.isInterface()) {
            QuickFixAction.registerQuickFixAction(
                highlightInfo,
                QuickFixFactory.getInstance().createModifierListFix(field, PsiModifier.FINAL, false, false)
            );
        }
        QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createAddVariableInitializerFix(field));
        return highlightInfo;
    }

    @RequiredReadAction
    public static HighlightInfo checkVariableInitializedBeforeUsage(
        @Nonnull PsiReferenceExpression expression,
        @Nonnull PsiVariable variable,
        @Nonnull Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems,
        @Nonnull PsiFile containingFile
    ) {
        return checkVariableInitializedBeforeUsage(expression, variable, uninitializedVarProblems, containingFile, false);
    }

    @RequiredReadAction
    public static HighlightInfo checkVariableInitializedBeforeUsage(
        @Nonnull PsiReferenceExpression expression,
        @Nonnull PsiVariable variable,
        @Nonnull Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems,
        @Nonnull PsiFile containingFile,
        boolean ignoreFinality
    ) {
        if (variable instanceof ImplicitVariable) {
            return null;
        }
        if (!PsiUtil.isAccessedForReading(expression)) {
            return null;
        }
        int startOffset = expression.getTextRange().getStartOffset();
        PsiElement topBlock;
        if (variable.hasInitializer()) {
            topBlock = PsiUtil.getVariableCodeBlock(variable, variable);
            if (topBlock == null) {
                return null;
            }
        }
        else {
            PsiElement scope = variable instanceof PsiField field
                ? field.getContainingClass()
                : variable.getParent() != null ? variable.getParent().getParent() : null;
            while (scope instanceof PsiCodeBlock && scope.getParent() instanceof PsiSwitchStatement) {
                scope = PsiTreeUtil.getParentOfType(scope, PsiCodeBlock.class);
            }

            topBlock = FileTypeUtils.isInServerPageFile(scope) && scope instanceof PsiFile
                ? scope
                : PsiUtil.getTopLevelEnclosingCodeBlock(expression, scope);
            if (variable instanceof PsiField field) {
                // non final field already initialized with default value
                if (!ignoreFinality && !field.isFinal()) {
                    return null;
                }
                // final field may be initialized in ctor or class initializer only
                // if we're inside non-ctr method, skip it
                if (PsiUtil.findEnclosingConstructorOrInitializer(expression) == null
                    && HighlightUtil.findEnclosingFieldInitializer(expression) == null) {
                    return null;
                }
                if (topBlock == null) {
                    return null;
                }
                final PsiElement parent = topBlock.getParent();
                // access to final fields from inner classes always allowed
                if (inInnerClass(expression, field.getContainingClass())) {
                    return null;
                }
                final PsiCodeBlock block;
                final PsiClass aClass;
                if (parent instanceof PsiMethod) {
                    PsiMethod constructor = (PsiMethod)parent;
                    if (!containingFile.getManager()
                        .areElementsEquivalent(constructor.getContainingClass(), field.getContainingClass())) {
                        return null;
                    }
                    // static variables already initialized in class initializers
                    if (field.isStatic()) {
                        return null;
                    }
                    // as a last chance, field may be initialized in this() call
                    final List<PsiMethod> redirectedConstructors = JavaHighlightUtil.getChainedConstructors(constructor);
                    for (PsiMethod redirectedConstructor : redirectedConstructors) {
                        // variable must be initialized before its usage
                        //???
                        //if (startOffset < redirectedConstructor.getTextRange().getStartOffset()) continue;
                        if (JavaPsiRecordUtil.isCompactConstructor(redirectedConstructor)) {
                            return null;
                        }
                        PsiCodeBlock body = redirectedConstructor.getBody();
                        if (body != null && variableDefinitelyAssignedIn(field, body)) {
                            return null;
                        }
                    }
                    block = constructor.getBody();
                    aClass = constructor.getContainingClass();
                }
                else if (parent instanceof PsiClassInitializer) {
                    final PsiClassInitializer classInitializer = (PsiClassInitializer)parent;
                    if (!containingFile.getManager().areElementsEquivalent(
                        classInitializer.getContainingClass(),
                        ((PsiField)variable).getContainingClass()
                    )) {
                        return null;
                    }
                    block = classInitializer.getBody();
                    aClass = classInitializer.getContainingClass();

                    if (aClass == null || isFieldInitializedInOtherFieldInitializer(
                        aClass,
                        (PsiField)variable,
                        variable.hasModifierProperty(PsiModifier.STATIC),
                        field1 -> startOffset > field1.getTextOffset()
                    )) {
                        return null;
                    }
                }
                else {
                    // field reference outside code block
                    // check variable initialized before its usage
                    aClass = field.getContainingClass();
                    final PsiField anotherField = PsiTreeUtil.getTopmostParentOfType(expression, PsiField.class);
                    if (aClass == null ||
                        isFieldInitializedInOtherFieldInitializer(
                            aClass,
                            field,
                            field.isStatic(),
                            psiField -> startOffset > psiField.getTextOffset()
                        )) {
                        return null;
                    }
                    if (anotherField != null && !anotherField.isStatic() && field.isStatic()
                        && isFieldInitializedInClassInitializer(field, true, aClass.getInitializers())) {
                        return null;
                    }

                    if (anotherField != null && anotherField.hasInitializer() && !PsiAugmentProvider.canTrustFieldInitializer(anotherField)) {
                        return null;
                    }

                    int offset = startOffset;
                    if (anotherField != null && anotherField.getContainingClass() == aClass && !field.isStatic()) {
                        offset = 0;
                    }
                    block = null;
                    // initializers will be checked later
                    final PsiMethod[] constructors = aClass.getConstructors();
                    for (PsiMethod constructor : constructors) {
                        // variable must be initialized before its usage
                        if (offset < constructor.getTextRange().getStartOffset()) {
                            continue;
                        }
                        PsiCodeBlock body = constructor.getBody();
                        if (body != null && variableDefinitelyAssignedIn(field, body)) {
                            return null;
                        }
                        // as a last chance, field may be initialized in this() call
                        final List<PsiMethod> redirectedConstructors = JavaHighlightUtil.getChainedConstructors(constructor);
                        for (PsiMethod redirectedConstructor : redirectedConstructors) {
                            // variable must be initialized before its usage
                            if (offset < redirectedConstructor.getTextRange().getStartOffset()) {
                                continue;
                            }
                            PsiCodeBlock redirectedBody = redirectedConstructor.getBody();
                            if (redirectedBody != null && variableDefinitelyAssignedIn(field, redirectedBody)) {
                                return null;
                            }
                        }
                    }
                }

                if (aClass != null) {
                    // field may be initialized in class initializer
                    final PsiClassInitializer[] initializers = aClass.getInitializers();
                    for (PsiClassInitializer initializer : initializers) {
                        PsiCodeBlock body = initializer.getBody();
                        if (body == block) {
                            break;
                        }
                        // variable referenced in initializer must be initialized in initializer preceding assignment
                        // variable referenced in field initializer or in class initializer
                        boolean shouldCheckInitializerOrder = block == null || block.getParent() instanceof PsiClassInitializer;
                        if (shouldCheckInitializerOrder && startOffset < initializer.getTextRange().getStartOffset()) {
                            continue;
                        }
                        if (initializer.isStatic() == field.isStatic() && variableDefinitelyAssignedIn(field, body)) {
                            return null;
                        }
                    }
                }
            }
        }
        if (topBlock == null) {
            return null;
        }
        Collection<PsiReferenceExpression> codeBlockProblems = uninitializedVarProblems.get(topBlock);
        if (codeBlockProblems == null) {
            try {
                final ControlFlow controlFlow = getControlFlow(topBlock);
                codeBlockProblems = ControlFlowUtil.getReadBeforeWriteLocals(controlFlow);
            }
            catch (AnalysisCanceledException | IndexNotReadyException e) {
                codeBlockProblems = Collections.emptyList();
            }
            uninitializedVarProblems.put(topBlock, codeBlockProblems);
        }
        if (codeBlockProblems.contains(expression)) {
            String name = expression.getElement().getText();
            HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(expression)
                .descriptionAndTooltip(JavaErrorLocalize.variableNotInitialized(name))
                .registerFix(QuickFixFactory.getInstance().createAddVariableInitializerFix(variable), null, null, null, null)
                .create();
            if (variable instanceof PsiLocalVariable) {
                //QuickFixAction.registerQuickFixAction(highlightInfo, HighlightFixUtil.createInsertSwitchDefaultFix(variable, topBlock, expression));
            }
            if (variable instanceof PsiField) {
                QuickFixAction.registerQuickFixAction(
                    highlightInfo,
                    QuickFixFactory.getInstance().createModifierListFix(variable, PsiModifier.FINAL, false, false)
                );
            }
            return highlightInfo;
        }

        return null;
    }

    private static boolean isFieldInitializedInClassInitializer(
        @Nonnull PsiField field,
        boolean isFieldStatic,
        @Nonnull PsiClassInitializer[] initializers
    ) {
        return ContainerUtil.find(
            initializers,
            initializer -> initializer.isStatic() == isFieldStatic && variableDefinitelyAssignedIn(field, initializer.getBody())
        ) != null;
    }

    private static boolean inInnerClass(@Nonnull PsiElement psiElement, @Nullable PsiClass containingClass) {
        for (PsiElement element = psiElement; element != null; element = element.getParent()) {
            if (element instanceof PsiClass) {
                final boolean innerClass = !psiElement.getManager().areElementsEquivalent(element, containingClass);
                if (innerClass) {
                    if (element instanceof PsiAnonymousClass) {
                        if (PsiTreeUtil.isAncestor(((PsiAnonymousClass)element).getArgumentList(), psiElement, false)) {
                            continue;
                        }
                        return !insideClassInitialization(containingClass, (PsiClass)element);
                    }
                    final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(psiElement, PsiLambdaExpression.class);
                    return lambdaExpression == null || !insideClassInitialization(containingClass, (PsiClass)element);
                }
                return false;
            }
        }
        return false;
    }

    private static boolean insideClassInitialization(@Nullable PsiClass containingClass, PsiClass aClass) {
        PsiMember member = aClass;
        while (member != null) {
            if (member.getContainingClass() == containingClass) {
                return member instanceof PsiField
                    || member instanceof PsiMethod && ((PsiMethod)member).isConstructor()
                    || member instanceof PsiClassInitializer;
            }
            member = PsiTreeUtil.getParentOfType(member, PsiMember.class, true);
        }
        return false;
    }

    public static boolean isReassigned(
        @Nonnull PsiVariable variable,
        @Nonnull Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems
    ) {
        if (variable instanceof PsiLocalVariable) {
            final PsiElement parent = variable.getParent();
            if (parent == null) {
                return false;
            }
            final PsiElement declarationScope = parent.getParent();
            if (declarationScope == null) {
                return false;
            }
            Collection<ControlFlowUtil.VariableInfo> codeBlockProblems =
                getFinalVariableProblemsInBlock(finalVarProblems, declarationScope);
            return codeBlockProblems.contains(new ControlFlowUtil.VariableInfo(variable, null));
        }
        if (variable instanceof PsiParameter) {
            final PsiParameter parameter = (PsiParameter)variable;
            return isAssigned(parameter);
        }
        return false;
    }


    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkFinalVariableMightAlreadyHaveBeenAssignedTo(
        @Nonnull PsiVariable variable,
        @Nonnull PsiReferenceExpression expression,
        @Nonnull Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems
    ) {
        if (!PsiUtil.isAccessedForWriting(expression)) {
            return null;
        }

        final PsiElement scope =
            variable instanceof PsiField ? variable.getParent() : variable.getParent() == null ? null : variable.getParent().getParent();
        PsiElement codeBlock = PsiUtil.getTopLevelEnclosingCodeBlock(expression, scope);
        if (codeBlock == null) {
            return null;
        }
        Collection<ControlFlowUtil.VariableInfo> codeBlockProblems = getFinalVariableProblemsInBlock(finalVarProblems, codeBlock);

        boolean alreadyAssigned = false;
        for (ControlFlowUtil.VariableInfo variableInfo : codeBlockProblems) {
            if (variableInfo.expression == expression) {
                alreadyAssigned = true;
                break;
            }
        }

        if (!alreadyAssigned) {
            if (!(variable instanceof PsiField)) {
                return null;
            }
            final PsiField field = (PsiField)variable;
            final PsiClass aClass = field.getContainingClass();
            if (aClass == null) {
                return null;
            }
            // field can get assigned in other field initializers
            final PsiField[] fields = aClass.getFields();
            boolean isFieldStatic = field.isStatic();
            for (PsiField psiField : fields) {
                PsiExpression initializer = psiField.getInitializer();
                if (psiField != field
                    && psiField.isStatic() == isFieldStatic
                    && initializer != null && initializer != codeBlock
                    && !variableDefinitelyNotAssignedIn(field, initializer)) {
                    alreadyAssigned = true;
                    break;
                }
            }

            if (!alreadyAssigned) {
                // field can get assigned in class initializers
                final PsiMember enclosingConstructorOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expression);
                if (enclosingConstructorOrInitializer == null
                    || !aClass.getManager().areElementsEquivalent(enclosingConstructorOrInitializer.getContainingClass(), aClass)) {
                    return null;
                }
                final PsiClassInitializer[] initializers = aClass.getInitializers();
                for (PsiClassInitializer initializer : initializers) {
                    if (initializer.isStatic() == field.isStatic()) {
                        final PsiCodeBlock body = initializer.getBody();
                        if (body == codeBlock) {
                            return null;
                        }
                        try {
                            final ControlFlow controlFlow = getControlFlow(body);
                            if (!ControlFlowUtil.isVariableDefinitelyNotAssigned(field, controlFlow)) {
                                alreadyAssigned = true;
                                break;
                            }
                        }
                        catch (AnalysisCanceledException e) {
                            // incomplete code
                            return null;
                        }
                    }
                }
            }

            if (!alreadyAssigned && !field.isStatic()) {
                // then check if instance field already assigned in other constructor
                final PsiMethod ctr = codeBlock.getParent() instanceof PsiMethod ? (PsiMethod)codeBlock.getParent() : null;
                // assignment to final field in several constructors threatens us only if these are linked (there is this() call in the beginning)
                final List<PsiMethod> redirectedConstructors =
                    ctr != null && ctr.isConstructor() ? JavaHighlightUtil.getChainedConstructors(ctr) : null;
                for (int j = 0; redirectedConstructors != null && j < redirectedConstructors.size(); j++) {
                    PsiMethod redirectedConstructor = redirectedConstructors.get(j);
                    PsiCodeBlock body = redirectedConstructor.getBody();
                    if (body != null && variableDefinitelyAssignedIn(variable, body)) {
                        alreadyAssigned = true;
                        break;
                    }
                }
            }
        }

        if (alreadyAssigned) {
            final HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(expression)
                .descriptionAndTooltip(JavaErrorLocalize.variableAlreadyAssigned(variable.getName()))
                .create();
            QuickFixAction.registerQuickFixAction(
                highlightInfo,
                QuickFixFactory.getInstance().createModifierListFix(variable, PsiModifier.FINAL, false, false)
            );
            QuickFixAction.registerQuickFixAction(
                highlightInfo,
                QuickFixFactory.getInstance().createDeferFinalAssignmentFix(variable, expression)
            );
            return highlightInfo;
        }

        return null;
    }

    @Nonnull
    private static Collection<ControlFlowUtil.VariableInfo> getFinalVariableProblemsInBlock(
        @Nonnull Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> finalVarProblems,
        @Nonnull PsiElement codeBlock
    ) {
        Collection<ControlFlowUtil.VariableInfo> codeBlockProblems = finalVarProblems.get(codeBlock);
        if (codeBlockProblems == null) {
            try {
                final ControlFlow controlFlow = getControlFlowNoConstantEvaluate(codeBlock);
                codeBlockProblems = ControlFlowUtil.getInitializedTwice(controlFlow);
            }
            catch (AnalysisCanceledException e) {
                codeBlockProblems = Collections.emptyList();
            }
            finalVarProblems.put(codeBlock, codeBlockProblems);
        }
        return codeBlockProblems;
    }


    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkFinalVariableInitializedInLoop(
        @Nonnull PsiReferenceExpression expression,
        @Nonnull PsiElement resolved
    ) {
        if (ControlFlowUtil.isVariableAssignedInLoop(expression, resolved)) {
            final HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(expression)
                .descriptionAndTooltip(JavaErrorLocalize.variableAssignedInLoop(((PsiVariable)resolved).getName()))
                .create();
            QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance()
                .createModifierListFix((PsiVariable)resolved, PsiModifier.FINAL, false, false));
            return highlightInfo;
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkCannotWriteToFinal(@Nonnull PsiExpression expression, @Nonnull PsiFile containingFile) {
        PsiReferenceExpression reference = null;
        boolean readBeforeWrite = false;
        if (expression instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
            final PsiExpression left = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
            if (left instanceof PsiReferenceExpression) {
                reference = (PsiReferenceExpression)left;
            }
            readBeforeWrite = assignmentExpression.getOperationTokenType() != JavaTokenType.EQ;
        }
        else if (expression instanceof PsiPostfixExpression) {
            final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(((PsiPostfixExpression)expression).getOperand());
            final IElementType sign = ((PsiPostfixExpression)expression).getOperationTokenType();
            if (operand instanceof PsiReferenceExpression && (sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS)) {
                reference = (PsiReferenceExpression)operand;
            }
            readBeforeWrite = true;
        }
        else if (expression instanceof PsiPrefixExpression) {
            final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(((PsiPrefixExpression)expression).getOperand());
            final IElementType sign = ((PsiPrefixExpression)expression).getOperationTokenType();
            if (operand instanceof PsiReferenceExpression && (sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS)) {
                reference = (PsiReferenceExpression)operand;
            }
            readBeforeWrite = true;
        }
        final PsiElement resolved = reference == null ? null : reference.resolve();
        PsiVariable variable = resolved instanceof PsiVariable ? (PsiVariable)resolved : null;
        if (variable == null || !variable.hasModifierProperty(PsiModifier.FINAL)) {
            return null;
        }
        final boolean canWrite =
            canWriteToFinal(variable, expression, reference, containingFile) && checkWriteToFinalInsideLambda(variable, reference) == null;
        if (readBeforeWrite || !canWrite) {
            final String name = variable.getName();
            LocalizeValue description = canWrite
                ? JavaErrorLocalize.variableNotInitialized(name)
                : JavaErrorLocalize.assignmentToFinalVariable(name);
            final HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(reference.getTextRange())
                .descriptionAndTooltip(description)
                .create();
            final PsiElement innerClass = getInnerClassVariableReferencedFrom(variable, expression);
            if (innerClass == null || variable instanceof PsiField) {
                QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance()
                    .createModifierListFix(variable, PsiModifier.FINAL, false, false));
            }
            else {
                QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance()
                    .createVariableAccessFromInnerClassFix(variable, innerClass));
            }
            return highlightInfo;
        }

        return null;
    }

    private static boolean canWriteToFinal(
        @Nonnull PsiVariable variable,
        @Nonnull PsiExpression expression,
        @Nonnull PsiReferenceExpression reference,
        @Nonnull PsiFile containingFile
    ) {
        if (variable.hasInitializer()) {
            return false;
        }
        if (variable instanceof PsiParameter) {
            return false;
        }
        PsiElement innerClass = getInnerClassVariableReferencedFrom(variable, expression);
        if (variable instanceof PsiField) {
            // if inside some field initializer
            if (HighlightUtil.findEnclosingFieldInitializer(expression) != null) {
                return true;
            }
            // assignment from within inner class is illegal always
            PsiField field = (PsiField)variable;
            if (innerClass != null && !containingFile.getManager().areElementsEquivalent(innerClass, field.getContainingClass())) {
                return false;
            }
            final PsiMember enclosingCtrOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expression);
            return enclosingCtrOrInitializer != null && isSameField(enclosingCtrOrInitializer, field, reference, containingFile);
        }
        if (variable instanceof PsiLocalVariable) {
            boolean isAccessedFromOtherClass = innerClass != null;
            if (isAccessedFromOtherClass) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSameField(
        @Nonnull PsiMember enclosingCtrOrInitializer,
        @Nonnull PsiField field,
        @Nonnull PsiReferenceExpression reference,
        @Nonnull PsiFile containingFile
    ) {
        if (!containingFile.getManager()
            .areElementsEquivalent(enclosingCtrOrInitializer.getContainingClass(), field.getContainingClass())) {
            return false;
        }
        return LocalsOrMyInstanceFieldsControlFlowPolicy.isLocalOrMyInstanceReference(reference);
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkVariableMustBeFinal(
        @Nonnull PsiVariable variable,
        @Nonnull PsiJavaCodeReferenceElement context,
        @Nonnull LanguageLevel languageLevel
    ) {
        if (variable.hasModifierProperty(PsiModifier.FINAL)) {
            return null;
        }
        final PsiElement innerClass = getInnerClassVariableReferencedFrom(variable, context);
        if (innerClass instanceof PsiClass) {
            if (variable instanceof PsiParameter) {
                final PsiElement parent = variable.getParent();
                if (parent instanceof PsiParameterList && parent.getParent() instanceof PsiLambdaExpression &&
                    notAccessedForWriting(variable, new LocalSearchScope(((PsiParameter)variable).getDeclarationScope()))) {
                    return null;
                }
            }
            final boolean isToBeEffectivelyFinal = languageLevel.isAtLeast(LanguageLevel.JDK_1_8);
            if (isToBeEffectivelyFinal && isEffectivelyFinal(variable, innerClass, context)) {
                return null;
            }
            final String description = isToBeEffectivelyFinal
                ? JavaErrorBundle.message("variable.must.be.final.or.effectively.final", context.getText())
                : JavaErrorLocalize.variableMustBeFinal(context.getText()).get();

            final HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .range(context)
                .descriptionAndTooltip(description)
                .create();
            QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance()
                .createVariableAccessFromInnerClassFix(variable, innerClass));
            return highlightInfo;
        }
        return checkWriteToFinalInsideLambda(variable, context);
    }

    @RequiredReadAction
    private static HighlightInfo checkWriteToFinalInsideLambda(
        @Nonnull PsiVariable variable,
        @Nonnull PsiJavaCodeReferenceElement context
    ) {
        PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(context, PsiLambdaExpression.class);
        if (lambdaExpression != null && !PsiTreeUtil.isAncestor(lambdaExpression, variable, true)) {
            PsiElement parent = variable.getParent();
            if (parent instanceof PsiParameterList && parent.getParent() == lambdaExpression) {
                return null;
            }
            if (!isEffectivelyFinal(variable, lambdaExpression, context)) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(context)
                    .descriptionAndTooltip(JavaErrorLocalize.lambdaVariableMustBeFinal())
                    .registerFix(QuickFixFactory.getInstance().createVariableAccessFromInnerClassFix(variable, lambdaExpression), null, null, null, null)
                    .create();
            }
        }
        return null;
    }

    @RequiredReadAction
    public static boolean isEffectivelyFinal(
        @Nonnull PsiVariable variable,
        @Nonnull PsiElement scope,
        @Nullable PsiJavaCodeReferenceElement context
    ) {
        boolean effectivelyFinal;
        if (variable instanceof PsiParameter) {
            effectivelyFinal = notAccessedForWriting(variable, new LocalSearchScope(((PsiParameter)variable).getDeclarationScope()));
        }
        else {
            ControlFlow controlFlow;
            try {
                PsiElement codeBlock = PsiUtil.getVariableCodeBlock(variable, context);
                if (codeBlock == null) {
                    return true;
                }
                controlFlow = getControlFlow(codeBlock);
            }
            catch (AnalysisCanceledException e) {
                return true;
            }

            List<PsiReferenceExpression> readBeforeWriteLocals = ControlFlowUtil.getReadBeforeWriteLocals(controlFlow);
            for (PsiReferenceExpression expression : readBeforeWriteLocals) {
                if (expression.resolve() == variable) {
                    return PsiUtil.isAccessedForReading(expression);
                }
            }

            Collection<ControlFlowUtil.VariableInfo> initializedTwice = ControlFlowUtil.getInitializedTwice(controlFlow);
            effectivelyFinal = !initializedTwice.contains(new ControlFlowUtil.VariableInfo(variable, null));
            if (effectivelyFinal) {
                effectivelyFinal = notAccessedForWriting(variable, new LocalSearchScope(scope));
            }
        }
        return effectivelyFinal;
    }

    @RequiredReadAction
    private static boolean notAccessedForWriting(@Nonnull PsiVariable variable, @Nonnull LocalSearchScope searchScope) {
        for (PsiReference reference : ReferencesSearch.search(variable, searchScope)) {
            PsiElement element = reference.getElement();
            if (element instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)element)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public static PsiElement getInnerClassVariableReferencedFrom(@Nonnull PsiVariable variable, @Nonnull PsiElement context) {
        PsiElement[] scope;
        if (variable instanceof PsiResourceVariable resourceVar) {
            scope = resourceVar.getDeclarationScope();
        }
        else if (variable instanceof PsiLocalVariable) {
            PsiElement parent = variable.getParent();
            scope = new PsiElement[]{parent != null ? parent.getParent() : null}; // code block or for statement
        }
        else if (variable instanceof PsiParameter param) {
            scope = new PsiElement[]{param.getDeclarationScope()};
        }
        else {
            scope = new PsiElement[]{variable.getParent()};
        }
        if (scope.length < 1 || scope[0] == null || scope[0].getContainingFile() != context.getContainingFile()) {
            return null;
        }

        PsiElement parent = context.getParent();
        PsiElement prevParent = context;
        outer:
        while (parent != null) {
            for (PsiElement scopeElement : scope) {
                if (parent.equals(scopeElement)) {
                    break outer;
                }
            }
            if (parent instanceof PsiClass && !(prevParent instanceof PsiExpressionList && parent instanceof PsiAnonymousClass)) {
                return parent;
            }
            if (parent instanceof PsiLambdaExpression) {
                return parent;
            }
            prevParent = parent;
            parent = parent.getParent();
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static HighlightInfo checkInitializerCompleteNormally(@Nonnull PsiClassInitializer initializer) {
        PsiCodeBlock body = initializer.getBody();
        // unhandled exceptions already reported
        try {
            ControlFlow controlFlow = getControlFlowNoConstantEvaluate(body);
            int completionReasons = ControlFlowUtil.getCompletionReasons(controlFlow, 0, controlFlow.getSize());
            if ((completionReasons & ControlFlowUtil.NORMAL_COMPLETION_REASON) == 0) {
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                    .range(body)
                    .descriptionAndTooltip(JavaErrorLocalize.initializerMustBeAbleToCompleteNormally())
                    .create();
            }
        }
        catch (AnalysisCanceledException e) {
            // incomplete code
        }
        return null;
    }
}
