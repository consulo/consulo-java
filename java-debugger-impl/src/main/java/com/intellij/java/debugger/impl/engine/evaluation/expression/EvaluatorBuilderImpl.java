/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.engine.evaluation.expression;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.java.debugger.engine.evaluation.TextWithImports;
import com.intellij.java.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.java.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.java.debugger.engine.evaluation.expression.Modifier;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.engine.ContextUtil;
import com.intellij.java.debugger.impl.engine.JVMName;
import com.intellij.java.debugger.impl.engine.JVMNameUtil;
import com.intellij.java.debugger.impl.engine.evaluation.CodeFragmentFactoryContextWrapper;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluateRuntimeException;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.language.impl.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.internal.com.sun.jdi.Value;
import consulo.java.language.localize.JavaCompilationErrorLocalize;
import consulo.language.ast.IElementType;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.psi.PsiCodeFragment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Jeka
 */
public class EvaluatorBuilderImpl implements EvaluatorBuilder {
    private static final EvaluatorBuilderImpl ourInstance = new EvaluatorBuilderImpl();

    private EvaluatorBuilderImpl() {
    }

    public static EvaluatorBuilder getInstance() {
        return ourInstance;
    }

    @RequiredReadAction
    public static ExpressionEvaluator build(
        TextWithImports text,
        @Nullable PsiElement contextElement,
        @Nullable SourcePosition position,
        @Nonnull Project project
    ) throws
        EvaluateException {
        CodeFragmentFactory factory = DebuggerUtilsEx.findAppropriateCodeFragmentFactory(text, contextElement);
        PsiCodeFragment codeFragment = factory.createCodeFragment(text, contextElement, project);
        if (codeFragment == null) {
            throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerLocalize.evaluationErrorInvalidExpression(text.getText()));
        }
        DebuggerUtils.checkSyntax(codeFragment);

        return factory.getEvaluatorBuilder().build(codeFragment, position);
    }

    @Override
    @RequiredReadAction
    public ExpressionEvaluator build(PsiElement codeFragment, SourcePosition position) throws EvaluateException {
        return new Builder(position).buildElement(codeFragment);
    }

    private static class Builder extends JavaElementVisitor {
        private static final Logger LOG = LoggerFactory.getLogger(EvaluatorBuilderImpl.class);
        private Evaluator myResult = null;
        private PsiClass myContextPsiClass;
        private CodeFragmentEvaluator myCurrentFragmentEvaluator;
        private final Set<JavaCodeFragment> myVisitedFragments = new HashSet<>();
        @Nullable
        private final SourcePosition myPosition;

        private Builder(@Nullable SourcePosition position) {
            myPosition = position;
        }

        @Override
        @RequiredReadAction
        public void visitCodeFragment(@Nonnull JavaCodeFragment codeFragment) {
            myVisitedFragments.add(codeFragment);
            ArrayList<Evaluator> evaluators = new ArrayList<>();

            CodeFragmentEvaluator oldFragmentEvaluator = setNewCodeFragmentEvaluator();

            try {
                for (PsiElement child = codeFragment.getFirstChild(); child != null; child = child.getNextSibling()) {
                    child.accept(this);
                    if (myResult != null) {
                        evaluators.add(myResult);
                    }
                    myResult = null;
                }

                myCurrentFragmentEvaluator.setStatements(evaluators.toArray(new Evaluator[evaluators.size()]));
                myResult = myCurrentFragmentEvaluator;
            }
            finally {
                myCurrentFragmentEvaluator = oldFragmentEvaluator;
            }
        }

        @Override
        @RequiredReadAction
        public void visitErrorElement(PsiErrorElement element) {
            throwExpressionInvalid(element);
        }

        @Override
        @RequiredReadAction
        public void visitAssignmentExpression(@Nonnull PsiAssignmentExpression expression) {
            PsiExpression rExpression = expression.getRExpression();
            if (rExpression == null) {
                throwExpressionInvalid(expression);
            }

            rExpression.accept(this);
            Evaluator rEvaluator = myResult;

            PsiExpression lExpression = expression.getLExpression();
            PsiType lType = lExpression.getType();
            if (lType == null) {
                throwEvaluateException(JavaDebuggerLocalize.evaluationErrorUnknownExpressionType(lExpression.getText()));
            }

            IElementType assignmentType = expression.getOperationTokenType();
            PsiType rType = rExpression.getType();
            if (!TypeConversionUtil.areTypesAssignmentCompatible(lType, rExpression) && rType != null) {
                throwEvaluateException(JavaDebuggerLocalize.evaluationErrorIncompatibleTypes(expression.getOperationSign().getText()));
            }
            lExpression.accept(this);
            Evaluator lEvaluator = myResult;

            rEvaluator = handleAssignmentBoxingAndPrimitiveTypeConversions(lType, rType, rEvaluator);

            if (assignmentType != JavaTokenType.EQ) {
                IElementType opType = TypeConversionUtil.convertEQtoOperation(assignmentType);
                PsiType typeForBinOp = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, opType, true);
                if (typeForBinOp == null || rType == null) {
                    throwEvaluateException(JavaDebuggerLocalize.evaluationErrorUnknownExpressionType(expression.getText()));
                }
                rEvaluator = createBinaryEvaluator(lEvaluator, lType, rEvaluator, rType, opType, typeForBinOp);
            }
            myResult = new AssignmentEvaluator(lEvaluator, rEvaluator);
        }

        // returns rEvaluator possibly wrapped with boxing/unboxing and casting evaluators
        private static Evaluator handleAssignmentBoxingAndPrimitiveTypeConversions(PsiType lType, PsiType rType, Evaluator rEvaluator) {
            PsiType unboxedLType = PsiPrimitiveType.getUnboxedType(lType);

            if (unboxedLType != null) {
                if (rType instanceof PsiPrimitiveType && !PsiType.NULL.equals(rType)) {
                    if (!rType.equals(unboxedLType)) {
                        rEvaluator = new TypeCastEvaluator(rEvaluator, unboxedLType.getCanonicalText(), true);
                    }
                    rEvaluator = new BoxingEvaluator(rEvaluator);
                }
            }
            else {
                // either primitive type or not unboxable type
                if (lType instanceof PsiPrimitiveType) {
                    if (rType instanceof PsiClassType) {
                        rEvaluator = new UnBoxingEvaluator(rEvaluator);
                    }
                    PsiPrimitiveType unboxedRType = PsiPrimitiveType.getUnboxedType(rType);
                    PsiType _rType = unboxedRType != null ? unboxedRType : rType;
                    if (_rType instanceof PsiPrimitiveType && !PsiType.NULL.equals(_rType)) {
                        if (!lType.equals(_rType)) {
                            rEvaluator = new TypeCastEvaluator(rEvaluator, lType.getCanonicalText(), true);
                        }
                    }
                }
            }
            return rEvaluator;
        }

        @Override
        public void visitTryStatement(@Nonnull PsiTryStatement statement) {
            if (statement.getResourceList() != null) {
                throw new EvaluateRuntimeException(new UnsupportedExpressionException(
                    LocalizeValue.localizeTODO("Try with resources is not yet supported")
                ));
            }
            Evaluator bodyEvaluator = accept(statement.getTryBlock());
            if (bodyEvaluator != null) {
                PsiCatchSection[] catchSections = statement.getCatchSections();
                List<CatchEvaluator> evaluators = new ArrayList<>();
                for (PsiCatchSection catchSection : catchSections) {
                    PsiParameter parameter = catchSection.getParameter();
                    PsiCodeBlock catchBlock = catchSection.getCatchBlock();
                    if (parameter != null && catchBlock != null) {
                        CodeFragmentEvaluator oldFragmentEvaluator = setNewCodeFragmentEvaluator();
                        try {
                            myCurrentFragmentEvaluator.setInitialValue(parameter.getName(), null);
                            myCurrentFragmentEvaluator.setStatements(visitStatements(catchBlock.getStatements()));
                            PsiType type = parameter.getType();
                            List<PsiType> types = type instanceof PsiDisjunctionType disjunctionType
                                ? disjunctionType.getDisjunctions()
                                : Collections.singletonList(type);
                            for (PsiType psiType : types) {
                                evaluators.add(new CatchEvaluator(
                                    psiType.getCanonicalText(),
                                    parameter.getName(),
                                    myCurrentFragmentEvaluator
                                ));
                            }
                        }
                        finally {
                            myCurrentFragmentEvaluator = oldFragmentEvaluator;
                        }
                    }
                }
                myResult = new TryEvaluator(bodyEvaluator, evaluators, accept(statement.getFinallyBlock()));
            }
        }

        @Override
        public void visitThrowStatement(@Nonnull PsiThrowStatement statement) {
            Evaluator accept = accept(statement.getException());
            if (accept != null) {
                myResult = new ThrowEvaluator(accept);
            }
        }

        @Override
        public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
            myResult = new ReturnEvaluator(accept(statement.getReturnValue()));
        }


        @Override
        @RequiredReadAction
        public void visitStatement(@Nonnull PsiStatement statement) {
            throwEvaluateException(JavaDebuggerLocalize.evaluationErrorStatementNotSupported(statement.getText()));
        }

        private CodeFragmentEvaluator setNewCodeFragmentEvaluator() {
            CodeFragmentEvaluator old = myCurrentFragmentEvaluator;
            myCurrentFragmentEvaluator = new CodeFragmentEvaluator(myCurrentFragmentEvaluator);
            return old;
        }

        private Evaluator[] visitStatements(PsiStatement[] statements) {
            List<Evaluator> evaluators = new ArrayList<>();
            for (PsiStatement psiStatement : statements) {
                psiStatement.accept(this);
                if (myResult != null) { // for example declaration w/o initializer produces empty evaluator now
                    evaluators.add(DisableGC.create(myResult));
                }
                myResult = null;
            }
            return evaluators.toArray(new Evaluator[0]);
        }

        @Override
        public void visitCodeBlock(@Nonnull PsiCodeBlock block) {
            CodeFragmentEvaluator oldFragmentEvaluator = setNewCodeFragmentEvaluator();
            try {
                myResult = new BlockStatementEvaluator(visitStatements(block.getStatements()));
            }
            finally {
                myCurrentFragmentEvaluator = oldFragmentEvaluator;
            }
        }

        @Override
        public void visitBlockStatement(@Nonnull PsiBlockStatement statement) {
            visitCodeBlock(statement.getCodeBlock());
        }

        @Override
        public void visitLabeledStatement(@Nonnull PsiLabeledStatement labeledStatement) {
            PsiStatement statement = labeledStatement.getStatement();
            if (statement != null) {
                statement.accept(this);
            }
        }

        private static String getLabel(PsiElement element) {
            return element.getParent() instanceof PsiLabeledStatement labeledStmt ? labeledStmt.getName() : null;
        }

        @Override
        public void visitDoWhileStatement(@Nonnull PsiDoWhileStatement statement) {
            Evaluator bodyEvaluator = accept(statement.getBody());
            Evaluator conditionEvaluator = accept(statement.getCondition());
            if (conditionEvaluator != null) {
                myResult = new DoWhileStatementEvaluator(new UnBoxingEvaluator(conditionEvaluator), bodyEvaluator, getLabel(statement));
            }
        }

        @Override
        public void visitWhileStatement(@Nonnull PsiWhileStatement statement) {
            Evaluator bodyEvaluator = accept(statement.getBody());
            Evaluator conditionEvaluator = accept(statement.getCondition());
            if (conditionEvaluator != null) {
                myResult = new WhileStatementEvaluator(new UnBoxingEvaluator(conditionEvaluator), bodyEvaluator, getLabel(statement));
            }
        }

        @Override
        public void visitForStatement(@Nonnull PsiForStatement statement) {
            CodeFragmentEvaluator oldFragmentEvaluator = setNewCodeFragmentEvaluator();
            try {
                Evaluator initializerEvaluator = accept(statement.getInitialization());
                Evaluator conditionEvaluator = accept(statement.getCondition());
                if (conditionEvaluator != null) {
                    conditionEvaluator = new UnBoxingEvaluator(conditionEvaluator);
                }
                Evaluator updateEvaluator = accept(statement.getUpdate());
                Evaluator bodyEvaluator = accept(statement.getBody());
                if (bodyEvaluator != null) {
                    myResult = new ForStatementEvaluator(
                        initializerEvaluator,
                        conditionEvaluator,
                        updateEvaluator,
                        bodyEvaluator,
                        getLabel(statement)
                    );
                }
            }
            finally {
                myCurrentFragmentEvaluator = oldFragmentEvaluator;
            }
        }

        @Override
        public void visitForeachStatement(@Nonnull PsiForeachStatement statement) {
            CodeFragmentEvaluator oldFragmentEvaluator = setNewCodeFragmentEvaluator();
            try {
                String iterationParameterName = statement.getIterationParameter().getName();
                myCurrentFragmentEvaluator.setInitialValue(iterationParameterName, null);
                SyntheticVariableEvaluator iterationParameterEvaluator =
                    new SyntheticVariableEvaluator(myCurrentFragmentEvaluator, iterationParameterName);

                Evaluator iteratedValueEvaluator = accept(statement.getIteratedValue());
                Evaluator bodyEvaluator = accept(statement.getBody());
                if (bodyEvaluator != null) {
                    myResult = new ForeachStatementEvaluator(
                        iterationParameterEvaluator,
                        iteratedValueEvaluator,
                        bodyEvaluator,
                        getLabel(statement)
                    );
                }
            }
            finally {
                myCurrentFragmentEvaluator = oldFragmentEvaluator;
            }
        }

        @Nullable
        private Evaluator accept(@Nullable PsiElement element) {
            if (element == null || element instanceof PsiEmptyStatement) {
                return null;
            }
            element.accept(this);
            return myResult;
        }

        @Override
        public void visitIfStatement(@Nonnull PsiIfStatement statement) {
            PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            thenBranch.accept(this);
            Evaluator thenEvaluator = myResult;

            PsiStatement elseBranch = statement.getElseBranch();
            Evaluator elseEvaluator = null;
            if (elseBranch != null) {
                elseBranch.accept(this);
                elseEvaluator = myResult;
            }

            PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            condition.accept(this);

            myResult = new IfStatementEvaluator(new UnBoxingEvaluator(myResult), thenEvaluator, elseEvaluator);
        }

        @Override
        @RequiredReadAction
        public void visitBreakStatement(@Nonnull PsiBreakStatement statement) {
            PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
            myResult = BreakContinueStatementEvaluator.createBreakEvaluator(labelIdentifier != null ? labelIdentifier.getText() : null);
        }

        @Override
        @RequiredReadAction
        public void visitContinueStatement(@Nonnull PsiContinueStatement statement) {
            PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
            myResult = BreakContinueStatementEvaluator.createContinueEvaluator(labelIdentifier != null ? labelIdentifier.getText() : null);
        }

        @Override
        public void visitExpressionStatement(@Nonnull PsiExpressionStatement statement) {
            statement.getExpression().accept(this);
        }

        @Override
        public void visitExpression(@Nonnull PsiExpression expression) {
            LOG.debug("visitExpression {}", expression);
        }

        @Override
        @RequiredReadAction
        public void visitPolyadicExpression(@Nonnull PsiPolyadicExpression wideExpression) {
            LOG.debug("visitPolyadicExpression {}", wideExpression);
            PsiExpression[] operands = wideExpression.getOperands();
            operands[0].accept(this);
            Evaluator result = myResult;
            PsiType lType = operands[0].getType();
            for (int i = 1; i < operands.length; i++) {
                PsiExpression expression = operands[i];
                if (expression == null) {
                    throwExpressionInvalid(wideExpression);
                }
                expression.accept(this);
                Evaluator rResult = myResult;
                IElementType opType = wideExpression.getOperationTokenType();
                PsiType rType = expression.getType();
                if (rType == null) {
                    throwEvaluateException(JavaDebuggerLocalize.evaluationErrorUnknownExpressionType(expression.getText()));
                }
                PsiType typeForBinOp = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, opType, true);
                if (typeForBinOp == null) {
                    throwEvaluateException(JavaDebuggerLocalize.evaluationErrorUnknownExpressionType(wideExpression.getText()));
                }
                myResult = createBinaryEvaluator(result, lType, rResult, rType, opType, typeForBinOp);
                lType = typeForBinOp;
                result = myResult;
            }
        }

        // constructs binary evaluator handling unboxing and numeric promotion issues
        private static Evaluator createBinaryEvaluator(
            Evaluator lResult,
            PsiType lType,
            Evaluator rResult,
            @Nonnull PsiType rType,
            @Nonnull IElementType operation,
            @Nonnull PsiType expressionExpectedType
        ) {
            // handle unboxing if necessary
            if (isUnboxingInBinaryExpressionApplicable(lType, rType, operation)) {
                if (rType instanceof PsiClassType && UnBoxingEvaluator.isTypeUnboxable(rType.getCanonicalText())) {
                    rResult = new UnBoxingEvaluator(rResult);
                }
                if (lType instanceof PsiClassType && UnBoxingEvaluator.isTypeUnboxable(lType.getCanonicalText())) {
                    lResult = new UnBoxingEvaluator(lResult);
                }
            }
            if (isBinaryNumericPromotionApplicable(lType, rType, operation)) {
                PsiType _lType = lType;
                PsiPrimitiveType unboxedLType = PsiPrimitiveType.getUnboxedType(lType);
                if (unboxedLType != null) {
                    _lType = unboxedLType;
                }

                PsiType _rType = rType;
                PsiPrimitiveType unboxedRType = PsiPrimitiveType.getUnboxedType(rType);
                if (unboxedRType != null) {
                    _rType = unboxedRType;
                }

                // handle numeric promotion
                if (PsiType.DOUBLE.equals(_lType)) {
                    if (TypeConversionUtil.areTypesConvertible(_rType, PsiType.DOUBLE)) {
                        rResult = new TypeCastEvaluator(rResult, PsiType.DOUBLE.getCanonicalText(), true);
                    }
                }
                else if (PsiType.DOUBLE.equals(_rType)) {
                    if (TypeConversionUtil.areTypesConvertible(_lType, PsiType.DOUBLE)) {
                        lResult = new TypeCastEvaluator(lResult, PsiType.DOUBLE.getCanonicalText(), true);
                    }
                }
                else if (PsiType.FLOAT.equals(_lType)) {
                    if (TypeConversionUtil.areTypesConvertible(_rType, PsiType.FLOAT)) {
                        rResult = new TypeCastEvaluator(rResult, PsiType.FLOAT.getCanonicalText(), true);
                    }
                }
                else if (PsiType.FLOAT.equals(_rType)) {
                    if (TypeConversionUtil.areTypesConvertible(_lType, PsiType.FLOAT)) {
                        lResult = new TypeCastEvaluator(lResult, PsiType.FLOAT.getCanonicalText(), true);
                    }
                }
                else if (PsiType.LONG.equals(_lType)) {
                    if (TypeConversionUtil.areTypesConvertible(_rType, PsiType.LONG)) {
                        rResult = new TypeCastEvaluator(rResult, PsiType.LONG.getCanonicalText(), true);
                    }
                }
                else if (PsiType.LONG.equals(_rType)) {
                    if (TypeConversionUtil.areTypesConvertible(_lType, PsiType.LONG)) {
                        lResult = new TypeCastEvaluator(lResult, PsiType.LONG.getCanonicalText(), true);
                    }
                }
                else {
                    if (!PsiType.INT.equals(_lType) && TypeConversionUtil.areTypesConvertible(_lType, PsiType.INT)) {
                        lResult = new TypeCastEvaluator(lResult, PsiType.INT.getCanonicalText(), true);
                    }
                    if (!PsiType.INT.equals(_rType) && TypeConversionUtil.areTypesConvertible(_rType, PsiType.INT)) {
                        rResult = new TypeCastEvaluator(rResult, PsiType.INT.getCanonicalText(), true);
                    }
                }
            }
            // unary numeric promotion if applicable
            else if (operation == JavaTokenType.GTGT || operation == JavaTokenType.LTLT || operation == JavaTokenType.GTGTGT) {
                lResult = handleUnaryNumericPromotion(lType, lResult);
                rResult = handleUnaryNumericPromotion(rType, rResult);
            }

            return DisableGC.create(new BinaryExpressionEvaluator(lResult, rResult, operation, expressionExpectedType.getCanonicalText()));
        }

        private static boolean isBinaryNumericPromotionApplicable(PsiType lType, PsiType rType, IElementType opType) {
            if (lType == null || rType == null) {
                return false;
            }
            if (!TypeConversionUtil.isNumericType(lType) || !TypeConversionUtil.isNumericType(rType)) {
                return false;
            }
            if (opType == JavaTokenType.EQEQ || opType == JavaTokenType.NE) {
                if (PsiType.NULL.equals(lType) || PsiType.NULL.equals(rType)) {
                    return false;
                }
                if (lType instanceof PsiClassType && rType instanceof PsiClassType) {
                    return false;
                }
                if (lType instanceof PsiClassType) {
                    return PsiPrimitiveType.getUnboxedType(lType) != null; // should be unboxable
                }
                if (rType instanceof PsiClassType) {
                    return PsiPrimitiveType.getUnboxedType(rType) != null; // should be unboxable
                }
                return true;
            }

            return opType == JavaTokenType.ASTERISK
                || opType == JavaTokenType.DIV
                || opType == JavaTokenType.PERC
                || opType == JavaTokenType.PLUS
                || opType == JavaTokenType.MINUS
                || opType == JavaTokenType.LT
                || opType == JavaTokenType.LE
                || opType == JavaTokenType.GT
                || opType == JavaTokenType.GE
                || opType == JavaTokenType.AND
                || opType == JavaTokenType.XOR
                || opType == JavaTokenType.OR;
        }

        private static boolean isUnboxingInBinaryExpressionApplicable(PsiType lType, PsiType rType, IElementType opCode) {
            if (PsiType.NULL.equals(lType) || PsiType.NULL.equals(rType)) {
                return false;
            }
            // handle '==' and '!=' separately
            if (opCode == JavaTokenType.EQEQ || opCode == JavaTokenType.NE) {
                return lType instanceof PsiPrimitiveType && rType instanceof PsiClassType
                    || lType instanceof PsiClassType && rType instanceof PsiPrimitiveType;
            }
            // concat with a String
            if (opCode == JavaTokenType.PLUS) {
                if ((lType instanceof PsiClassType && lType.equalsToText(CommonClassNames.JAVA_LANG_STRING))
                    || (rType instanceof PsiClassType && rType.equalsToText(CommonClassNames.JAVA_LANG_STRING))) {
                    return false;
                }
            }
            // all other operations at least one should be of class type
            return lType instanceof PsiClassType || rType instanceof PsiClassType;
        }

        /**
         * @param type
         * @return promotion type to cast to or null if no casting needed
         */
        @Nullable
        private static PsiType calcUnaryNumericPromotionType(PsiPrimitiveType type) {
            if (PsiType.BYTE.equals(type) || PsiType.SHORT.equals(type) || PsiType.CHAR.equals(type) || PsiType.INT.equals(type)) {
                return PsiType.INT;
            }
            return null;
        }

        @Override
        @RequiredReadAction
        public void visitDeclarationStatement(@Nonnull PsiDeclarationStatement statement) {
            List<Evaluator> evaluators = new ArrayList<>();

            PsiElement[] declaredElements = statement.getDeclaredElements();
            for (PsiElement declaredElement : declaredElements) {
                if (declaredElement instanceof PsiLocalVariable localVariable) {
                    if (myCurrentFragmentEvaluator != null) {
                        PsiType lType = localVariable.getType();

                        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(localVariable.getProject()).getElementFactory();
                        try {
                            PsiExpression initialValue =
                                elementFactory.createExpressionFromText(PsiTypesUtil.getDefaultValueOfType(lType), null);
                            Object value = JavaConstantExpressionEvaluator.computeConstantExpression(initialValue, true);
                            myCurrentFragmentEvaluator.setInitialValue(localVariable.getName(), value);
                        }
                        catch (IncorrectOperationException e) {
                            LOG.error("Error while computing constant expression", e);
                        }

                        PsiExpression initializer = localVariable.getInitializer();
                        if (initializer != null) {
                            try {
                                if (!TypeConversionUtil.areTypesAssignmentCompatible(lType, initializer)) {
                                    throwEvaluateException(JavaDebuggerLocalize.evaluationErrorIncompatibleVariableInitializerType(
                                        localVariable.getName()
                                    ));
                                }
                                PsiType rType = initializer.getType();
                                initializer.accept(this);
                                Evaluator rEvaluator = myResult;

                                PsiExpression localVarReference =
                                    elementFactory.createExpressionFromText(localVariable.getName(), initializer);

                                localVarReference.accept(this);
                                Evaluator lEvaluator = myResult;
                                rEvaluator =
                                    handleAssignmentBoxingAndPrimitiveTypeConversions(localVarReference.getType(), rType, rEvaluator);

                                Evaluator assignment = new AssignmentEvaluator(lEvaluator, rEvaluator);
                                evaluators.add(assignment);
                            }
                            catch (IncorrectOperationException e) {
                                LOG.error("Error while computing expression", e);
                            }
                        }
                    }
                    else {
                        throw new EvaluateRuntimeException(
                            new EvaluateException(JavaDebuggerLocalize.evaluationErrorLocalVariableDeclarationsNotSupported(), null)
                        );
                    }
                }
                else {
                    throw new EvaluateRuntimeException(
                        new EvaluateException(JavaDebuggerLocalize.evaluationErrorUnsupportedDeclaration(declaredElement.getText()), null)
                    );
                }
            }

            if (!evaluators.isEmpty()) {
                CodeFragmentEvaluator codeFragmentEvaluator = new CodeFragmentEvaluator(myCurrentFragmentEvaluator);
                codeFragmentEvaluator.setStatements(evaluators.toArray(new Evaluator[evaluators.size()]));
                myResult = codeFragmentEvaluator;
            }
            else {
                myResult = null;
            }
        }

        @Override
        @RequiredReadAction
        public void visitConditionalExpression(@Nonnull PsiConditionalExpression expression) {
            LOG.debug("visitConditionalExpression {}", expression);
            PsiExpression thenExpression = expression.getThenExpression();
            PsiExpression elseExpression = expression.getElseExpression();
            if (thenExpression == null || elseExpression == null) {
                throwExpressionInvalid(expression);
            }
            PsiExpression condition = expression.getCondition();
            condition.accept(this);
            if (myResult == null) {
                throwExpressionInvalid(condition);
            }
            Evaluator conditionEvaluator = new UnBoxingEvaluator(myResult);
            thenExpression.accept(this);
            if (myResult == null) {
                throwExpressionInvalid(thenExpression);
            }
            Evaluator thenEvaluator = myResult;
            elseExpression.accept(this);
            if (myResult == null) {
                throwExpressionInvalid(elseExpression);
            }
            Evaluator elseEvaluator = myResult;
            myResult = new ConditionalExpressionEvaluator(conditionEvaluator, thenEvaluator, elseEvaluator);
        }

        @Override
        @RequiredReadAction
        public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
            LOG.debug("visitReferenceExpression {}", expression);
            PsiExpression qualifier = expression.getQualifierExpression();
            JavaResolveResult resolveResult = expression.advancedResolve(true);
            PsiElement element = resolveResult.getElement();

            if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
                @Nonnull PsiVariable variable = (PsiVariable) element;
                Value labeledValue = variable.getUserData(CodeFragmentFactoryContextWrapper.LABEL_VARIABLE_VALUE_KEY);
                if (labeledValue != null) {
                    myResult = new IdentityEvaluator(labeledValue);
                    return;
                }
                //synthetic variable
                if (variable.getContainingFile() instanceof PsiCodeFragment codeFragment
                    && myCurrentFragmentEvaluator != null
                    && myVisitedFragments.contains(codeFragment)) {
                    // psiVariable may live in PsiCodeFragment not only in debugger editors, for example Fabrique has such variables.
                    // So treat it as synthetic var only when this code fragment is located in DebuggerEditor,
                    // that's why we need to check that containing code fragment is the one we visited
                    myResult = new SyntheticVariableEvaluator(myCurrentFragmentEvaluator, variable.getName());
                    return;
                }
                // local variable
                String localName = variable.getName();
                PsiClass variableClass = getContainingClass(variable);
                if (getContextPsiClass() == null || getContextPsiClass().equals(variableClass)) {
                    PsiElement method = DebuggerUtilsEx.getContainingMethod(expression);
                    boolean canScanFrames = method instanceof PsiLambdaExpression || ContextUtil.isJspImplicit(variable);
                    myResult = new LocalVariableEvaluator(localName, canScanFrames);
                    return;
                }
                // the expression references final var outside the context's class (in some of the outer classes)
                int iterationCount = 0;
                PsiClass aClass = getOuterClass(getContextPsiClass());
                while (aClass != null && !aClass.equals(variableClass)) {
                    iterationCount++;
                    aClass = getOuterClass(aClass);
                }
                if (aClass != null) {
                    PsiExpression initializer = variable.getInitializer();
                    if (initializer != null) {
                        Object value = JavaPsiFacade.getInstance(variable.getProject())
                            .getConstantEvaluationHelper()
                            .computeConstantExpression(initializer);
                        if (value != null) {
                            PsiType type = resolveResult.getSubstitutor().substitute(variable.getType());
                            myResult = new LiteralEvaluator(value, type.getCanonicalText());
                            return;
                        }
                    }
                    Evaluator objectEvaluator = new ThisEvaluator(iterationCount);
                    //noinspection HardCodedStringLiteral
                    PsiClass classAt = myPosition != null ? JVMNameUtil.getClassAt(myPosition) : null;
                    FieldEvaluator.TargetClassFilter filter =
                        FieldEvaluator.createClassFilter(classAt != null ? classAt : getContextPsiClass());
                    myResult = createFallbackEvaluator(
                        new FieldEvaluator(objectEvaluator, filter, "val$" + localName),
                        new LocalVariableEvaluator(localName, true)
                    );
                    return;
                }
                throwEvaluateException(JavaDebuggerLocalize.evaluationErrorLocalVariableMissingFromClassClosure(localName));
            }
            else if (element instanceof PsiField field) {
                PsiClass fieldClass = field.getContainingClass();
                if (fieldClass == null) {
                    throwEvaluateException(JavaDebuggerLocalize.evaluationErrorCannotResolveFieldClass(field.getName()));
                    return;
                }
                Evaluator objectEvaluator;
                if (field.isStatic()) {
                    JVMName className = JVMNameUtil.getContextClassJVMQualifiedName(SourcePosition.createFromElement(field));
                    if (className == null) {
                        className = JVMNameUtil.getJVMQualifiedName(fieldClass);
                    }
                    objectEvaluator = new TypeEvaluator(className);
                }
                else if (qualifier != null) {
                    qualifier.accept(this);
                    objectEvaluator = myResult;
                }
                else if (fieldClass.equals(getContextPsiClass())
                    || (getContextPsiClass() != null && getContextPsiClass().isInheritor(fieldClass, true))) {
                    objectEvaluator = new ThisEvaluator();
                }
                else {  // myContextPsiClass != fieldClass && myContextPsiClass is not a subclass of fieldClass
                    int iterationCount = 0;
                    PsiClass aClass = getContextPsiClass();
                    while (aClass != null && !(aClass.equals(fieldClass) || aClass.isInheritor(fieldClass, true))) {
                        iterationCount++;
                        aClass = getOuterClass(aClass);
                    }
                    if (aClass == null) {
                        throwEvaluateException(JavaDebuggerLocalize.evaluationErrorCannotSourcesForFieldClass(field.getName()));
                    }
                    objectEvaluator = new ThisEvaluator(iterationCount);
                }
                myResult = new FieldEvaluator(objectEvaluator, FieldEvaluator.createClassFilter(fieldClass), field.getName());
            }
            else {
                //let's guess what this could be
                PsiElement nameElement = expression.getReferenceNameElement(); // get "b" part
                if (!(nameElement instanceof PsiIdentifier identifier)) {
                    //noinspection HardCodedStringLiteral
                    String elementDisplayString = nameElement != null ? nameElement.getText() : "(null)";
                    throwEvaluateException(JavaDebuggerLocalize.evaluationErrorIdentifierExpected(elementDisplayString));
                    return;
                }
                String name = identifier.getText();

                if (qualifier != null) {
                    if (qualifier instanceof PsiReferenceExpression refExpr && refExpr.resolve() instanceof PsiClass psiClass) {
                        // this is a call to a 'static' field
                        JVMName typeName = JVMNameUtil.getJVMQualifiedName(psiClass);
                        myResult = new FieldEvaluator(new TypeEvaluator(typeName), FieldEvaluator.createClassFilter(psiClass), name);
                    }
                    else {
                        qualifier.accept(this);
                        if (myResult == null) {
                            throwEvaluateException(JavaDebuggerLocalize.evaluationErrorCannotEvaluateQualifier(qualifier.getText()));
                        }

                        myResult = new FieldEvaluator(myResult, FieldEvaluator.createClassFilter(qualifier.getType()), name);
                    }
                }
                else {
                    myResult = createFallbackEvaluator(
                        new LocalVariableEvaluator(name, false),
                        new FieldEvaluator(new ThisEvaluator(), FieldEvaluator.TargetClassFilter.ALL, name)
                    );
                }
            }
        }

        private static Evaluator createFallbackEvaluator(final Evaluator primary, final Evaluator fallback) {
            return new Evaluator() {
                private boolean myIsFallback;

                @Override
                public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
                    try {
                        return primary.evaluate(context);
                    }
                    catch (EvaluateException e) {
                        try {
                            Object res = fallback.evaluate(context);
                            myIsFallback = true;
                            return res;
                        }
                        catch (EvaluateException e1) {
                            throw e;
                        }
                    }
                }

                @Override
                public Modifier getModifier() {
                    return myIsFallback ? fallback.getModifier() : primary.getModifier();
                }
            };
        }

        @RequiredReadAction
        private static void throwExpressionInvalid(PsiElement expression) {
            throwEvaluateException(JavaDebuggerLocalize.evaluationErrorInvalidExpression(expression.getText()));
        }

        private static void throwEvaluateException(@Nonnull LocalizeValue message) throws EvaluateRuntimeException {
            throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(message));
        }

        @Override
        @RequiredReadAction
        public void visitSuperExpression(@Nonnull PsiSuperExpression expression) {
            LOG.debug("visitSuperExpression {}", expression);
            int iterationCount = calcIterationCount(expression.getQualifier());
            myResult = new SuperEvaluator(iterationCount);
        }

        @Override
        @RequiredReadAction
        public void visitThisExpression(@Nonnull PsiThisExpression expression) {
            LOG.debug("visitThisExpression {}", expression);
            int iterationCount = calcIterationCount(expression.getQualifier());
            myResult = new ThisEvaluator(iterationCount);
        }

        @RequiredReadAction
        private int calcIterationCount(PsiJavaCodeReferenceElement qualifier) {
            if (qualifier != null) {
                return calcIterationCount(qualifier.resolve(), qualifier.getText());
            }
            return 0;
        }

        private int calcIterationCount(PsiElement targetClass, String name) {
            int iterationCount = 0;
            if (targetClass == null || getContextPsiClass() == null) {
                throwEvaluateException(JavaDebuggerLocalize.evaluationErrorInvalidExpression(name));
            }
            try {
                PsiClass aClass = getContextPsiClass();
                while (aClass != null && !aClass.equals(targetClass)) {
                    iterationCount++;
                    aClass = getOuterClass(aClass);
                }
            }
            catch (Exception e) {
                //noinspection ThrowableResultOfMethodCallIgnored
                throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(e));
            }
            return iterationCount;
        }

        @Override
        @RequiredReadAction
        public void visitInstanceOfExpression(@Nonnull PsiInstanceOfExpression expression) {
            LOG.debug("visitInstanceOfExpression {}", expression);
            PsiTypeElement checkType = expression.getCheckType();
            if (checkType == null) {
                throwExpressionInvalid(expression);
            }
            PsiType type = checkType.getType();
            expression.getOperand().accept(this);
            //    ClassObjectEvaluator typeEvaluator = new ClassObjectEvaluator(type.getCanonicalText());
            Evaluator operandEvaluator = myResult;
            myResult = new InstanceofEvaluator(operandEvaluator, new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(type)));
        }

        @Override
        public void visitParenthesizedExpression(@Nonnull PsiParenthesizedExpression expression) {
            LOG.debug("visitParenthesizedExpression {}", expression);
            PsiExpression expr = expression.getExpression();
            if (expr != null) {
                expr.accept(this);
            }
        }

        @Override
        @RequiredReadAction
        public void visitPostfixExpression(@Nonnull PsiPostfixExpression expression) {
            if (expression.getType() == null) {
                throwEvaluateException(JavaDebuggerLocalize.evaluationErrorUnknownExpressionType(expression.getText()));
            }

            PsiExpression operandExpression = expression.getOperand();
            operandExpression.accept(this);

            Evaluator operandEvaluator = myResult;

            IElementType operation = expression.getOperationTokenType();
            PsiType operandType = operandExpression.getType();
            @Nullable PsiType unboxedOperandType = PsiPrimitiveType.getUnboxedType(operandType);

            Evaluator incrementImpl = createBinaryEvaluator(
                operandEvaluator,
                operandType,
                new LiteralEvaluator(1, "int"),
                PsiType.INT,
                operation == JavaTokenType.PLUSPLUS ? JavaTokenType.PLUS : JavaTokenType.MINUS,
                unboxedOperandType != null ? unboxedOperandType : operandType
            );
            if (unboxedOperandType != null) {
                incrementImpl = new BoxingEvaluator(incrementImpl);
            }
            myResult = new PostfixOperationEvaluator(operandEvaluator, incrementImpl);
        }

        @Override
        @RequiredReadAction
        public void visitPrefixExpression(PsiPrefixExpression expression) {
            PsiType expressionType = expression.getType();
            if (expressionType == null) {
                throwEvaluateException(JavaDebuggerLocalize.evaluationErrorUnknownExpressionType(expression.getText()));
            }

            PsiExpression operandExpression = expression.getOperand();
            if (operandExpression == null) {
                throwEvaluateException(JavaDebuggerLocalize.evaluationErrorUnknownExpressionOperand(expression.getText()));
            }

            operandExpression.accept(this);
            Evaluator operandEvaluator = myResult;

            // handle unboxing issues
            PsiType operandType = operandExpression.getType();
            @Nullable PsiType unboxedOperandType = PsiPrimitiveType.getUnboxedType(operandType);

            IElementType operation = expression.getOperationTokenType();

            if (operation == JavaTokenType.PLUSPLUS || operation == JavaTokenType.MINUSMINUS) {
                try {
                    Evaluator rightEval = createBinaryEvaluator(
                        operandEvaluator,
                        operandType,
                        new LiteralEvaluator(1, "int"),
                        PsiType.INT,
                        operation == JavaTokenType.PLUSPLUS ? JavaTokenType.PLUS : JavaTokenType.MINUS,
                        unboxedOperandType != null ? unboxedOperandType : operandType
                    );
                    myResult =
                        new AssignmentEvaluator(operandEvaluator, unboxedOperandType != null ? new BoxingEvaluator(rightEval) : rightEval);
                }
                catch (IncorrectOperationException e) {
                    LOG.error("Error while evaluating expression", e);
                }
            }
            else {
                if (JavaTokenType.PLUS.equals(operation) || JavaTokenType.MINUS.equals(operation) || JavaTokenType.TILDE.equals(operation)) {
                    operandEvaluator = handleUnaryNumericPromotion(operandType, operandEvaluator);
                }
                else {
                    if (unboxedOperandType != null) {
                        operandEvaluator = new UnBoxingEvaluator(operandEvaluator);
                    }
                }
                myResult = new UnaryExpressionEvaluator(
                    operation,
                    expressionType.getCanonicalText(),
                    operandEvaluator,
                    expression.getOperationSign().getText()
                );
            }
        }

        @Override
        @RequiredReadAction
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            LOG.debug("visitMethodCallExpression {}", expression);
            PsiExpressionList argumentList = expression.getArgumentList();
            PsiExpression[] argExpressions = argumentList.getExpressions();
            Evaluator[] argumentEvaluators = new Evaluator[argExpressions.length];
            // evaluate arguments
            for (int idx = 0; idx < argExpressions.length; idx++) {
                PsiExpression psiExpression = argExpressions[idx];
                psiExpression.accept(this);
                if (myResult == null) {
                    // cannot build evaluator
                    throwExpressionInvalid(psiExpression);
                }
                argumentEvaluators[idx] = DisableGC.create(myResult);
            }
            PsiReferenceExpression methodExpr = expression.getMethodExpression();

            JavaResolveResult resolveResult = methodExpr.advancedResolve(false);
            PsiMethod psiMethod = (PsiMethod) resolveResult.getElement();

            PsiExpression qualifier = methodExpr.getQualifierExpression();
            Evaluator objectEvaluator;
            JVMName contextClass = null;

            if (psiMethod != null) {
                PsiClass methodPsiClass = psiMethod.getContainingClass();
                contextClass = JVMNameUtil.getJVMQualifiedName(methodPsiClass);
                if (psiMethod.isStatic()) {
                    objectEvaluator = new TypeEvaluator(contextClass);
                }
                else if (qualifier != null) {
                    qualifier.accept(this);
                    objectEvaluator = myResult;
                }
                else {
                    int iterationCount = 0;
                    if (resolveResult.getCurrentFileResolveScope() instanceof PsiClass scopeClass) {
                        PsiClass aClass = getContextPsiClass();
                        while (aClass != null && !aClass.equals(scopeClass)) {
                            aClass = getOuterClass(aClass);
                            iterationCount++;
                        }
                    }
                    objectEvaluator = new ThisEvaluator(iterationCount);
                }
            }
            else {
                //trying to guess
                if (qualifier != null) {
                    PsiType type = qualifier.getType();

                    if (type != null) {
                        contextClass = JVMNameUtil.getJVMQualifiedName(type);
                    }

                    if (qualifier instanceof PsiReferenceExpression qRefExpr && qRefExpr.resolve() instanceof PsiClass) {
                        // this is a call to a 'static' method but class is not available, try to evaluate by qName
                        if (contextClass == null) {
                            contextClass = JVMNameUtil.getJVMRawText(qRefExpr.getQualifiedName());
                        }
                        objectEvaluator = new TypeEvaluator(contextClass);
                    }
                    else {
                        qualifier.accept(this);
                        objectEvaluator = myResult;
                    }
                }
                else {
                    objectEvaluator = new ThisEvaluator();
                    contextClass = JVMNameUtil.getContextClassJVMQualifiedName(myPosition);
                    if (contextClass == null && myContextPsiClass != null) {
                        contextClass = JVMNameUtil.getJVMQualifiedName(myContextPsiClass);
                    }
                    //else {
                    //  throw new EvaluateRuntimeException(EvaluateExceptionUtil.createEvaluateException(
                    //    DebuggerBundle.message("evaluation.error.method.not.found", methodExpr.getReferenceName()))
                    //  );
                    //}
                }
            }

            if (objectEvaluator == null) {
                throwExpressionInvalid(expression);
            }

            if (psiMethod != null && !psiMethod.isConstructor()) {
                if (psiMethod.getReturnType() == null) {
                    throwEvaluateException(JavaDebuggerLocalize.evaluationErrorUnknownMethodReturnType(psiMethod.getText()));
                }
            }

            boolean defaultInterfaceMethod = false;
            boolean mustBeVararg = false;

            if (psiMethod != null) {
                processBoxingConversions(
                    psiMethod.getParameterList().getParameters(),
                    argExpressions,
                    resolveResult.getSubstitutor(),
                    argumentEvaluators
                );
                defaultInterfaceMethod = psiMethod.hasModifierProperty(PsiModifier.DEFAULT);
                mustBeVararg = psiMethod.isVarArgs();
            }

            myResult = new MethodEvaluator(
                objectEvaluator,
                contextClass,
                methodExpr.getReferenceName(),
                psiMethod != null ? JVMNameUtil.getJVMSignature(psiMethod) : null,
                argumentEvaluators,
                defaultInterfaceMethod,
                mustBeVararg
            );
        }

        @Override
        @RequiredReadAction
        public void visitLiteralExpression(@Nonnull PsiLiteralExpression expression) {
            HighlightInfo.Builder parsingError = HighlightUtil.checkLiteralExpressionParsingError(expression, null, null);
            if (parsingError != null) {
                HighlightInfo hlInfo = parsingError.create();
                if (hlInfo != null) {
                    throwEvaluateException(hlInfo.getDescription());
                    return;
                }
            }

            PsiType type = expression.getType();
            if (type == null) {
                throwEvaluateException(LocalizeValue.localizeTODO(expression + ": null type"));
                return;
            }

            myResult = new LiteralEvaluator(expression.getValue(), type.getCanonicalText());
        }

        @Override
        @RequiredReadAction
        public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
            PsiExpression indexExpression = expression.getIndexExpression();
            if (indexExpression == null) {
                throwExpressionInvalid(expression);
            }
            indexExpression.accept(this);
            Evaluator indexEvaluator = handleUnaryNumericPromotion(indexExpression.getType(), myResult);

            expression.getArrayExpression().accept(this);
            Evaluator arrayEvaluator = myResult;
            myResult = new ArrayAccessEvaluator(arrayEvaluator, indexEvaluator);
        }

        /**
         * Handles unboxing and numeric promotion issues for
         * - array dimension expressions
         * - array index expression
         * - unary +, -, and ~ operations
         *
         * @param operandExpressionType
         * @param operandEvaluator      @return operandEvaluator possibly 'wrapped' with necessary unboxing and type-casting evaluators
         *                              to make returning value suitable for mentioned contexts
         */
        private static Evaluator handleUnaryNumericPromotion(PsiType operandExpressionType, Evaluator operandEvaluator) {
            PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(operandExpressionType);
            if (unboxedType != null && !PsiType.BOOLEAN.equals(unboxedType)) {
                operandEvaluator = new UnBoxingEvaluator(operandEvaluator);
            }

            // handle numeric promotion
            PsiType unboxedIndexType = unboxedType != null ? unboxedType : operandExpressionType;
            if (unboxedIndexType instanceof PsiPrimitiveType primitiveType) {
                PsiType promotionType = calcUnaryNumericPromotionType(primitiveType);
                if (promotionType != null) {
                    operandEvaluator = new TypeCastEvaluator(operandEvaluator, promotionType.getCanonicalText(), true);
                }
            }
            return operandEvaluator;
        }

        @Override
        @RequiredReadAction
        public void visitTypeCastExpression(PsiTypeCastExpression expression) {
            PsiExpression operandExpr = expression.getOperand();
            if (operandExpr == null) {
                throwExpressionInvalid(expression);
            }
            operandExpr.accept(this);
            Evaluator operandEvaluator = myResult;
            PsiTypeElement castTypeElem = expression.getCastType();
            if (castTypeElem == null) {
                throwExpressionInvalid(expression);
            }
            PsiType castType = castTypeElem.getType();
            PsiType operandType = operandExpr.getType();

            // if operand type can not be resolved in current context - leave it for runtime checks
            if (operandType != null
                && !TypeConversionUtil.areTypesConvertible(operandType, castType)
                && PsiUtil.resolveClassInType(operandType) != null) {
                throw new EvaluateRuntimeException(new EvaluateException(JavaCompilationErrorLocalize.castInconvertible(
                    JavaHighlightUtil.formatType(operandType),
                    JavaHighlightUtil.formatType(castType)
                ).get()));
            }

            boolean shouldPerformBoxingConversion =
                operandType != null && TypeConversionUtil.boxingConversionApplicable(castType, operandType);
            boolean castingToPrimitive = castType instanceof PsiPrimitiveType;
            if (shouldPerformBoxingConversion && castingToPrimitive) {
                operandEvaluator = new UnBoxingEvaluator(operandEvaluator);
            }

            boolean performCastToWrapperClass = shouldPerformBoxingConversion && !castingToPrimitive;

            if (!(PsiUtil.resolveClassInClassTypeOnly(castType) instanceof PsiTypeParameter)) {
                String castTypeName = castType.getCanonicalText();
                if (performCastToWrapperClass) {
                    PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(castType);
                    if (unboxedType != null) {
                        castTypeName = unboxedType.getCanonicalText();
                    }
                }

                myResult = new TypeCastEvaluator(operandEvaluator, castTypeName, castingToPrimitive);
            }

            if (performCastToWrapperClass) {
                myResult = new BoxingEvaluator(myResult);
            }
        }

        @Override
        public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
            PsiType type = expression.getOperand().getType();

            if (type instanceof PsiPrimitiveType primitiveType) {
                JVMName typeName = JVMNameUtil.getJVMRawText(primitiveType.getBoxedTypeName());
                myResult = new FieldEvaluator(new TypeEvaluator(typeName), FieldEvaluator.TargetClassFilter.ALL, "TYPE");
            }
            else {
                myResult = new ClassObjectEvaluator(new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(type)));
            }
        }

        @Override
        public void visitLambdaExpression(@Nonnull PsiLambdaExpression expression) {
            throw new EvaluateRuntimeException(new UnsupportedExpressionException(
                JavaDebuggerLocalize.evaluationErrorLambdaEvaluationNotSupported()
            ));
        }

        @Override
        public void visitMethodReferenceExpression(@Nonnull PsiMethodReferenceExpression expression) {
            throw new EvaluateRuntimeException(new UnsupportedExpressionException(
                JavaDebuggerLocalize.evaluationErrorMethodReferenceEvaluationNotSupported()
            ));
        }

        @Override
        @RequiredReadAction
        public void visitNewExpression(PsiNewExpression expression) {
            PsiType expressionPsiType = expression.getType();
            if (expressionPsiType instanceof PsiArrayType) {
                Evaluator dimensionEvaluator = null;
                PsiExpression[] dimensions = expression.getArrayDimensions();
                if (dimensions.length == 1) {
                    PsiExpression dimensionExpression = dimensions[0];
                    dimensionExpression.accept(this);
                    if (myResult != null) {
                        dimensionEvaluator = handleUnaryNumericPromotion(dimensionExpression.getType(), myResult);
                    }
                    else {
                        throwEvaluateException(
                            JavaDebuggerLocalize.evaluationErrorInvalidArrayDimensionExpression(dimensionExpression.getText())
                        );
                    }
                }
                else if (dimensions.length > 1) {
                    throwEvaluateException(JavaDebuggerLocalize.evaluationErrorMultiDimensionalArraysCreationNotSupported());
                }

                Evaluator initializerEvaluator = null;
                PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
                if (arrayInitializer != null) {
                    if (dimensionEvaluator != null) { // initializer already exists
                        throwExpressionInvalid(expression);
                    }
                    arrayInitializer.accept(this);
                    if (myResult != null) {
                        initializerEvaluator = handleUnaryNumericPromotion(arrayInitializer.getType(), myResult);
                    }
                    else {
                        throwExpressionInvalid(arrayInitializer);
                    }
                    /*
                    PsiExpression[] initializers = arrayInitializer.getInitializers();
                    initializerEvaluators = new Evaluator[initializers.length];
                    for (int idx = 0; idx < initializers.length; idx++) {
                        PsiExpression initializer = initializers[idx];
                        initializer.accept(this);
                        if (myResult instanceof Evaluator) {
                            initializerEvaluators[idx] = myResult;
                        }
                        else {
                            throw new EvaluateException("Invalid expression for array initializer: " + initializer.getText(), true);
                        }
                    }
                    */
                }
                if (dimensionEvaluator == null && initializerEvaluator == null) {
                    throwExpressionInvalid(expression);
                }
                myResult = new NewArrayInstanceEvaluator(
                    new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(expressionPsiType)),
                    dimensionEvaluator,
                    initializerEvaluator
                );
            }
            else if (expressionPsiType instanceof PsiClassType classType) { // must be a class ref
                PsiClass aClass = classType.resolve();
                if (aClass instanceof PsiAnonymousClass) {
                    throw new EvaluateRuntimeException(new UnsupportedExpressionException(
                        JavaDebuggerLocalize.evaluationErrorAnonymousClassEvaluationNotSupported()
                    ));
                }
                PsiExpressionList argumentList = expression.getArgumentList();
                if (argumentList == null) {
                    throwExpressionInvalid(expression);
                }
                PsiExpression[] argExpressions = argumentList.getExpressions();
                JavaResolveResult constructorResolveResult = expression.resolveMethodGenerics();
                PsiMethod constructor = (PsiMethod) constructorResolveResult.getElement();
                if (constructor == null && argExpressions.length > 0) {
                    throw new EvaluateRuntimeException(
                        new EvaluateException(JavaDebuggerLocalize.evaluationErrorCannotResolveConstructor(expression.getText()), null)
                    );
                }
                Evaluator[] argumentEvaluators = new Evaluator[argExpressions.length];
                // evaluate arguments
                for (int idx = 0; idx < argExpressions.length; idx++) {
                    PsiExpression argExpression = argExpressions[idx];
                    argExpression.accept(this);
                    if (myResult != null) {
                        argumentEvaluators[idx] = DisableGC.create(myResult);
                    }
                    else {
                        throwExpressionInvalid(argExpression);
                    }
                }

                if (constructor != null) {
                    processBoxingConversions(
                        constructor.getParameterList().getParameters(),
                        argExpressions,
                        constructorResolveResult.getSubstitutor(),
                        argumentEvaluators
                    );
                }

                if (aClass != null) {
                    PsiClass containingClass = aClass.getContainingClass();
                    if (containingClass != null && !aClass.isStatic()) {
                        PsiExpression qualifier = expression.getQualifier();
                        if (qualifier != null) {
                            qualifier.accept(this);
                            if (myResult != null) {
                                argumentEvaluators = ArrayUtil.prepend(myResult, argumentEvaluators);
                            }
                        }
                        else {
                            argumentEvaluators =
                                ArrayUtil.prepend(new ThisEvaluator(calcIterationCount(containingClass, "this")), argumentEvaluators);
                        }
                    }
                }

                JVMName signature = JVMNameUtil.getJVMConstructorSignature(constructor, aClass);
                myResult = new NewClassInstanceEvaluator(
                    new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(expressionPsiType)),
                    signature,
                    argumentEvaluators
                );
            }
            else if (expressionPsiType != null) {
                throwEvaluateException(LocalizeValue.localizeTODO("Unsupported expression type: " + expressionPsiType.getPresentableText()));
            }
            else {
                throwEvaluateException(LocalizeValue.localizeTODO("Unknown type for expression: " + expression.getText()));
            }
        }

        @Override
        @RequiredReadAction
        public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
            PsiExpression[] initializers = expression.getInitializers();
            Evaluator[] evaluators = new Evaluator[initializers.length];
            PsiType type = expression.getType();
            boolean primitive = type instanceof PsiArrayType arrayType && arrayType.getComponentType() instanceof PsiPrimitiveType;
            for (int idx = 0; idx < initializers.length; idx++) {
                PsiExpression initializer = initializers[idx];
                initializer.accept(this);
                if (myResult != null) {
                    Evaluator coerced =
                        primitive ? handleUnaryNumericPromotion(initializer.getType(), myResult) : new BoxingEvaluator(myResult);
                    evaluators[idx] = DisableGC.create(coerced);
                }
                else {
                    throwExpressionInvalid(initializer);
                }
            }
            myResult = new ArrayInitializerEvaluator(evaluators);
            if (type != null && !(expression.getParent() instanceof PsiNewExpression)) {
                myResult = new NewArrayInstanceEvaluator(new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(type)), null, myResult);
            }
        }

        @Nullable
        private static PsiClass getOuterClass(PsiClass aClass) {
            return aClass == null ? null : PsiTreeUtil.getContextOfType(aClass, PsiClass.class, true);
        }

        private PsiClass getContainingClass(PsiVariable variable) {
            PsiElement element = PsiTreeUtil.getParentOfType(variable.getParent(), PsiClass.class, false);
            return element == null ? getContextPsiClass() : (PsiClass) element;
        }

        @Nullable
        public PsiClass getContextPsiClass() {
            return myContextPsiClass;
        }

        @RequiredReadAction
        protected ExpressionEvaluator buildElement(PsiElement element) throws EvaluateException {
            if (!element.isValid()) {
                LOG.error("Element is invalid", new Throwable());
            }

            myContextPsiClass = PsiTreeUtil.getContextOfType(element, PsiClass.class, false);
            try {
                element.accept(this);
            }
            catch (EvaluateRuntimeException e) {
                throw e.getCause();
            }
            if (myResult == null) {
                throw EvaluateExceptionUtil.createEvaluateException(
                    JavaDebuggerLocalize.evaluationErrorInvalidExpression(element.toString())
                );
            }
            return new ExpressionEvaluatorImpl(myResult);
        }
    }

    private static void processBoxingConversions(
        PsiParameter[] declaredParams,
        PsiExpression[] actualArgumentExpressions,
        PsiSubstitutor methodResolveSubstitutor,
        Evaluator[] argumentEvaluators
    ) {
        if (declaredParams.length > 0) {
            int paramCount = Math.max(declaredParams.length, actualArgumentExpressions.length);
            PsiType varargType = null;
            for (int idx = 0; idx < paramCount; idx++) {
                if (idx >= actualArgumentExpressions.length) {
                    break; // actual arguments count is less than number of declared params
                }
                PsiType declaredParamType;
                if (idx < declaredParams.length) {
                    declaredParamType = methodResolveSubstitutor.substitute(declaredParams[idx].getType());
                    if (declaredParamType instanceof PsiEllipsisType ellipsisType) {
                        declaredParamType = varargType = ellipsisType.getComponentType();
                    }
                }
                else if (varargType != null) {
                    declaredParamType = varargType;
                }
                else {
                    break;
                }
                PsiType actualArgType = actualArgumentExpressions[idx].getType();
                if (TypeConversionUtil.boxingConversionApplicable(declaredParamType, actualArgType)) {
                    Evaluator argEval = argumentEvaluators[idx];
                    argumentEvaluators[idx] =
                        declaredParamType instanceof PsiPrimitiveType ? new UnBoxingEvaluator(argEval) : new BoxingEvaluator(argEval);
                }
            }
        }
    }
}
