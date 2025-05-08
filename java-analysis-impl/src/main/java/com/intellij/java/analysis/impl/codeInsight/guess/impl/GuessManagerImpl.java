// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInsight.guess.impl;

import com.intellij.java.analysis.codeInsight.guess.GuessManager;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.*;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.InstanceofInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.TypeCastInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.CachedValueProvider;
import consulo.content.scope.SearchScope;
import consulo.document.util.TextRange;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.resolve.PsiElementProcessorAdapter;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Maps;
import consulo.util.collection.MultiMap;
import consulo.util.lang.BitUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
@ServiceImpl
public final class GuessManagerImpl extends GuessManager {
    private final MethodPatternMap myMethodPatternMap = new MethodPatternMap();

    private final Project myProject;

    @Inject
    public GuessManagerImpl(Project project) {
        myProject = project;
        initMethodPatterns();
    }

    private void initMethodPatterns() {
        // Collection
        myMethodPatternMap.addPattern(new MethodPattern("add", 1, 0));
        myMethodPatternMap.addPattern(new MethodPattern("contains", 1, 0));
        myMethodPatternMap.addPattern(new MethodPattern("remove", 1, 0));

        // Vector
        myMethodPatternMap.addPattern(new MethodPattern("add", 2, 1));
        myMethodPatternMap.addPattern(new MethodPattern("addElement", 1, 0));
        myMethodPatternMap.addPattern(new MethodPattern("elementAt", 1, -1));
        myMethodPatternMap.addPattern(new MethodPattern("firstElement", 0, -1));
        myMethodPatternMap.addPattern(new MethodPattern("lastElement", 0, -1));
        myMethodPatternMap.addPattern(new MethodPattern("get", 1, -1));
        myMethodPatternMap.addPattern(new MethodPattern("indexOf", 1, 0));
        myMethodPatternMap.addPattern(new MethodPattern("indexOf", 2, 0));
        myMethodPatternMap.addPattern(new MethodPattern("lastIndexOf", 1, 0));
        myMethodPatternMap.addPattern(new MethodPattern("lastIndexOf", 2, 0));
        myMethodPatternMap.addPattern(new MethodPattern("insertElementAt", 2, 0));
        myMethodPatternMap.addPattern(new MethodPattern("removeElement", 1, 0));
        myMethodPatternMap.addPattern(new MethodPattern("set", 2, 1));
        myMethodPatternMap.addPattern(new MethodPattern("setElementAt", 2, 0));
    }

    @Override
    @Nonnull
    public PsiType[] guessContainerElementType(PsiExpression containerExpr, TextRange rangeToIgnore) {
        HashSet<PsiType> typesSet = new HashSet<>();

        PsiType type = containerExpr.getType();
        PsiType elemType;
        if ((elemType = getGenericElementType(type)) != null) {
            return new PsiType[]{elemType};
        }

        if (containerExpr instanceof PsiReferenceExpression) {
            PsiElement refElement = ((PsiReferenceExpression)containerExpr).resolve();
            if (refElement instanceof PsiVariable) {

                PsiFile file = refElement.getContainingFile();
                if (file == null) {
                    file = containerExpr.getContainingFile(); // implicit variable in jsp
                }
                HashSet<PsiVariable> checkedVariables = new HashSet<>();
                addTypesByVariable(typesSet, (PsiVariable)refElement, file, checkedVariables, CHECK_USAGE | CHECK_DOWN, rangeToIgnore);
                checkedVariables.clear();
                addTypesByVariable(typesSet, (PsiVariable)refElement, file, checkedVariables, CHECK_UP, rangeToIgnore);
            }
        }

        return typesSet.toArray(PsiType.createArray(typesSet.size()));
    }

    @Nullable
    private static PsiType getGenericElementType(PsiType collectionType) {
        if (collectionType instanceof PsiClassType) {
            PsiClassType classType = (PsiClassType)collectionType;
            PsiType[] parameters = classType.getParameters();
            if (parameters.length == 1) {
                return parameters[0];
            }
        }
        return null;
    }

    @Override
    @Nonnull
    public PsiType[] guessTypeToCast(PsiExpression expr) {
        LinkedHashSet<PsiType> types = new LinkedHashSet<>(getControlFlowExpressionTypeConjuncts(expr));
        addExprTypesWhenContainerElement(types, expr);
        addExprTypesByDerivedClasses(types, expr);
        return types.toArray(PsiType.createArray(types.size()));
    }

    @Nonnull
    @Override
    public MultiMap<PsiExpression, PsiType> getControlFlowExpressionTypes(@Nonnull PsiExpression forPlace, boolean honorAssignments) {
        PsiElement scope = DfaPsiUtil.getTopmostBlockInSameClass(forPlace);
        if (scope == null) {
            PsiFile file = forPlace.getContainingFile();
            if (!(file instanceof PsiCodeFragment)) {
                return MultiMap.empty();
            }
            scope = file;
        }

        DataFlowRunner runner = createRunner(honorAssignments, scope);

        final ExpressionTypeInstructionVisitor visitor = new ExpressionTypeInstructionVisitor(forPlace);
        if (runner.analyzeMethodWithInlining(scope, visitor) == RunnerResult.OK) {
            return visitor.getResult();
        }
        return MultiMap.empty();
    }

    @Nullable
    private static PsiType getTypeFromDataflow(PsiExpression forPlace, boolean honorAssignments) {
        PsiType type = forPlace.getType();
        TypeConstraint initial = type == null ? TypeConstraints.TOP : TypeConstraints.instanceOf(type);
        PsiElement scope = DfaPsiUtil.getTopmostBlockInSameClass(forPlace);
        if (scope == null) {
            PsiFile file = forPlace.getContainingFile();
            if (!(file instanceof PsiCodeFragment)) {
                return null;
            }
            scope = file;
        }

        DataFlowRunner runner = createRunner(honorAssignments, scope);

        class Visitor extends CastTrackingVisitor {
            TypeConstraint constraint = TypeConstraints.BOTTOM;

            @Override
            protected void beforeExpressionPush(
                @Nonnull DfaValue value,
                @Nonnull PsiExpression expression,
                @Nullable TextRange range,
                @Nonnull DfaMemoryState state
            ) {
                if (expression == forPlace && range == null) {
                    if (!(value instanceof DfaVariableValue) || ((DfaVariableValue)value).isFlushableByCalls()) {
                        value = runner.getFactory().getVarFactory().createVariableValue(new ExpressionVariableDescriptor(expression));
                    }
                    constraint = constraint.join(TypeConstraint.fromDfType(state.getDfType(value)));
                }
                super.beforeExpressionPush(value, expression, range, state);
            }

            @Override
            boolean isInteresting(@Nonnull DfaValue value, @Nonnull PsiExpression expression) {
                return (!(value instanceof DfaVariableValue) || ((DfaVariableValue)value).isFlushableByCalls()) &&
                    ExpressionVariableDescriptor.EXPRESSION_HASHING_STRATEGY.equals(expression, forPlace);
            }
        }
        final Visitor visitor = new Visitor();
        if (runner.analyzeMethodWithInlining(scope, visitor) == RunnerResult.OK) {
            return visitor.constraint.meet(initial).getPsiType(scope.getProject());
        }
        return null;
    }

    @Nonnull
    private static DataFlowRunner createRunner(boolean honorAssignments, PsiElement scope) {
        return honorAssignments ? new DataFlowRunner(scope.getProject()) : new DataFlowRunner(scope.getProject()) {
            @Nonnull
            @Override
            protected DfaMemoryState createMemoryState() {
                return new AssignmentFilteringMemoryState(getFactory());
            }
        };
    }

    private static PsiElement getTopmostBlock(PsiElement scope) {
        assert scope.isValid();
        PsiElement lastScope = scope;
        while (true) {
            final PsiCodeBlock lastCodeBlock = PsiTreeUtil.getParentOfType(lastScope, PsiCodeBlock.class, true);
            if (lastCodeBlock == null) {
                break;
            }
            lastScope = lastCodeBlock;
        }
        if (lastScope == scope) {
            PsiFile file = scope.getContainingFile();
            if (file instanceof PsiCodeFragment) {
                return file;
            }
        }
        return lastScope;
    }

    private void addExprTypesByDerivedClasses(LinkedHashSet<? super PsiType> set, PsiExpression expr) {
        PsiType type = expr.getType();
        if (!(type instanceof PsiClassType)) {
            return;
        }
        PsiClass refClass = PsiUtil.resolveClassInType(type);
        if (refClass == null) {
            return;
        }

        PsiManager manager = PsiManager.getInstance(myProject);
        PsiElementProcessor.CollectElementsWithLimit<PsiClass> processor = new PsiElementProcessor.CollectElementsWithLimit<>(5);
        ClassInheritorsSearch.search(refClass).forEach(new PsiElementProcessorAdapter<>(processor));
        if (processor.isOverflow()) {
            return;
        }

        for (PsiClass derivedClass : processor.getCollection()) {
            if (derivedClass instanceof PsiAnonymousClass) {
                continue;
            }
            PsiType derivedType = JavaPsiFacade.getElementFactory(manager.getProject()).createType(derivedClass);
            set.add(derivedType);
        }
    }

    private void addExprTypesWhenContainerElement(LinkedHashSet<? super PsiType> set, PsiExpression expr) {
        if (expr instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression callExpr = (PsiMethodCallExpression)expr;
            PsiReferenceExpression methodExpr = callExpr.getMethodExpression();
            String methodName = methodExpr.getReferenceName();
            MethodPattern pattern = myMethodPatternMap.findPattern(methodName, callExpr.getArgumentList().getExpressionCount());
            if (pattern != null && pattern.parameterIndex < 0/* return value */) {
                PsiExpression qualifier = methodExpr.getQualifierExpression();
                if (qualifier != null) {
                    PsiType[] types = guessContainerElementType(qualifier, null);
                    for (PsiType type : types) {
                        if (type instanceof PsiClassType) {
                            if (((PsiClassType)type).resolve() instanceof PsiAnonymousClass) {
                                continue;
                            }
                        }
                        set.add(type);
                    }
                }
            }
        }
    }

    private static final int CHECK_USAGE = 0x01;
    private static final int CHECK_UP = 0x02;
    private static final int CHECK_DOWN = 0x04;

    private void addTypesByVariable(
        HashSet<? super PsiType> typesSet,
        PsiVariable var,
        PsiFile scopeFile,
        HashSet<? super PsiVariable> checkedVariables,
        int flags,
        TextRange rangeToIgnore
    ) {
        if (!checkedVariables.add(var)) {
            return;
        }
        //System.out.println("analyzing usages of " + var + " in file " + scopeFile);
        SearchScope searchScope = new LocalSearchScope(scopeFile);

        if (BitUtil.isSet(flags, CHECK_USAGE) || BitUtil.isSet(flags, CHECK_DOWN)) {
            for (PsiReference varRef : ReferencesSearch.search(var, searchScope, false)) {
                PsiElement ref = varRef.getElement();

                if (BitUtil.isSet(flags, CHECK_USAGE)) {
                    PsiType type = guessElementTypeFromReference(myMethodPatternMap, ref, rangeToIgnore);
                    if (type != null && !(type instanceof PsiPrimitiveType)) {
                        typesSet.add(type);
                    }
                }

                if (BitUtil.isSet(flags, CHECK_DOWN)) {
                    if (ref.getParent() instanceof PsiExpressionList
                        && ref.getParent().getParent() instanceof PsiMethodCallExpression) { //TODO : new
                        PsiExpressionList list = (PsiExpressionList)ref.getParent();
                        int argIndex = ArrayUtil.indexOf(list.getExpressions(), ref);

                        PsiMethodCallExpression methodCall = (PsiMethodCallExpression)list.getParent();
                        PsiMethod method = (PsiMethod)methodCall.getMethodExpression().resolve();
                        if (method != null) {
                            PsiParameter[] parameters = method.getParameterList().getParameters();
                            if (argIndex < parameters.length) {
                                addTypesByVariable(
                                    typesSet,
                                    parameters[argIndex],
                                    method.getContainingFile(),
                                    checkedVariables,
                                    flags | CHECK_USAGE,
                                    rangeToIgnore
                                );
                            }
                        }
                    }
                }
            }
        }

        if (BitUtil.isSet(flags, CHECK_UP)) {
            if (var instanceof PsiParameter && var.getParent() instanceof PsiParameterList && var.getParent()
                .getParent() instanceof PsiMethod) {
                PsiParameterList list = (PsiParameterList)var.getParent();
                PsiParameter[] parameters = list.getParameters();
                int argIndex = -1;
                for (int i = 0; i < parameters.length; i++) {
                    PsiParameter parameter = parameters[i];
                    if (parameter.equals(var)) {
                        argIndex = i;
                        break;
                    }
                }

                PsiMethod method = (PsiMethod)var.getParent().getParent();
                //System.out.println("analyzing usages of " + method + " in file " + scopeFile);
                for (PsiReference methodRef : ReferencesSearch.search(method, searchScope, false)) {
                    PsiElement ref = methodRef.getElement();
                    if (ref.getParent() instanceof PsiMethodCallExpression) {
                        PsiMethodCallExpression methodCall = (PsiMethodCallExpression)ref.getParent();
                        PsiExpression[] args = methodCall.getArgumentList().getExpressions();
                        if (args.length <= argIndex) {
                            continue;
                        }
                        PsiExpression arg = args[argIndex];
                        if (arg instanceof PsiReferenceExpression) {
                            PsiElement refElement = ((PsiReferenceExpression)arg).resolve();
                            if (refElement instanceof PsiVariable) {
                                addTypesByVariable(
                                    typesSet,
                                    (PsiVariable)refElement,
                                    scopeFile,
                                    checkedVariables,
                                    flags | CHECK_USAGE,
                                    rangeToIgnore
                                );
                            }
                        }
                        //TODO : constructor
                    }
                }
            }
        }
    }

    @Nullable
    private static PsiType guessElementTypeFromReference(
        MethodPatternMap methodPatternMap,
        PsiElement ref,
        TextRange rangeToIgnore
    ) {
        PsiElement refParent = ref.getParent();
        if (refParent instanceof PsiReferenceExpression) {
            PsiReferenceExpression parentExpr = (PsiReferenceExpression)refParent;
            if (ref.equals(parentExpr.getQualifierExpression()) && parentExpr.getParent() instanceof PsiMethodCallExpression) {
                String methodName = parentExpr.getReferenceName();
                PsiMethodCallExpression methodCall = (PsiMethodCallExpression)parentExpr.getParent();
                PsiExpression[] args = methodCall.getArgumentList().getExpressions();
                MethodPattern pattern = methodPatternMap.findPattern(methodName, args.length);
                if (pattern != null) {
                    if (pattern.parameterIndex < 0) { // return value
                        if (methodCall.getParent() instanceof PsiTypeCastExpression &&
                            (rangeToIgnore == null || !rangeToIgnore.contains(methodCall.getTextRange()))) {
                            return ((PsiTypeCastExpression)methodCall.getParent()).getType();
                        }
                    }
                    else {
                        return args[pattern.parameterIndex].getType();
                    }
                }
            }
        }
        return null;
    }

    @Nonnull
    @Override
    public List<PsiType> getControlFlowExpressionTypeConjuncts(@Nonnull PsiExpression expr, boolean honorAssignments) {
        if (expr.getType() instanceof PsiPrimitiveType) {
            return Collections.emptyList();
        }
        PsiExpression place = PsiUtil.skipParenthesizedExprDown(expr);
        if (place == null) {
            return Collections.emptyList();
        }

        List<PsiType> result = null;
        if (!ControlFlowAnalyzer.inlinerMayInferPreciseType(place)) {
            GuessTypeVisitor visitor = tryGuessingTypeWithoutDfa(place, honorAssignments);
            if (!visitor.isDfaNeeded()) {
                result = visitor.mySpecificType == null ?
                    Collections.emptyList() : Collections.singletonList(DfaPsiUtil.tryGenerify(expr, visitor.mySpecificType));
            }
        }
        if (result == null) {
            PsiType psiType = getTypeFromDataflow(expr, honorAssignments);
            if (psiType instanceof PsiIntersectionType) {
                result =
                    ContainerUtil.mapNotNull(((PsiIntersectionType)psiType).getConjuncts(), type -> DfaPsiUtil.tryGenerify(expr, type));
            }
            else if (psiType != null) {
                result = Collections.singletonList(DfaPsiUtil.tryGenerify(expr, psiType));
            }
            else {
                result = Collections.emptyList();
            }
        }
        result = ContainerUtil.filter(result, t -> {
            PsiClass typeClass = PsiUtil.resolveClassInType(t);
            return typeClass == null || PsiUtil.isAccessible(typeClass, expr, null);
        });
        if (result.equals(Collections.singletonList(TypeConversionUtil.erasure(expr.getType())))) {
            return Collections.emptyList();
        }
        return result;
    }

    @Nonnull
    private static GuessTypeVisitor tryGuessingTypeWithoutDfa(PsiExpression place, boolean honorAssignments) {
        List<PsiElement> exprsAndVars = getPotentiallyAffectingElements(place);
        GuessTypeVisitor visitor = new GuessTypeVisitor(place, honorAssignments);
        for (PsiElement e : exprsAndVars) {
            e.accept(visitor);
            if (e == place || visitor.isDfaNeeded()) {
                break;
            }
        }
        return visitor;
    }

    private static List<PsiElement> getPotentiallyAffectingElements(PsiExpression place) {
        PsiElement topmostBlock = getTopmostBlock(place);
        return LanguageCachedValueUtil.getCachedValue(topmostBlock, () -> {
            List<PsiElement> list =
                SyntaxTraverser.psiTraverser(topmostBlock)
                    .filter(e -> e instanceof PsiExpression || e instanceof PsiLocalVariable)
                    .toList();
            return new CachedValueProvider.Result<>(list, topmostBlock);
        });
    }

    private static class GuessTypeVisitor extends JavaElementVisitor {
        private static final CallMatcher OBJECT_GET_CLASS =
            CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_OBJECT, "getClass").parameterCount(0);
        private final
        @Nonnull
        PsiExpression myPlace;
        PsiType mySpecificType;
        private boolean myNeedDfa;
        private boolean myDeclared;
        private final boolean myHonorAssignments;

        GuessTypeVisitor(@Nonnull PsiExpression place, boolean honorAssignments) {
            myPlace = place;
            myHonorAssignments = honorAssignments;
        }

        protected void handleAssignment(@Nullable PsiExpression expression) {
            if (!myHonorAssignments || expression == null) {
                return;
            }
            PsiType type = expression.getType();
            if (type instanceof PsiPrimitiveType) {
                type = ((PsiPrimitiveType)type).getBoxedType(expression);
            }
            PsiType rawType = type instanceof PsiClassType ? ((PsiClassType)type).rawType() : type;
            if (rawType == null || rawType.equals(PsiType.NULL)) {
                return;
            }
            if (mySpecificType == null) {
                mySpecificType = rawType;
            }
            else if (!mySpecificType.equals(rawType)) {
                myNeedDfa = true;
            }
        }

        @Override
        public void visitAssignmentExpression(PsiAssignmentExpression expression) {
            if (ExpressionVariableDescriptor.EXPRESSION_HASHING_STRATEGY.equals(expression.getLExpression(), myPlace)) {
                handleAssignment(expression.getRExpression());
            }
            super.visitAssignmentExpression(expression);
        }

        @Override
        public void visitLocalVariable(PsiLocalVariable variable) {
            if (ExpressionUtils.isReferenceTo(myPlace, variable)) {
                myDeclared = true;
                handleAssignment(variable.getInitializer());
            }
            super.visitLocalVariable(variable);
        }

        @Override
        public void visitTypeCastExpression(PsiTypeCastExpression expression) {
            PsiExpression operand = expression.getOperand();
            if (operand != null && ExpressionVariableDescriptor.EXPRESSION_HASHING_STRATEGY.equals(operand, myPlace)) {
                myNeedDfa = true;
            }
            super.visitTypeCastExpression(expression);
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression call) {
            if (OBJECT_GET_CLASS.test(call)) {
                PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
                if (qualifier != null && ExpressionVariableDescriptor.EXPRESSION_HASHING_STRATEGY.equals(qualifier, myPlace)) {
                    myNeedDfa = true;
                }
            }
            super.visitMethodCallExpression(call);
        }

        @Override
        public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
            if (ExpressionVariableDescriptor.EXPRESSION_HASHING_STRATEGY.equals(expression.getOperand(), myPlace)) {
                myNeedDfa = true;
            }
            super.visitInstanceOfExpression(expression);
        }

        public boolean isDfaNeeded() {
            if (myNeedDfa) {
                return true;
            }
            if (myDeclared || mySpecificType == null) {
                return false;
            }
            PsiType type = myPlace.getType();
            PsiType rawType = type instanceof PsiClassType ? ((PsiClassType)type).rawType() : type;
            return !mySpecificType.equals(rawType);
        }
    }

    abstract static class CastTrackingVisitor extends StandardInstructionVisitor {
        @Override
        public DfaInstructionState[] visitTypeCast(TypeCastInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
            DfaValue value = memState.pop();
            memState.push(adjustValue(runner, value, instruction.getCasted()));
            return super.visitTypeCast(instruction, runner, memState);
        }

        @Override
        public DfaInstructionState[] visitInstanceof(InstanceofInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
            DfaValue dfaRight = memState.pop();
            DfaValue dfaLeft = memState.pop();
            memState.push(adjustValue(runner, dfaLeft, instruction.getLeft()));
            memState.push(dfaRight);
            return super.visitInstanceof(instruction, runner, memState);
        }

        private DfaValue adjustValue(DataFlowRunner runner, DfaValue value, @Nullable PsiExpression expression) {
            if (expression != null && isInteresting(value, expression)) {
                value = runner.getFactory().getVarFactory().createVariableValue(new ExpressionVariableDescriptor(expression));
            }
            return value;
        }

        boolean isInteresting(@Nonnull DfaValue value, @Nonnull PsiExpression expression) {
            return true;
        }
    }

    private static final class ExpressionTypeInstructionVisitor extends CastTrackingVisitor {
        private final Map<DfaVariableValue, TypeConstraint> myResult = new HashMap<>();
        private final PsiElement myForPlace;

        private ExpressionTypeInstructionVisitor(@Nonnull PsiElement forPlace) {
            myForPlace = PsiUtil.skipParenthesizedExprUp(forPlace);
        }

        MultiMap<PsiExpression, PsiType> getResult() {
            MultiMap<PsiExpression, PsiType> result =
                MultiMap.createSet(Maps.newHashMap(ExpressionVariableDescriptor.EXPRESSION_HASHING_STRATEGY));
            Project project = myForPlace.getProject();
            myResult.forEach((value, constraint) -> {
                if (value.getDescriptor() instanceof ExpressionVariableDescriptor) {
                    PsiExpression expression = ((ExpressionVariableDescriptor)value.getDescriptor()).getExpression();
                    PsiType type = constraint.getPsiType(project);
                    if (type instanceof PsiIntersectionType) {
                        result.putValues(expression, Arrays.asList(((PsiIntersectionType)type).getConjuncts()));
                    }
                    else if (type != null) {
                        result.putValue(expression, type);
                    }
                }
            });
            return result;
        }

        @Override
        protected void beforeExpressionPush(
            @Nonnull DfaValue value,
            @Nonnull PsiExpression expression,
            @Nullable TextRange range,
            @Nonnull DfaMemoryState state
        ) {
            if (range == null && myForPlace == expression) {
                ((DfaMemoryStateImpl)state).forRecordedVariableTypes((var, dfType) -> {
                    myResult.merge(var, TypeConstraint.fromDfType(dfType), TypeConstraint::join);
                });
            }
            super.beforeExpressionPush(value, expression, range, state);
        }
    }
}
