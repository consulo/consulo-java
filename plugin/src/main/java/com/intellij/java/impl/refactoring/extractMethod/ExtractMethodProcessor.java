/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.extractMethod;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.*;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValue;
import com.intellij.java.analysis.impl.refactoring.extractMethod.ExtractMethodUtil;
import com.intellij.java.analysis.impl.refactoring.extractMethod.InputVariables;
import com.intellij.java.analysis.impl.refactoring.util.VariableData;
import com.intellij.java.analysis.impl.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.java.analysis.impl.refactoring.util.duplicates.Match;
import com.intellij.java.analysis.impl.refactoring.util.duplicates.VariableReturnValue;
import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.AnonymousTargetClassPreselectionUtil;
import com.intellij.java.impl.psi.impl.source.codeStyle.JavaCodeStyleManagerImpl;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.extractMethodObject.ExtractMethodObjectHandler;
import com.intellij.java.impl.refactoring.introduceField.ElementToWorkOn;
import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.java.impl.refactoring.util.InlineUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.classMembers.ElementNeedsThis;
import com.intellij.java.impl.refactoring.util.duplicates.MatchProvider;
import com.intellij.java.impl.refactoring.util.duplicates.MatchUtil;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import com.intellij.java.language.impl.psi.controlFlow.ControlFlowUtil;
import com.intellij.java.language.impl.psi.scope.processor.VariablesProcessor;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.*;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.progress.ProgressManager;
import consulo.codeEditor.*;
import consulo.content.scope.SearchScope;
import consulo.dataContext.DataManager;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.refactoring.util.RefactoringMessageDialog;
import consulo.language.editor.ui.PopupNavigationUtil;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ProjectPropertiesComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.popup.JBPopup;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.function.Consumer;

import static com.intellij.java.language.codeInsight.AnnotationUtil.CHECK_TYPE;

public class ExtractMethodProcessor implements MatchProvider {
    private static final Logger LOG = Logger.getInstance(ExtractMethodProcessor.class);

    protected final Project myProject;
    private final Editor myEditor;
    protected final PsiElement[] myElements;
    private final PsiBlockStatement myEnclosingBlockStatement;
    private final PsiType myForcedReturnType;
    private final LocalizeValue myRefactoringName;
    protected final String myInitialMethodName;
    private final String myHelpId;

    private final PsiManager myManager;
    private final PsiElementFactory myElementFactory;
    private final CodeStyleManager myStyleManager;

    private PsiExpression myExpression;

    private PsiElement myCodeFragmentMember; // parent of myCodeFragment

    protected String myMethodName; // name for extracted method
    protected PsiType myReturnType; // return type for extracted method
    protected PsiTypeParameterList myTypeParameterList; //type parameter list of extracted method
    private VariableData[] myVariableDatum; // parameter data for extracted method
    protected PsiClassType[] myThrownExceptions; // exception to declare as thrown by extracted method
    protected boolean myStatic; // whether to declare extracted method static

    protected PsiClass myTargetClass; // class to create the extracted method in
    private PsiElement myAnchor; // anchor to insert extracted method after it

    protected ControlFlowWrapper myControlFlowWrapper;
    protected InputVariables myInputVariables; // input variables
    protected PsiVariable[] myOutputVariables; // output variables
    protected PsiVariable myOutputVariable; // the only output variable
    private PsiVariable myArtificialOutputVariable;
    private Collection<PsiStatement> myExitStatements;

    private boolean myHasReturnStatement; // there is a return statement
    private boolean myHasReturnStatementOutput; // there is a return statement and its type is not void
    protected boolean myHasExpressionOutput; // extracted code is an expression with non-void type
    private boolean myNeedChangeContext; // target class is not immediate container of the code to be extracted

    private boolean myShowErrorDialogs = true;
    protected boolean myCanBeStatic;
    protected boolean myCanBeChainedConstructor;
    protected boolean myIsChainedConstructor;
    private List<Match> myDuplicates;
    @PsiModifier.ModifierConstant
    private String myMethodVisibility = PsiModifier.PRIVATE;
    protected boolean myGenerateConditionalExit;
    protected PsiStatement myFirstExitStatementCopy;
    private PsiMethod myExtractedMethod;
    private PsiMethodCallExpression myMethodCall;
    protected boolean myNullConditionalCheck = false;
    protected boolean myNotNullConditionalCheck = false;
    protected Nullability myNullability;

    @RequiredReadAction
    public ExtractMethodProcessor(
        Project project,
        Editor editor,
        PsiElement[] elements,
        PsiType forcedReturnType,
        LocalizeValue refactoringName,
        String initialMethodName,
        String helpId
    ) {
        myProject = project;
        myEditor = editor;
        if (elements.length != 1 || !(elements[0] instanceof PsiBlockStatement)) {
            myElements = elements.length == 1 && elements[0] instanceof PsiParenthesizedExpression parenthesized
                ? new PsiElement[]{PsiUtil.skipParenthesizedExprDown(parenthesized)}
                : elements;
            myEnclosingBlockStatement = null;
        }
        else {
            myEnclosingBlockStatement = (PsiBlockStatement) elements[0];
            PsiElement[] codeBlockChildren = myEnclosingBlockStatement.getCodeBlock().getChildren();
            myElements = processCodeBlockChildren(codeBlockChildren);
        }
        myForcedReturnType = forcedReturnType;
        myRefactoringName = refactoringName;
        myInitialMethodName = initialMethodName;
        myHelpId = helpId;

        myManager = PsiManager.getInstance(myProject);
        myElementFactory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
        myStyleManager = CodeStyleManager.getInstance(myProject);
    }

    private static PsiElement[] processCodeBlockChildren(PsiElement[] codeBlockChildren) {
        int resultLast = codeBlockChildren.length;

        if (codeBlockChildren.length == 0) {
            return PsiElement.EMPTY_ARRAY;
        }

        int resultStart = 0;
        if (codeBlockChildren[0] instanceof PsiJavaToken firstToken && firstToken.getTokenType() == JavaTokenType.LBRACE) {
            resultStart++;
        }
        PsiElement last = codeBlockChildren[codeBlockChildren.length - 1];
        if (last instanceof PsiJavaToken lastToken && lastToken.getTokenType() == JavaTokenType.RBRACE) {
            resultLast--;
        }
        List<PsiElement> result = new ArrayList<>();
        for (int i = resultStart; i < resultLast; i++) {
            PsiElement element = codeBlockChildren[i];
            if (!(element instanceof PsiWhiteSpace)) {
                result.add(element);
            }
        }

        return PsiUtilCore.toPsiElementArray(result);
    }

    /**
     * Method for test purposes
     */
    public void setShowErrorDialogs(boolean showErrorDialogs) {
        myShowErrorDialogs = showErrorDialogs;
    }

    public void setChainedConstructor(boolean isChainedConstructor) {
        myIsChainedConstructor = isChainedConstructor;
    }

    @RequiredUIAccess
    public boolean prepare() throws PrepareFailedException {
        return prepare(null);
    }

    /**
     * Invoked in atomic action
     */
    @RequiredUIAccess
    public boolean prepare(@Nullable Consumer<ExtractMethodProcessor> pass) throws PrepareFailedException {
        myExpression = null;
        if (myElements.length == 1 && myElements[0] instanceof PsiExpression expression) {
            if (expression instanceof PsiAssignmentExpression && expression.getParent() instanceof PsiExpressionStatement) {
                myElements[0] = expression.getParent();
            }
            else {
                myExpression = expression;
            }
        }

        PsiElement codeFragment = ControlFlowUtil.findCodeFragment(myElements[0]);
        myCodeFragmentMember = codeFragment.getUserData(ElementToWorkOn.PARENT);
        if (myCodeFragmentMember == null) {
            myCodeFragmentMember = codeFragment.getParent();
        }
        if (myCodeFragmentMember == null) {
            myCodeFragmentMember = ControlFlowUtil.findCodeFragment(codeFragment.getContext()).getParent();
        }

        myControlFlowWrapper = new ControlFlowWrapper(codeFragment, myElements);

        try {
            myExitStatements = myControlFlowWrapper.prepareExitStatements(myElements);
            if (myControlFlowWrapper.isGenerateConditionalExit()) {
                myGenerateConditionalExit = true;
            }
            else {
                myHasReturnStatement = myExpression == null && myControlFlowWrapper.isReturnPresentBetween();
            }
            myFirstExitStatementCopy = myControlFlowWrapper.getFirstExitStatementCopy();
        }
        catch (ControlFlowWrapper.ExitStatementsNotSameException e) {
            myExitStatements = myControlFlowWrapper.getExitStatements();
            myNotNullConditionalCheck = areAllExitPointsAreNotNull(getExpectedReturnType());
            if (!myNotNullConditionalCheck) {
                showMultipleExitPointsMessage();
                return false;
            }
        }

        myOutputVariables = myControlFlowWrapper.getOutputVariables();

        return chooseTargetClass(codeFragment, pass);
    }

    @RequiredUIAccess
    private boolean checkExitPoints() throws PrepareFailedException {
        PsiType expressionType = null;
        if (myExpression != null) {
            if (myForcedReturnType != null) {
                expressionType = myForcedReturnType;
            }
            else {
                expressionType = RefactoringUtil.getTypeByExpressionWithExpectedType(myExpression);
                if (expressionType == null && !(myExpression.getParent() instanceof PsiExpressionStatement)) {
                    expressionType = PsiType.getJavaLangObject(myExpression.getManager(), GlobalSearchScope.allScope(myProject));
                }
            }
        }
        if (expressionType == null) {
            expressionType = PsiType.VOID;
        }
        myHasExpressionOutput = !PsiType.VOID.equals(expressionType);

        PsiType returnStatementType = getExpectedReturnType();
        myHasReturnStatementOutput = myHasReturnStatement && returnStatementType != null && !PsiType.VOID.equals(returnStatementType);

        if (myGenerateConditionalExit && myOutputVariables.length == 1) {
            if (!(myOutputVariables[0].getType() instanceof PsiPrimitiveType)) {
                myNullConditionalCheck = true;
                for (PsiStatement exitStatement : myExitStatements) {
                    if (exitStatement instanceof PsiReturnStatement returnStmt) {
                        PsiExpression returnValue = returnStmt.getReturnValue();
                        myNullConditionalCheck &= returnValue == null || isNullInferred(returnValue.getText(), true);
                    }
                }
                myNullConditionalCheck &= isNullInferred(myOutputVariables[0].getName(), false);
            }

            myNotNullConditionalCheck = areAllExitPointsAreNotNull(returnStatementType);
        }

        if (!myHasReturnStatementOutput && checkOutputVariablesCount() && !myNullConditionalCheck && !myNotNullConditionalCheck) {
            showMultipleOutputMessage(expressionType);
            return false;
        }

        myOutputVariable = myOutputVariables.length > 0 ? myOutputVariables[0] : null;
        if (myNotNullConditionalCheck) {
            myReturnType = returnStatementType instanceof PsiPrimitiveType primitiveType
                ? primitiveType.getBoxedType(myCodeFragmentMember) : returnStatementType;
        }
        else if (myHasReturnStatementOutput) {
            myReturnType = returnStatementType;
        }
        else if (myOutputVariable != null) {
            myReturnType = myOutputVariable.getType();
        }
        else if (myGenerateConditionalExit) {
            myReturnType = PsiType.BOOLEAN;
        }
        else {
            myReturnType = expressionType;
        }

        PsiElement container = PsiTreeUtil.getParentOfType(myElements[0], PsiClass.class, PsiMethod.class);
        while (container instanceof PsiMethod method && method.getContainingClass() != myTargetClass) {
            container = PsiTreeUtil.getParentOfType(method, PsiMethod.class, true);
        }
        if (container instanceof PsiMethod method) {
            PsiElement[] elements = myElements;
            if (myExpression == null) {
                if (myOutputVariable != null) {
                    elements = ArrayUtil.append(myElements, myOutputVariable, PsiElement.class);
                }
                if (myCodeFragmentMember instanceof PsiMethod codeFragmentMethod && myReturnType == codeFragmentMethod.getReturnType()) {
                    elements = ArrayUtil.append(myElements, codeFragmentMethod.getReturnTypeElement(), PsiElement.class);
                }
            }
            myTypeParameterList = RefactoringUtil.createTypeParameterListWithUsedTypeParameters(method.getTypeParameterList(), elements);
        }
        List<PsiClassType> exceptions = ExceptionUtil.getThrownCheckedExceptions(myElements);
        myThrownExceptions = exceptions.toArray(new PsiClassType[exceptions.size()]);

        if (container instanceof PsiMethod containerMethod) {
            checkLocalClasses(containerMethod);
        }
        return true;
    }

    private PsiType getExpectedReturnType() {
        return myCodeFragmentMember instanceof PsiMethod codeFragmentMethod
            ? codeFragmentMethod.getReturnType()
            : myCodeFragmentMember instanceof PsiLambdaExpression codeFragmentLambda
            ? LambdaUtil.getFunctionalInterfaceReturnType(codeFragmentLambda)
            : null;
    }

    @Nullable
    private PsiVariable getArtificialOutputVariable() {
        if (myOutputVariables.length == 0 && myExitStatements.isEmpty()) {
            if (myCanBeChainedConstructor) {
                final Set<PsiField> fields = new HashSet<>();
                for (PsiElement element : myElements) {
                    element.accept(new JavaRecursiveElementWalkingVisitor() {
                        @Override
                        @RequiredReadAction
                        public void visitReferenceExpression(PsiReferenceExpression expression) {
                            super.visitReferenceExpression(expression);
                            if (expression.resolve() instanceof PsiField field
                                && field.isFinal()
                                && PsiUtil.isAccessedForWriting(expression)) {
                                fields.add(field);
                            }
                        }
                    });
                }
                if (!fields.isEmpty()) {
                    return fields.size() == 1 ? fields.iterator().next() : null;
                }
            }
            VariablesProcessor processor = new VariablesProcessor(true) {
                @Override
                @RequiredReadAction
                protected boolean check(PsiVariable var, ResolveState state) {
                    return isDeclaredInside(var);
                }
            };
            PsiScopesUtil.treeWalkUp(processor, myElements[myElements.length - 1], myCodeFragmentMember);
            if (processor.size() == 1) {
                return processor.getResult(0);
            }
        }
        return null;
    }

    @RequiredReadAction
    private boolean areAllExitPointsAreNotNull(PsiType returnStatementType) {
        if (insertNotNullCheckIfPossible() && myControlFlowWrapper.getOutputVariables(false).length == 0) {
            boolean isNotNull = returnStatementType != null && !PsiType.VOID.equals(returnStatementType);
            for (PsiStatement statement : myExitStatements) {
                if (statement instanceof PsiReturnStatement returnStmt) {
                    PsiExpression returnValue = returnStmt.getReturnValue();
                    isNotNull &= returnValue != null && !isNullInferred(returnValue.getText(), true);
                }
            }
            return isNotNull;
        }
        return false;
    }

    protected boolean insertNotNullCheckIfPossible() {
        return true;
    }

    @RequiredReadAction
    private boolean isNullInferred(String exprText, boolean trueSet) {
        PsiCodeBlock block = myElementFactory.createCodeBlockFromText("{}", myElements[0]);
        for (PsiElement element : myElements) {
            block.add(element);
        }
        PsiReturnStatement statementFromText =
            (PsiReturnStatement) myElementFactory.createStatementFromText("return " + exprText + ";", null);
        statementFromText = (PsiReturnStatement) block.add(statementFromText);

        return inferNullability(block, Objects.requireNonNull(statementFromText.getReturnValue())) == Nullability.NOT_NULL;
    }

    @RequiredReadAction
    private static Nullability inferNullability(@Nonnull PsiCodeBlock block, @Nonnull PsiExpression expr) {
        DataFlowRunner dfaRunner = new DataFlowRunner(block.getProject());

        class Visitor extends StandardInstructionVisitor {
            DfaNullability myNullability = DfaNullability.NOT_NULL;
            boolean myVisited = false;

            @Override
            protected void beforeExpressionPush(
                @Nonnull DfaValue value,
                @Nonnull PsiExpression expression,
                @Nullable TextRange range,
                @Nonnull DfaMemoryState state
            ) {
                if (expression == expr && range == null) {
                    myVisited = true;
                    myNullability = myNullability.unite(DfaNullability.fromDfType(state.getDfType(value)));
                }
            }
        }
        Visitor visitor = new Visitor();
        RunnerResult rc = dfaRunner.analyzeMethod(block, visitor);
        return rc == RunnerResult.OK && visitor.myVisited ? DfaNullability.toNullability(visitor.myNullability) : Nullability.UNKNOWN;
    }

    protected boolean checkOutputVariablesCount() {
        int outputCount = (myHasExpressionOutput ? 1 : 0) + (myGenerateConditionalExit ? 1 : 0) + myOutputVariables.length;
        return outputCount > 1;
    }

    private void checkCanBeChainedConstructor() {
        if (!(myCodeFragmentMember instanceof PsiMethod method)) {
            return;
        }
        if (!method.isConstructor() || !PsiType.VOID.equals(myReturnType)) {
            return;
        }
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return;
        }
        PsiStatement[] psiStatements = body.getStatements();
        if (psiStatements.length > 0 && myElements[0] == psiStatements[0]) {
            myCanBeChainedConstructor = true;
        }
    }

    @RequiredReadAction
    private void checkLocalClasses(PsiMethod container) throws PrepareFailedException {
        final List<PsiClass> localClasses = new ArrayList<>();
        container.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitClass(@Nonnull PsiClass aClass) {
                localClasses.add(aClass);
            }

            @Override
            public void visitAnonymousClass(@Nonnull PsiAnonymousClass aClass) {
                visitElement(aClass);
            }

            @Override
            public void visitTypeParameter(@Nonnull PsiTypeParameter classParameter) {
                visitElement(classParameter);
            }
        });
        for (PsiClass localClass : localClasses) {
            boolean classExtracted = isExtractedElement(localClass);
            List<PsiElement> extractedReferences = Collections.synchronizedList(new ArrayList<PsiElement>());
            List<PsiElement> remainingReferences = Collections.synchronizedList(new ArrayList<PsiElement>());
            ReferencesSearch.search(localClass).forEach(psiReference -> {
                PsiElement element = psiReference.getElement();
                boolean elementExtracted = isExtractedElement(element);
                if (elementExtracted && !classExtracted) {
                    extractedReferences.add(element);
                    return false;
                }
                if (!elementExtracted && classExtracted) {
                    remainingReferences.add(element);
                    return false;
                }
                return true;
            });
            if (!extractedReferences.isEmpty()) {
                throw new PrepareFailedException(
                    "Cannot extract method because the selected code fragment uses local classes defined outside of the fragment",
                    extractedReferences.get(0)
                );
            }
            if (!remainingReferences.isEmpty()) {
                throw new PrepareFailedException(
                    "Cannot extract method because the selected code fragment defines local classes used outside of the fragment",
                    remainingReferences.get(0)
                );
            }
            if (classExtracted) {
                for (PsiVariable variable : myControlFlowWrapper.getUsedVariables()) {
                    if (isDeclaredInside(variable) && !variable.equals(myOutputVariable) && PsiUtil.resolveClassInType(variable.getType()) == localClass) {
                        throw new PrepareFailedException(
                            "Cannot extract method because the selected code fragment defines variable of local class type used outside of the fragment",
                            variable
                        );
                    }
                }
            }
        }
    }

    private boolean isExtractedElement(PsiElement element) {
        boolean isExtracted = false;
        for (PsiElement psiElement : myElements) {
            if (PsiTreeUtil.isAncestor(psiElement, element, false)) {
                isExtracted = true;
                break;
            }
        }
        return isExtracted;
    }

    private boolean shouldBeStatic() {
        for (PsiElement element : myElements) {
            PsiExpressionStatement statement = PsiTreeUtil.getParentOfType(element, PsiExpressionStatement.class);
            if (statement != null && JavaHighlightUtil.isSuperOrThisCall(statement, true, true)) {
                return true;
            }
        }
        PsiElement codeFragmentMember = myCodeFragmentMember;
        while (codeFragmentMember != null && PsiTreeUtil.isAncestor(myTargetClass, codeFragmentMember, true)) {
            if (codeFragmentMember instanceof PsiModifierListOwner modifierListOwner
                && modifierListOwner.hasModifierProperty(PsiModifier.STATIC)) {
                return true;
            }
            codeFragmentMember = PsiTreeUtil.getParentOfType(codeFragmentMember, PsiModifierListOwner.class, true);
        }
        return false;
    }

    @RequiredUIAccess
    public boolean showDialog(boolean direct) {
        AbstractExtractDialog dialog = createExtractMethodDialog(direct);
        dialog.show();
        if (!dialog.isOK()) {
            return false;
        }
        apply(dialog);
        return true;
    }

    protected void apply(AbstractExtractDialog dialog) {
        myMethodName = dialog.getChosenMethodName();
        myVariableDatum = dialog.getChosenParameters();
        myStatic = isStatic() | dialog.isMakeStatic();
        myIsChainedConstructor = dialog.isChainedConstructor();
        myMethodVisibility = dialog.getVisibility();

        PsiType returnType = dialog.getReturnType();
        if (returnType != null) {
            myReturnType = returnType;
        }
    }

    @RequiredUIAccess
    protected AbstractExtractDialog createExtractMethodDialog(final boolean direct) {
        List<VariableData> variables = myInputVariables.getInputVariables();
        myVariableDatum = variables.toArray(new VariableData[variables.size()]);
        myNullability = initNullability();
        myArtificialOutputVariable = PsiType.VOID.equals(myReturnType) ? getArtificialOutputVariable() : null;
        final PsiType returnType = myArtificialOutputVariable != null ? myArtificialOutputVariable.getType() : myReturnType;
        return new ExtractMethodDialog(
            myProject,
            myTargetClass,
            myInputVariables,
            returnType,
            getTypeParameterList(),
            getThrownExceptions(),
            isStatic(),
            isCanBeStatic(),
            myCanBeChainedConstructor,
            myRefactoringName.get(),
            myHelpId,
            myNullability,
            myElements
        ) {
            @Override
            protected boolean areTypesDirected() {
                return direct;
            }

            @Override
            @RequiredReadAction
            protected String[] suggestMethodNames() {
                return suggestInitialMethodName();
            }

            @Override
            protected PsiExpression[] findOccurrences() {
                return ExtractMethodProcessor.this.findOccurrences();
            }

            @Override
            protected boolean isOutputVariable(PsiVariable var) {
                return ExtractMethodProcessor.this.isOutputVariable(var);
            }

            @Override
            protected boolean isVoidReturn() {
                return myArtificialOutputVariable != null && !(myArtificialOutputVariable instanceof PsiField);
            }

            @Override
            @RequiredReadAction
            protected void checkMethodConflicts(MultiMap<PsiElement, LocalizeValue> conflicts) {
                super.checkMethodConflicts(conflicts);
                VariableData[] parameters = getChosenParameters();
                final Map<String, PsiLocalVariable> vars = new HashMap<>();
                for (PsiElement element : myElements) {
                    element.accept(new JavaRecursiveElementWalkingVisitor() {
                        @Override
                        @RequiredReadAction
                        public void visitLocalVariable(@Nonnull PsiLocalVariable variable) {
                            super.visitLocalVariable(variable);
                            vars.put(variable.getName(), variable);
                        }

                        @Override
                        public void visitClass(@Nonnull PsiClass aClass) {
                        }
                    });
                }
                for (VariableData parameter : parameters) {
                    String paramName = parameter.name;
                    PsiLocalVariable variable = vars.get(paramName);
                    if (variable != null) {
                        conflicts.putValue(
                            variable,
                            LocalizeValue.localizeTODO("Variable with name " + paramName + " is already defined in the selected scope")
                        );
                    }
                }
            }
        };
    }

    public PsiExpression[] findOccurrences() {
        if (myExpression != null) {
            return new PsiExpression[]{myExpression};
        }
        if (myOutputVariable != null) {
            PsiElement scope = myOutputVariable instanceof PsiLocalVariable localVariable
                ? RefactoringUtil.getVariableScope(localVariable)
                : PsiTreeUtil.findCommonParent(myElements);
            return CodeInsightUtil.findReferenceExpressions(scope, myOutputVariable);
        }
        List<PsiStatement> filter = ContainerUtil.filter(
            myExitStatements,
            statement -> statement instanceof PsiReturnStatement returnStatement && returnStatement.getReturnValue() != null
        );
        List<PsiExpression> map = ContainerUtil.map(filter, statement -> ((PsiReturnStatement) statement).getReturnValue());
        return map.toArray(new PsiExpression[map.size()]);
    }

    @RequiredReadAction
    private Nullability initNullability() {
        if (!PsiUtil.isLanguageLevel5OrHigher(myElements[0]) || PsiUtil.resolveClassInType(myReturnType) == null) {
            return null;
        }
        NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);
        PsiClass nullableAnnotationClass = JavaPsiFacade.getInstance(myProject)
            .findClass(manager.getDefaultNullable(), myElements[0].getResolveScope());
        //noinspection RequiredXAction
        if (nullableAnnotationClass != null) {
            PsiElement elementInCopy = myTargetClass.getContainingFile().copy().findElementAt(myTargetClass.getTextOffset());
            PsiClass classCopy = PsiTreeUtil.getParentOfType(elementInCopy, PsiClass.class);
            if (classCopy == null) {
                return null;
            }
            PsiMethod emptyMethod = (PsiMethod) classCopy.addAfter(generateEmptyMethod("name", null), classCopy.getLBrace());
            prepareMethodBody(emptyMethod, false);
            if (myNotNullConditionalCheck || myNullConditionalCheck) {
                return Nullability.NULLABLE;
            }
            return DfaUtil.inferMethodNullability(emptyMethod);
        }
        return null;
    }

    @RequiredReadAction
    protected String[] suggestInitialMethodName() {
        if (StringUtil.isEmpty(myInitialMethodName)) {
            Set<String> initialMethodNames = new LinkedHashSet<>();
            JavaCodeStyleManagerImpl codeStyleManager = (JavaCodeStyleManagerImpl) JavaCodeStyleManager.getInstance(myProject);
            if (myExpression != null || !(myReturnType instanceof PsiPrimitiveType)) {
                String[] names = codeStyleManager.suggestVariableName(VariableKind.FIELD, null, myExpression, myReturnType).names;
                for (String name : names) {
                    initialMethodNames.add(codeStyleManager.variableNameToPropertyName(name, VariableKind.FIELD));
                }
            }

            if (myOutputVariable != null) {
                VariableKind outKind = codeStyleManager.getVariableKind(myOutputVariable);
                SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(
                    VariableKind.FIELD,
                    codeStyleManager.variableNameToPropertyName(myOutputVariable.getName(), outKind),
                    null,
                    myOutputVariable.getType()
                );
                for (String name : nameInfo.names) {
                    initialMethodNames.add(codeStyleManager.variableNameToPropertyName(name, VariableKind.FIELD));
                }
            }

            String nameByComment = getNameByComment();
            PsiField field = JavaPsiFacade.getElementFactory(myProject).createField(
                "fieldNameToReplace",
                myReturnType instanceof PsiEllipsisType ellipsisType ? ellipsisType.toArrayType() : myReturnType
            );
            List<String> getters = new ArrayList<>(ContainerUtil.map(
                initialMethodNames,
                propertyName -> {
                    if (!PsiNameHelper.getInstance(myProject).isIdentifier(propertyName)) {
                        LOG.info(propertyName + "; " + myExpression);
                        return null;
                    }
                    field.setName(propertyName);
                    return PropertyUtil.suggestGetterName(field);
                }
            ));
            ContainerUtil.addIfNotNull(getters, nameByComment);
            return ArrayUtil.toStringArray(getters);
        }
        return new String[]{myInitialMethodName};
    }

    @RequiredReadAction
    private String getNameByComment() {
        PsiElement prevSibling = PsiTreeUtil.skipSiblingsBackward(myElements[0], PsiWhiteSpace.class);
        if (prevSibling instanceof PsiComment comment && comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
            String text = StringUtil.decapitalize(StringUtil.capitalizeWords(prevSibling.getText().trim().substring(2), true))
                .replaceAll(" ", "");
            if (PsiNameHelper.getInstance(myProject).isIdentifier(text) && text.length() < 20) {
                return text;
            }
        }
        return null;
    }

    public boolean isOutputVariable(PsiVariable var) {
        return ArrayUtil.find(myOutputVariables, var) != -1;
    }

    @RequiredUIAccess
    public boolean showDialog() {
        return showDialog(true);
    }

    @TestOnly
    @RequiredReadAction
    public void testRun() throws IncorrectOperationException {
        testPrepare();
        testNullness();
        ExtractMethodHandler.run(myProject, myEditor, this);
    }

    @TestOnly
    @RequiredReadAction
    public void testNullness() {
        myNullability = initNullability();
    }

    @TestOnly
    public void testPrepare() {
        myInputVariables.setFoldingAvailable(myInputVariables.isFoldingSelectedByDefault());
        myMethodName = myInitialMethodName;
        myVariableDatum = new VariableData[myInputVariables.getInputVariables().size()];
        for (int i = 0; i < myInputVariables.getInputVariables().size(); i++) {
            myVariableDatum[i] = myInputVariables.getInputVariables().get(i);
        }
    }

    @TestOnly
    public void testPrepare(PsiType returnType, boolean makeStatic) throws PrepareFailedException {
        if (makeStatic) {
            if (!isCanBeStatic()) {
                throw new PrepareFailedException("Failed to make static", myElements[0]);
            }
            myInputVariables.setPassFields(true);
            myStatic = true;
        }
        if (PsiType.VOID.equals(myReturnType)) {
            myArtificialOutputVariable = getArtificialOutputVariable();
        }
        testPrepare();
        if (returnType != null) {
            myReturnType = returnType;
        }
    }

    @TestOnly
    public void doNotPassParameter(int i) {
        myVariableDatum[i].passAsParameter = false;
    }

    @TestOnly
    public void changeParamName(int i, String param) {
        myVariableDatum[i].name = param;
    }

    /**
     * Invoked in command and in atomic action
     */
    @RequiredUIAccess
    public void doRefactoring() throws IncorrectOperationException {
        initDuplicates();

        chooseAnchor();

        LogicalPosition pos1;
        if (myEditor != null) {
            int col = myEditor.getCaretModel().getLogicalPosition().column;
            int line = myEditor.getCaretModel().getLogicalPosition().line;
            pos1 = new LogicalPosition(line, col);
            LogicalPosition pos = new LogicalPosition(0, 0);
            myEditor.getCaretModel().moveToLogicalPosition(pos);
        }
        else {
            pos1 = null;
        }

        SearchScope processConflictsScope = myMethodVisibility.equals(PsiModifier.PRIVATE)
            ? new LocalSearchScope(myTargetClass) : GlobalSearchScope.projectScope(myProject);

        Map<PsiMethodCallExpression, PsiMethod> overloadsResolveMap = new HashMap<>();
        Runnable collectOverloads = () -> myProject.getApplication().runReadAction(() -> {
            Map<PsiMethodCallExpression, PsiMethod> overloads =
                ExtractMethodUtil.encodeOverloadTargets(myTargetClass, processConflictsScope, myMethodName, myCodeFragmentMember);
            overloadsResolveMap.putAll(overloads);
        });
        Runnable extract = () -> {
            doExtract();
            ExtractMethodUtil.decodeOverloadTargets(overloadsResolveMap, myExtractedMethod, myCodeFragmentMember);
        };
        if (myProject.getApplication().isWriteAccessAllowed()) {
            collectOverloads.run();
            extract.run();
        }
        else if (ProgressManager.getInstance().runProcessWithProgressSynchronously(
            collectOverloads,
            LocalizeValue.localizeTODO("Collect overloads..."),
            true,
            myProject
        )) {
            myProject.getApplication().runWriteAction(extract);
        }
        else {
            return;
        }

        if (myEditor != null) {
            myEditor.getCaretModel().moveToLogicalPosition(pos1);
            int offset = myMethodCall.getMethodExpression().getTextRange().getStartOffset();
            myEditor.getCaretModel().moveToOffset(offset);
            myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            myEditor.getSelectionModel().removeSelection();
        }
    }

    @Nullable
    private DuplicatesFinder initDuplicates() {
        List<PsiElement> elements = new ArrayList<>();
        for (PsiElement element : myElements) {
            if (!(element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
                elements.add(element);
            }
        }

        if (myExpression != null) {
            DuplicatesFinder finder =
                new DuplicatesFinder(PsiUtilCore.toPsiElementArray(elements), myInputVariables.copy(), new ArrayList<>());
            myDuplicates = finder.findDuplicates(myTargetClass);
            return finder;
        }
        else if (elements.size() > 0) {
            DuplicatesFinder myDuplicatesFinder = new DuplicatesFinder(PsiUtilCore.toPsiElementArray(elements), myInputVariables.copy(),
                myOutputVariable != null ? new VariableReturnValue(myOutputVariable) : null, Arrays.asList(myOutputVariables)
            );
            myDuplicates = myDuplicatesFinder.findDuplicates(myTargetClass);
            return myDuplicatesFinder;
        }
        else {
            myDuplicates = new ArrayList<>();
        }
        return null;
    }

    @RequiredWriteAction
    public void doExtract() throws IncorrectOperationException {
        PsiMethod newMethod = generateEmptyMethod();

        myExpression = myInputVariables.replaceWrappedReferences(myElements, myExpression);
        renameInputVariables();

        LOG.assertTrue(myElements[0].isValid());

        PsiCodeBlock body = newMethod.getBody();
        myMethodCall = generateMethodCall(null, true);

        LOG.assertTrue(myElements[0].isValid());

        PsiStatement exitStatementCopy = prepareMethodBody(newMethod, true);

        if (myExpression == null) {
            if (myNeedChangeContext && isNeedToChangeCallContext()) {
                for (PsiElement element : myElements) {
                    ChangeContextUtil.encodeContextInfo(element, false);
                }
            }

            if (myNullConditionalCheck) {
                String varName = myOutputVariable.getName();
                if (isDeclaredInside(myOutputVariable)) {
                    declareVariableAtMethodCallLocation(varName);
                }
                else {
                    PsiExpressionStatement assignmentExpression =
                        (PsiExpressionStatement) myElementFactory.createStatementFromText(varName + "=x;", null);
                    assignmentExpression = (PsiExpressionStatement) addToMethodCallLocation(assignmentExpression);
                    myMethodCall = (PsiMethodCallExpression) ((PsiAssignmentExpression) assignmentExpression.getExpression())
                        .getRExpression().replace(myMethodCall);
                }
                declareNecessaryVariablesAfterCall(myOutputVariable);
                PsiIfStatement ifStatement;
                if (myHasReturnStatementOutput) {
                    ifStatement =
                        (PsiIfStatement) myElementFactory.createStatementFromText("if (" + varName + "==null) return null;", null);
                }
                else if (myGenerateConditionalExit) {
                    if (myFirstExitStatementCopy instanceof PsiReturnStatement returnStmt && returnStmt.getReturnValue() != null) {
                        ifStatement =
                            (PsiIfStatement) myElementFactory.createStatementFromText("if (" + varName + "==null) return null;", null);
                    }
                    else {
                        ifStatement = (PsiIfStatement) myElementFactory.createStatementFromText(
                            "if (" + varName + "==null) " + myFirstExitStatementCopy.getText(),
                            null
                        );
                    }
                }
                else {
                    ifStatement = (PsiIfStatement) myElementFactory.createStatementFromText("if (" + varName + "==null) return;", null);
                }
                ifStatement = (PsiIfStatement) addToMethodCallLocation(ifStatement);
                CodeStyleManager.getInstance(myProject).reformat(ifStatement);
            }
            else if (myNotNullConditionalCheck) {
                String varName = myOutputVariable != null ? myOutputVariable.getName() : "x";
                varName = declareVariableAtMethodCallLocation(
                    varName,
                    myReturnType instanceof PsiPrimitiveType primitiveType ? primitiveType.getBoxedType(myCodeFragmentMember) : myReturnType
                );
                addToMethodCallLocation(myElementFactory.createStatementFromText(
                    "if (" + varName + " != null) return " + varName + ";",
                    null
                ));
            }
            else if (myGenerateConditionalExit) {
                PsiIfStatement ifStatement = (PsiIfStatement) myElementFactory.createStatementFromText("if (a) b;", null);
                ifStatement = (PsiIfStatement) addToMethodCallLocation(ifStatement);
                myMethodCall = (PsiMethodCallExpression) ifStatement.getCondition().replace(myMethodCall);
                myFirstExitStatementCopy = (PsiStatement) ifStatement.getThenBranch().replace(myFirstExitStatementCopy);
                CodeStyleManager.getInstance(myProject).reformat(ifStatement);
            }
            else if (myOutputVariable != null || isArtificialOutputUsed()) {
                boolean toDeclare =
                    isArtificialOutputUsed() ? !(myArtificialOutputVariable instanceof PsiField) : isDeclaredInside(myOutputVariable);
                String name = isArtificialOutputUsed() ? myArtificialOutputVariable.getName() : myOutputVariable.getName();
                if (!toDeclare) {
                    PsiExpressionStatement statement =
                        (PsiExpressionStatement) myElementFactory.createStatementFromText(name + "=x;", null);
                    statement = (PsiExpressionStatement) myStyleManager.reformat(statement);
                    statement = (PsiExpressionStatement) addToMethodCallLocation(statement);
                    PsiAssignmentExpression assignment = (PsiAssignmentExpression) statement.getExpression();
                    myMethodCall = (PsiMethodCallExpression) assignment.getRExpression().replace(myMethodCall);
                }
                else {
                    declareVariableAtMethodCallLocation(name);
                }
            }
            else if (myHasReturnStatementOutput) {
                PsiStatement statement = myElementFactory.createStatementFromText("return x;", null);
                statement = (PsiStatement) addToMethodCallLocation(statement);
                myMethodCall = (PsiMethodCallExpression) ((PsiReturnStatement) statement).getReturnValue().replace(myMethodCall);
            }
            else {
                PsiStatement statement = myElementFactory.createStatementFromText("x();", null);
                statement = (PsiStatement) addToMethodCallLocation(statement);
                myMethodCall = (PsiMethodCallExpression) ((PsiExpressionStatement) statement).getExpression().replace(myMethodCall);
            }
            if (myHasReturnStatement && !myHasReturnStatementOutput && !hasNormalExit()) {
                PsiStatement statement = myElementFactory.createStatementFromText("return;", null);
                addToMethodCallLocation(statement);
            }
            else if (!myGenerateConditionalExit && exitStatementCopy != null) {
                addToMethodCallLocation(exitStatementCopy);
            }

            if (!myNullConditionalCheck && !myNotNullConditionalCheck) {
                declareNecessaryVariablesAfterCall(myOutputVariable);
            }

            deleteExtracted();
        }
        else {
            PsiExpression expression2Replace = myExpression;
            if (myExpression instanceof PsiAssignmentExpression assignment) {
                expression2Replace = assignment.getRExpression();
            }
            else if (myExpression instanceof PsiPostfixExpression || myExpression instanceof PsiPrefixExpression) {
                IElementType elementType = myExpression instanceof PsiPostfixExpression postfixExpr
                    ? postfixExpr.getOperationTokenType()
                    : ((PsiPrefixExpression) myExpression).getOperationTokenType();
                if (elementType == JavaTokenType.PLUSPLUS || elementType == JavaTokenType.MINUSMINUS) {
                    PsiExpression operand = myExpression instanceof PsiPostfixExpression postfixExpr
                        ? postfixExpr.getOperand()
                        : ((PsiPrefixExpression) myExpression).getOperand();
                    expression2Replace = ((PsiBinaryExpression) myExpression.replace(
                        myElementFactory.createExpressionFromText(operand.getText() + " + x", operand))
                    ).getROperand();
                }
            }
            myExpression = (PsiExpression) IntroduceVariableBase.replace(expression2Replace, myMethodCall, myProject);
            myMethodCall = PsiTreeUtil.getParentOfType(
                myExpression.findElementAt(myExpression.getText().indexOf(myMethodCall.getText())),
                PsiMethodCallExpression.class
            );
            declareNecessaryVariablesAfterCall(myOutputVariable);
        }

        if (myAnchor instanceof PsiField field) {
            field.normalizeDeclaration();
        }

        adjustFinalParameters(newMethod);
        int i = 0;
        for (VariableData data : myVariableDatum) {
            if (!data.passAsParameter) {
                continue;
            }
            PsiParameter psiParameter = newMethod.getParameterList().getParameters()[i++];
            PsiType paramType = psiParameter.getType();
            for (PsiReference reference : ReferencesSearch.search(psiParameter, new LocalSearchScope(body))) {
                PsiElement element = reference.getElement();
                if (element != null && element.getParent() instanceof PsiTypeCastExpression typeCastExpression) {
                    PsiTypeElement castType = typeCastExpression.getCastType();
                    if (castType != null && Comparing.equal(castType.getType(), paramType)) {
                        RedundantCastUtil.removeCast(typeCastExpression);
                    }
                }
            }
        }

        if (myNullability != null &&
            PsiUtil.resolveClassInType(newMethod.getReturnType()) != null &&
            ProjectPropertiesComponent.getInstance(myProject).getBoolean(ExtractMethodDialog.EXTRACT_METHOD_GENERATE_ANNOTATIONS, true)) {
            NullableNotNullManager nullManager = NullableNotNullManager.getInstance(myProject);
            switch (myNullability) {
                case NOT_NULL:
                    updateAnnotations(newMethod, nullManager.getNullables(), nullManager.getDefaultNotNull(), nullManager.getNotNulls());
                    break;
                case NULLABLE:
                    updateAnnotations(newMethod, nullManager.getNotNulls(), nullManager.getDefaultNullable(), nullManager.getNullables());
                    break;
                default:
            }
        }

        myExtractedMethod = (PsiMethod) myTargetClass.addAfter(newMethod, myAnchor);
        if (isNeedToChangeCallContext() && myNeedChangeContext) {
            ChangeContextUtil.decodeContextInfo(
                myExtractedMethod,
                myTargetClass,
                RefactoringChangeUtil.createThisExpression(myManager, null)
            );
            if (myMethodCall.resolveMethod() != myExtractedMethod) {
                PsiReferenceExpression methodExpression = myMethodCall.getMethodExpression();
                methodExpression.setQualifierExpression(RefactoringChangeUtil.createThisExpression(myManager, myTargetClass));
            }
        }
    }

    @RequiredWriteAction
    private void updateAnnotations(PsiModifierListOwner owner, List<String> toRemove, String toAdd, List<String> toKeep) {
        AddAnnotationPsiFix.removePhysicalAnnotations(owner, ArrayUtil.toStringArray(toRemove));
        PsiModifierList modifierList = owner.getModifierList();
        if (modifierList != null && !AnnotationUtil.isAnnotated(owner, toKeep, CHECK_TYPE)) {
            PsiAnnotation annotation = AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(toAdd, PsiNameValuePair.EMPTY_ARRAY, modifierList);
            if (annotation != null) {
                JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(annotation);
            }
        }
    }

    @Nullable
    @RequiredWriteAction
    private PsiStatement prepareMethodBody(PsiMethod newMethod, boolean doExtract) {
        PsiCodeBlock body = newMethod.getBody();
        if (myExpression != null) {
            declareNecessaryVariablesInsideBody(body);
            if (myHasExpressionOutput) {
                PsiReturnStatement returnStatement = (PsiReturnStatement) myElementFactory.createStatementFromText("return x;", null);
                PsiExpression returnValue = RefactoringUtil.convertInitializerToNormalExpression(myExpression, myForcedReturnType);
                returnStatement.getReturnValue().replace(returnValue);
                body.add(returnStatement);
            }
            else {
                PsiExpressionStatement statement = (PsiExpressionStatement) myElementFactory.createStatementFromText("x;", null);
                statement.getExpression().replace(myExpression);
                body.add(statement);
            }
            return null;
        }

        boolean hasNormalExit = hasNormalExit();
        String outVariableName = myOutputVariable != null ? getNewVariableName(myOutputVariable) : null;
        PsiReturnStatement returnStatement;
        if (myNullConditionalCheck) {
            returnStatement = (PsiReturnStatement) myElementFactory.createStatementFromText("return null;", null);
        }
        else if (myOutputVariable != null) {
            returnStatement = (PsiReturnStatement) myElementFactory.createStatementFromText("return " + outVariableName + ";", null);
        }
        else if (myGenerateConditionalExit) {
            returnStatement = (PsiReturnStatement) myElementFactory.createStatementFromText("return true;", null);
        }
        else {
            returnStatement = (PsiReturnStatement) myElementFactory.createStatementFromText("return;", null);
        }

        PsiStatement exitStatementCopy = !doExtract || myNotNullConditionalCheck
            ? null : myControlFlowWrapper.getExitStatementCopy(returnStatement, myElements);

        declareNecessaryVariablesInsideBody(body);

        body.addRange(myElements[0], myElements[myElements.length - 1]);
        if (myNullConditionalCheck) {
            body.add(myElementFactory.createStatementFromText("return " + myOutputVariable.getName() + ";", null));
        }
        else if (myNotNullConditionalCheck) {
            body.add(myElementFactory.createStatementFromText("return null;", null));
        }
        else if (myGenerateConditionalExit) {
            body.add(myElementFactory.createStatementFromText("return false;", null));
        }
        else if (!myHasReturnStatement && hasNormalExit && myOutputVariable != null) {
            PsiReturnStatement insertedReturnStatement = (PsiReturnStatement) body.add(returnStatement);
            if (myOutputVariables.length == 1) {
                PsiExpression returnValue = insertedReturnStatement.getReturnValue();
                if (returnValue instanceof PsiReferenceExpression returnRefExpr
                    && returnRefExpr.resolve() instanceof PsiLocalVariable localVar
                    && Comparing.strEqual(localVar.getName(), outVariableName)) {
                    PsiStatement statement = PsiTreeUtil.getPrevSiblingOfType(insertedReturnStatement, PsiStatement.class);
                    if (statement instanceof PsiDeclarationStatement declarationStatement) {
                        PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
                        if (ArrayUtil.find(declaredElements, localVar) != -1) {
                            InlineUtil.inlineVariable(localVar, localVar.getInitializer(), returnRefExpr);
                            localVar.delete();
                        }
                    }
                }
            }
        }
        else if (isArtificialOutputUsed()) {
            body.add(myElementFactory.createStatementFromText("return " + myArtificialOutputVariable.getName() + ";", null));
        }
        return exitStatementCopy;
    }

    private boolean isArtificialOutputUsed() {
        return myArtificialOutputVariable != null && !PsiType.VOID.equals(myReturnType) && !myIsChainedConstructor;
    }

    private boolean hasNormalExit() {
        boolean hasNormalExit = false;
        PsiElement lastElement = myElements[myElements.length - 1];
        if (!(lastElement instanceof PsiReturnStatement
            || lastElement instanceof PsiBreakStatement
            || lastElement instanceof PsiContinueStatement)) {
            hasNormalExit = true;
        }
        return hasNormalExit;
    }

    protected boolean isNeedToChangeCallContext() {
        return true;
    }

    @RequiredWriteAction
    private void declareVariableAtMethodCallLocation(String name) {
        declareVariableAtMethodCallLocation(name, myReturnType);
    }

    @RequiredWriteAction
    private String declareVariableAtMethodCallLocation(String name, PsiType type) {
        if (myControlFlowWrapper.getOutputVariables(false).length == 0) {
            PsiElement lastStatement = PsiTreeUtil.getNextSiblingOfType(
                myEnclosingBlockStatement != null ? myEnclosingBlockStatement : myElements[myElements.length - 1],
                PsiStatement.class
            );
            if (lastStatement != null) {
                name = JavaCodeStyleManager.getInstance(myProject).suggestUniqueVariableName(name, lastStatement, true);
            }
        }
        PsiDeclarationStatement statement = myElementFactory.createVariableDeclarationStatement(name, type, myMethodCall);
        statement = (PsiDeclarationStatement) addToMethodCallLocation(statement);
        PsiVariable var = (PsiVariable) statement.getDeclaredElements()[0];
        myMethodCall = (PsiMethodCallExpression) var.getInitializer();
        if (myOutputVariable != null) {
            var.getModifierList().replace(myOutputVariable.getModifierList());
        }
        return name;
    }

    private void adjustFinalParameters(final PsiMethod method) throws IncorrectOperationException {
        final IncorrectOperationException[] exc = new IncorrectOperationException[1];
        exc[0] = null;
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length > 0) {
            if (CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS) {
                method.accept(new JavaRecursiveElementVisitor() {
                    @Override
                    @RequiredReadAction
                    public void visitReferenceExpression(PsiReferenceExpression expression) {
                        PsiElement resolved = expression.resolve();
                        if (resolved != null) {
                            int index = ArrayUtil.find(parameters, resolved);
                            if (index >= 0) {
                                PsiParameter param = parameters[index];
                                if (param.hasModifierProperty(PsiModifier.FINAL) && PsiUtil.isAccessedForWriting(expression)) {
                                    try {
                                        PsiUtil.setModifierProperty(param, PsiModifier.FINAL, false);
                                    }
                                    catch (IncorrectOperationException e) {
                                        exc[0] = e;
                                    }
                                }
                            }
                        }
                        super.visitReferenceExpression(expression);
                    }
                });
            }
            else {
                method.accept(new JavaRecursiveElementVisitor() {
                    @Override
                    @RequiredReadAction
                    public void visitReferenceExpression(PsiReferenceExpression expression) {
                        PsiElement resolved = expression.resolve();
                        int index = ArrayUtil.find(parameters, resolved);
                        if (index >= 0) {
                            PsiParameter param = parameters[index];
                            if (!param.hasModifierProperty(PsiModifier.FINAL)
                                && RefactoringUtil.isInsideAnonymousOrLocal(expression, method)) {
                                try {
                                    PsiUtil.setModifierProperty(param, PsiModifier.FINAL, true);
                                }
                                catch (IncorrectOperationException e) {
                                    exc[0] = e;
                                }
                            }
                        }
                        super.visitReferenceExpression(expression);
                    }
                });
            }
            if (exc[0] != null) {
                throw exc[0];
            }
        }
    }

    @Override
    public List<Match> getDuplicates() {
        if (myIsChainedConstructor) {
            return filterChainedConstructorDuplicates(myDuplicates);
        }
        return myDuplicates;
    }

    private static List<Match> filterChainedConstructorDuplicates(List<Match> duplicates) {
        List<Match> result = new ArrayList<>();
        for (Match duplicate : duplicates) {
            PsiElement matchStart = duplicate.getMatchStart();
            PsiMethod method = PsiTreeUtil.getParentOfType(matchStart, PsiMethod.class);
            if (method != null && method.isConstructor()) {
                PsiCodeBlock body = method.getBody();
                if (body != null) {
                    PsiStatement[] psiStatements = body.getStatements();
                    if (psiStatements.length > 0 && matchStart == psiStatements[0]) {
                        result.add(duplicate);
                    }
                }
            }
        }
        return result;
    }

    @Override
    @RequiredWriteAction
    public PsiElement processMatch(Match match) throws IncorrectOperationException {
        MatchUtil.changeSignature(match, myExtractedMethod);
        if (RefactoringUtil.isInStaticContext(match.getMatchStart(), myExtractedMethod.getContainingClass())) {
            PsiUtil.setModifierProperty(myExtractedMethod, PsiModifier.STATIC, true);
        }
        PsiMethodCallExpression methodCallExpression = generateMethodCall(match.getInstanceExpression(), false);

        List<VariableData> datas = new ArrayList<>();
        for (VariableData variableData : myVariableDatum) {
            if (variableData.passAsParameter) {
                datas.add(variableData);
            }
        }
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
        for (VariableData data : datas) {
            List<PsiElement> parameterValue = match.getParameterValues(data.variable);
            if (parameterValue != null) {
                for (PsiElement val : parameterValue) {
                    if (val instanceof PsiExpression expr) {
                        PsiType exprType = expr.getType();
                        if (exprType != null && !TypeConversionUtil.isAssignable(data.type, exprType)) {
                            PsiTypeCastExpression cast = (PsiTypeCastExpression) elementFactory.createExpressionFromText("(A)a", val);
                            cast.getCastType().replace(elementFactory.createTypeElement(data.type));
                            cast.getOperand().replace(val.copy());
                            val = cast;
                        }
                    }
                    methodCallExpression.getArgumentList().add(val);
                }
            }
            else {
                methodCallExpression.getArgumentList()
                    .add(myElementFactory.createExpressionFromText(data.variable.getName(), methodCallExpression));
            }
        }
        return match.replace(myExtractedMethod, methodCallExpression, myOutputVariable);
    }

    @RequiredWriteAction
    protected void deleteExtracted() throws IncorrectOperationException {
        if (myEnclosingBlockStatement == null) {
            myElements[0].getParent().deleteChildRange(myElements[0], myElements[myElements.length - 1]);
        }
        else {
            myEnclosingBlockStatement.delete();
        }
    }

    @RequiredWriteAction
    protected PsiElement addToMethodCallLocation(PsiStatement statement) throws IncorrectOperationException {
        if (myEnclosingBlockStatement == null) {
            PsiElement containingStatement = myElements[0] instanceof PsiComment ? myElements[0]
                : PsiTreeUtil.getParentOfType(myExpression != null ? myExpression : myElements[0], PsiStatement.class, false);
            if (containingStatement == null) {
                containingStatement =
                    PsiTreeUtil.getParentOfType(myExpression != null ? myExpression : myElements[0], PsiComment.class, false);
            }

            return containingStatement.getParent().addBefore(statement, containingStatement);
        }
        else {
            return myEnclosingBlockStatement.getParent().addBefore(statement, myEnclosingBlockStatement);
        }
    }

    @RequiredReadAction
    private void renameInputVariables() throws IncorrectOperationException {
        //when multiple input variables should have the same name, unique names are generated
        //without reverse, the second rename would rename variable without a prefix into second one though it was already renamed
        for (int i = myVariableDatum.length - 1; i >= 0; i--) {
            VariableData data = myVariableDatum[i];
            PsiVariable variable = data.variable;
            if (!data.name.equals(variable.getName())) {
                for (PsiElement element : myElements) {
                    RefactoringUtil.renameVariableReferences(variable, data.name, new LocalSearchScope(element));
                }
            }
        }
    }

    public PsiClass getTargetClass() {
        return myTargetClass;
    }

    public PsiType getReturnType() {
        return myReturnType;
    }

    @RequiredWriteAction
    private PsiMethod generateEmptyMethod() throws IncorrectOperationException {
        return generateEmptyMethod(myMethodName, null);
    }

    @RequiredWriteAction
    public PsiMethod generateEmptyMethod(String methodName, PsiElement context) throws IncorrectOperationException {
        PsiMethod newMethod;
        if (myIsChainedConstructor) {
            newMethod = myElementFactory.createConstructor();
        }
        else {
            newMethod = context != null ? myElementFactory.createMethod(methodName, myReturnType, context)
                : myElementFactory.createMethod(methodName, myReturnType);
            PsiUtil.setModifierProperty(newMethod, PsiModifier.STATIC, isStatic());
        }
        PsiUtil.setModifierProperty(newMethod, myMethodVisibility, true);
        if (getTypeParameterList() != null) {
            newMethod.getTypeParameterList().replace(getTypeParameterList());
        }
        PsiCodeBlock body = newMethod.getBody();
        LOG.assertTrue(body != null);

        boolean isFinal = CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS;
        PsiParameterList list = newMethod.getParameterList();
        for (VariableData data : myVariableDatum) {
            if (data.passAsParameter) {
                PsiParameter param = myElementFactory.createParameter(data.name, data.type);
                copyParamAnnotations(param);
                if (isFinal) {
                    PsiUtil.setModifierProperty(param, PsiModifier.FINAL, true);
                }
                list.add(param);
            }
            else {
                StringBuilder buffer = new StringBuilder();
                if (isFinal) {
                    buffer.append("final ");
                }
                buffer.append("int ");
                buffer.append(data.name);
                buffer.append("=;");
                String text = buffer.toString();

                PsiDeclarationStatement declaration = (PsiDeclarationStatement) myElementFactory.createStatementFromText(text, null);
                declaration = (PsiDeclarationStatement) myStyleManager.reformat(declaration);
                PsiTypeElement typeElement = myElementFactory.createTypeElement(data.type);
                ((PsiVariable) declaration.getDeclaredElements()[0]).getTypeElement().replace(typeElement);
                body.add(declaration);
            }
        }

        PsiReferenceList throwsList = newMethod.getThrowsList();
        for (PsiClassType exception : getThrownExceptions()) {
            throwsList.add(JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createReferenceElementByType(exception));
        }

        if (myTargetClass.isInterface() && PsiUtil.isLanguageLevel8OrHigher(myTargetClass)) {
            PsiMethod containingMethod = PsiTreeUtil.getParentOfType(myCodeFragmentMember, PsiMethod.class, false);
            if (containingMethod != null && containingMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
                PsiUtil.setModifierProperty(newMethod, PsiModifier.DEFAULT, true);
            }
        }
        return (PsiMethod) myStyleManager.reformat(newMethod);
    }

    private void copyParamAnnotations(PsiParameter param) {
        PsiVariable variable = PsiResolveHelper.getInstance(myProject).resolveReferencedVariable(param.getName(), myElements[0]);
        if (variable instanceof PsiParameter refParam) {
            PsiModifierList modifierList = refParam.getModifierList();
            if (modifierList != null) {
                for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                    if (SuppressWarnings.class.getName().equals(annotation.getQualifiedName())) {
                        continue;
                    }
                    PsiModifierList paramModifierList = param.getModifierList();
                    LOG.assertTrue(paramModifierList != null, param);
                    paramModifierList.add(annotation);
                }
            }
        }
    }

    @Nonnull
    @RequiredWriteAction
    protected PsiMethodCallExpression generateMethodCall(PsiExpression instanceQualifier, boolean generateArgs)
        throws IncorrectOperationException {
        StringBuilder buffer = new StringBuilder();

        boolean skipInstanceQualifier;
        if (myIsChainedConstructor) {
            skipInstanceQualifier = true;
            buffer.append(PsiKeyword.THIS);
        }
        else {
            skipInstanceQualifier = instanceQualifier == null || instanceQualifier instanceof PsiThisExpression;
            if (skipInstanceQualifier) {
                if (isNeedToChangeCallContext() && myNeedChangeContext) {
                    boolean needsThisQualifier = false;
                    PsiElement parent = myCodeFragmentMember;
                    while (!myTargetClass.equals(parent)) {
                        if (parent instanceof PsiMethod method && method.getName().equals(myMethodName)) {
                            needsThisQualifier = true;
                            break;
                        }
                        parent = parent.getParent();
                    }
                    if (needsThisQualifier) {
                        buffer.append(myTargetClass.getName()).append(".this.");
                    }
                }
            }
            else {
                buffer.append("qqq.");
            }

            buffer.append(myMethodName);
        }
        buffer.append("(");
        if (generateArgs) {
            int count = 0;
            for (VariableData data : myVariableDatum) {
                if (data.passAsParameter) {
                    if (count > 0) {
                        buffer.append(",");
                    }
                    myInputVariables.appendCallArguments(data, buffer);
                    count++;
                }
            }
        }
        buffer.append(")");
        String text = buffer.toString();

        PsiMethodCallExpression expr = (PsiMethodCallExpression) myElementFactory.createExpressionFromText(text, null);
        expr = (PsiMethodCallExpression) myStyleManager.reformat(expr);
        if (!skipInstanceQualifier) {
            PsiExpression qualifierExpression = expr.getMethodExpression().getQualifierExpression();
            LOG.assertTrue(qualifierExpression != null);
            qualifierExpression.replace(instanceQualifier);
        }
        return (PsiMethodCallExpression) JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(expr);
    }

    @RequiredUIAccess
    private boolean chooseTargetClass(PsiElement codeFragment, Consumer<ExtractMethodProcessor> extractPass) throws PrepareFailedException {
        List<PsiVariable> inputVariables = myControlFlowWrapper.getInputVariables(codeFragment, myElements, myOutputVariables);

        myNeedChangeContext = false;
        myTargetClass = myCodeFragmentMember instanceof PsiMember member
            ? member.getContainingClass()
            : PsiTreeUtil.getParentOfType(myCodeFragmentMember, PsiClass.class);
        if (!shouldAcceptCurrentTarget(extractPass, myTargetClass)) {
            Map<PsiClass, List<PsiVariable>> classes = new LinkedHashMap<>();
            @RequiredUIAccess
            PsiElementProcessor<PsiClass> processor = selectedClass -> {
                AnonymousTargetClassPreselectionUtil.rememberSelection(selectedClass, myTargetClass);
                List<PsiVariable> array = classes.get(selectedClass);
                myNeedChangeContext = myTargetClass != selectedClass;
                myTargetClass = selectedClass;
                if (array != null) {
                    for (PsiVariable variable : array) {
                        if (!inputVariables.contains(variable)) {
                            inputVariables.addAll(array);
                        }
                    }
                }
                try {
                    return applyChosenClassAndExtract(inputVariables, extractPass);
                }
                catch (PrepareFailedException e) {
                    if (myShowErrorDialogs) {
                        CommonRefactoringUtil.showErrorHint(
                            myProject,
                            myEditor,
                            LocalizeValue.ofNullable(e.getMessage()),
                            ExtractMethodHandler.REFACTORING_NAME,
                            HelpID.EXTRACT_METHOD
                        );
                        ExtractMethodHandler.highlightPrepareError(e, e.getFile(), myEditor, myProject);
                    }
                    return false;
                }
            };

            classes.put(myTargetClass, null);
            PsiElement target = myTargetClass.getParent();
            PsiElement targetMember = myTargetClass;
            while (true) {
                if (target instanceof PsiFile) {
                    break;
                }
                if (target instanceof PsiClass psiClass) {
                    boolean success = true;
                    List<PsiVariable> array = new ArrayList<>();
                    for (PsiElement el : myElements) {
                        if (!ControlFlowUtil.collectOuterLocals(array, el, myCodeFragmentMember, targetMember)) {
                            success = false;
                            break;
                        }
                    }
                    if (success) {
                        classes.put(psiClass, array);
                        if (shouldAcceptCurrentTarget(extractPass, target)) {
                            return processor.execute(psiClass);
                        }
                    }
                }
                targetMember = target;
                target = target.getParent();
            }

            if (classes.size() > 1) {
                PsiClass[] psiClasses = classes.keySet().toArray(new PsiClass[classes.size()]);
                PsiClass preselection = AnonymousTargetClassPreselectionUtil.getPreselection(classes.keySet(), psiClasses[0]);
                JBPopup popup = PopupNavigationUtil.getPsiElementPopup(
                    psiClasses,
                    new PsiClassListCellRenderer(),
                    "Choose Destination Class",
                    processor,
                    preselection
                );
                EditorPopupHelper.getInstance().showPopupInBestPositionFor(myEditor, popup);
                return true;
            }
        }

        return applyChosenClassAndExtract(inputVariables, extractPass);
    }

    @RequiredReadAction
    private void declareNecessaryVariablesInsideBody(PsiCodeBlock body) throws IncorrectOperationException {
        List<PsiVariable> usedVariables =
            myControlFlowWrapper.getUsedVariablesInBody(ControlFlowUtil.findCodeFragment(myElements[0]), myOutputVariables);
        for (PsiVariable variable : usedVariables) {
            boolean toDeclare = !isDeclaredInside(variable) && myInputVariables.toDeclareInsideBody(variable);
            if (toDeclare) {
                String name = variable.getName();
                PsiDeclarationStatement statement = myElementFactory.createVariableDeclarationStatement(name, variable.getType(), null);
                body.add(statement);
            }
        }

        if (myArtificialOutputVariable instanceof PsiField && !myIsChainedConstructor) {
            body.add(myElementFactory.createVariableDeclarationStatement(
                myArtificialOutputVariable.getName(),
                myArtificialOutputVariable.getType(),
                null
            ));
        }
    }

    @RequiredWriteAction
    protected void declareNecessaryVariablesAfterCall(PsiVariable outputVariable) throws IncorrectOperationException {
        if (myHasExpressionOutput) {
            return;
        }
        List<PsiVariable> usedVariables = myControlFlowWrapper.getUsedVariables();
        Collection<ControlFlowUtil.VariableInfo> reassigned = myControlFlowWrapper.getInitializedTwice();
        for (PsiVariable variable : usedVariables) {
            boolean toDeclare = isDeclaredInside(variable) && !variable.equals(outputVariable);
            if (toDeclare) {
                String name = variable.getName();
                PsiDeclarationStatement statement = myElementFactory.createVariableDeclarationStatement(name, variable.getType(), null);
                if (reassigned.contains(new ControlFlowUtil.VariableInfo(variable, null))) {
                    PsiElement[] psiElements = statement.getDeclaredElements();
                    assert psiElements.length > 0;
                    PsiVariable var = (PsiVariable) psiElements[0];
                    PsiUtil.setModifierProperty(var, PsiModifier.FINAL, false);
                }
                addToMethodCallLocation(statement);
            }
        }
    }

    public PsiMethodCallExpression getMethodCall() {
        return myMethodCall;
    }

    public void setMethodCall(PsiMethodCallExpression methodCall) {
        myMethodCall = methodCall;
    }

    @RequiredReadAction
    public boolean isDeclaredInside(PsiVariable variable) {
        if (variable instanceof ImplicitVariable) {
            return false;
        }
        int startOffset;
        int endOffset;
        if (myExpression != null) {
            TextRange range = myExpression.getTextRange();
            startOffset = range.getStartOffset();
            endOffset = range.getEndOffset();
        }
        else {
            startOffset = myElements[0].getTextRange().getStartOffset();
            endOffset = myElements[myElements.length - 1].getTextRange().getEndOffset();
        }
        PsiIdentifier nameIdentifier = variable.getNameIdentifier();
        if (nameIdentifier == null) {
            return false;
        }
        TextRange range = nameIdentifier.getTextRange();
        int offset = range.getStartOffset();
        return startOffset <= offset && offset <= endOffset;
    }

    @RequiredReadAction
    private String getNewVariableName(PsiVariable variable) {
        for (VariableData data : myVariableDatum) {
            if (data.variable.equals(variable)) {
                return data.name;
            }
        }
        return variable.getName();
    }

    private static boolean shouldAcceptCurrentTarget(Consumer<ExtractMethodProcessor> extractPass, PsiElement target) {
        return extractPass == null && !(target instanceof PsiAnonymousClass);
    }

    @RequiredUIAccess
    private boolean applyChosenClassAndExtract(List<PsiVariable> inputVariables, @Nullable Consumer<ExtractMethodProcessor> extractPass)
        throws PrepareFailedException {
        myStatic = shouldBeStatic();
        final Set<PsiField> fields = new LinkedHashSet<>();
        if (!PsiUtil.isLocalOrAnonymousClass(myTargetClass)
            && (myTargetClass.getContainingClass() == null || myTargetClass.isStatic())) {
            boolean canBeStatic = true;
            if (myTargetClass.isInterface()) {
                PsiMethod containingMethod = PsiTreeUtil.getParentOfType(myCodeFragmentMember, PsiMethod.class, false);
                canBeStatic = containingMethod == null || containingMethod.isStatic();
            }
            if (canBeStatic) {
                ElementNeedsThis needsThis = new ElementNeedsThis(myTargetClass) {
                    @Override
                    protected void visitClassMemberReferenceElement(
                        PsiMember classMember,
                        PsiJavaCodeReferenceElement classMemberReference
                    ) {
                        if (classMember instanceof PsiField && !classMember.isStatic()) {
                            PsiExpression expression = PsiTreeUtil.getParentOfType(classMemberReference, PsiExpression.class, false);
                            if (expression == null || !PsiUtil.isAccessedForWriting(expression)) {
                                fields.add((PsiField) classMember);
                                return;
                            }
                        }
                        super.visitClassMemberReferenceElement(classMember, classMemberReference);
                    }
                };
                for (int i = 0; i < myElements.length && !needsThis.usesMembers(); i++) {
                    PsiElement element = myElements[i];
                    element.accept(needsThis);
                }
                myCanBeStatic = !needsThis.usesMembers();
            }
            else {
                myCanBeStatic = false;
            }
        }
        else {
            myCanBeStatic = false;
        }

        myInputVariables = new InputVariables(inputVariables, myProject, new LocalSearchScope(myElements), isFoldingApplicable());
        myInputVariables.setUsedInstanceFields(fields);

        if (!checkExitPoints()) {
            return false;
        }

        checkCanBeChainedConstructor();

        if (extractPass != null) {
            extractPass.accept(this);
        }
        return true;
    }

    protected boolean isFoldingApplicable() {
        return true;
    }

    private void chooseAnchor() {
        myAnchor = myCodeFragmentMember;
        while (!myAnchor.getParent().equals(myTargetClass)) {
            myAnchor = myAnchor.getParent();
        }
    }

    @RequiredUIAccess
    private void showMultipleExitPointsMessage() {
        if (myShowErrorDialogs) {
            HighlightManager highlightManager = HighlightManager.getInstance(myProject);
            PsiStatement[] exitStatementsArray = myExitStatements.toArray(new PsiStatement[myExitStatements.size()]);
            highlightManager.addOccurrenceHighlights(myEditor, exitStatementsArray, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
            LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
                RefactoringLocalize.thereAreMultipleExitPointsInTheSelectedCodeFragment()
            );
            CommonRefactoringUtil.showErrorHint(myProject, myEditor, message, myRefactoringName, myHelpId);
        }
    }

    @RequiredUIAccess
    private void showMultipleOutputMessage(PsiType expressionType) {
        if (myShowErrorDialogs) {
            StringBuilder buffer = new StringBuilder();
            buffer.append(RefactoringLocalize.cannotPerformRefactoringWithReason(
                RefactoringLocalize.thereAreMultipleOutputValuesForTheSelectedCodeFragment()
            ));
            buffer.append("\n");
            if (myHasExpressionOutput) {
                buffer.append("    ").append(RefactoringLocalize.expressionResult()).append(": ");
                buffer.append(PsiFormatUtil.formatType(expressionType, 0, PsiSubstitutor.EMPTY));
                buffer.append(",\n");
            }
            if (myGenerateConditionalExit) {
                buffer.append("    ").append(RefactoringLocalize.booleanMethodResult());
                buffer.append(",\n");
            }
            for (int i = 0; i < myOutputVariables.length; i++) {
                PsiVariable var = myOutputVariables[i];
                buffer.append("    ");
                buffer.append(var.getName());
                buffer.append(" : ");
                buffer.append(PsiFormatUtil.formatType(var.getType(), 0, PsiSubstitutor.EMPTY));
                if (i < myOutputVariables.length - 1) {
                    buffer.append(",\n");
                }
                else {
                    buffer.append(".");
                }
            }
            buffer.append("\nWould you like to Extract Method Object?");

            String message = buffer.toString();

            if (myProject.getApplication().isUnitTestMode()) {
                throw new RuntimeException(message);
            }
            RefactoringMessageDialog dialog = new RefactoringMessageDialog(
                myRefactoringName,
                LocalizeValue.localizeTODO(message),
                myHelpId,
                UIUtil.getErrorIcon(),
                true,
                myProject
            );
            if (dialog.showAndGet()) {
                new ExtractMethodObjectHandler().invoke(
                    myProject,
                    myEditor,
                    myTargetClass.getContainingFile(),
                    DataManager.getInstance().getDataContext()
                );
            }
        }
    }

    public PsiMethod getExtractedMethod() {
        return myExtractedMethod;
    }

    @Override
    public Boolean hasDuplicates() {
        List<Match> duplicates = getDuplicates();
        if (duplicates != null && !duplicates.isEmpty()) {
            return true;
        }

        if (myExtractedMethod != null) {
            ExtractMethodSignatureSuggester suggester =
                new ExtractMethodSignatureSuggester(myProject, myExtractedMethod, myMethodCall, myVariableDatum);
            duplicates = suggester.getDuplicates(myExtractedMethod, myMethodCall, myInputVariables.getFolding());
            if (duplicates != null && !duplicates.isEmpty()) {
                myDuplicates = duplicates;
                myExtractedMethod = suggester.getExtractedMethod();
                myMethodCall = suggester.getMethodCall();
                myVariableDatum = suggester.getVariableData();
                return null;
            }
        }
        return false;
    }

    @RequiredReadAction
    public boolean hasDuplicates(Set<VirtualFile> files) {
        DuplicatesFinder finder = initDuplicates();

        Boolean hasDuplicates = hasDuplicates();
        if (hasDuplicates == null || hasDuplicates) {
            return true;
        }
        if (finder != null) {
            PsiManager psiManager = PsiManager.getInstance(myProject);
            for (VirtualFile file : files) {
                if (!finder.findDuplicates(psiManager.findFile(file)).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    @Nullable
    public String getConfirmDuplicatePrompt(Match match) {
        boolean needToBeStatic = RefactoringUtil.isInStaticContext(match.getMatchStart(), myExtractedMethod.getContainingClass());
        String changedSignature = MatchUtil.getChangedSignature(
            match,
            myExtractedMethod,
            needToBeStatic,
            VisibilityUtil.getVisibilityStringToDisplay(myExtractedMethod)
        );
        if (changedSignature != null) {
            return RefactoringLocalize.replaceThisCodeFragmentAndChangeSignature(changedSignature).get();
        }
        if (needToBeStatic && !myExtractedMethod.isStatic()) {
            return RefactoringLocalize.replaceThisCodeFragmentAndMakeMethodStatic().get();
        }
        return null;
    }

    @Override
    public LocalizeValue getReplaceDuplicatesTitle(int idx, int size) {
        return RefactoringLocalize.processDuplicatesTitle(idx, size);
    }

    public InputVariables getInputVariables() {
        return myInputVariables;
    }

    public PsiTypeParameterList getTypeParameterList() {
        return myTypeParameterList;
    }

    public PsiClassType[] getThrownExceptions() {
        return myThrownExceptions;
    }

    public boolean isStatic() {
        return myStatic;
    }

    public boolean isCanBeStatic() {
        return myCanBeStatic;
    }

    public PsiElement[] getElements() {
        return myElements;
    }

    public PsiVariable[] getOutputVariables() {
        return myOutputVariables;
    }
}
