// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java;

import com.intellij.java.analysis.codeInsight.daemon.JavaImplicitUsageProvider;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.codeInsight.Nullability;
import consulo.language.editor.ImplicitUsageProvider;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.java.language.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.ConsumedStreamUtils;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.CustomMethodHandlers;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.MethodContract;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraints;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaPolyadicPartAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaSwitchDeconstructionLabelAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaSwitchLabelTakenAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.AccessorInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.AllNotNullInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.AssertAllInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.AssertInstanceOfInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.AssertJInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.AssumeInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.BoxingInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.CallInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.ClassMethodsInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.CollectionUpdateInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.EnumCompareInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.IndexOfInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.LambdaInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.MapGetOrDefaultInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.MapUpdateInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.OptionalChainInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.SimpleMethodInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.StreamChainInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inliner.TransformInliner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.ArrayAccessInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.AssignInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.BooleanAndOrInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.BooleanBinaryInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.CheckNotNullInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.EndOfInstanceInitializerInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.EscapeInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.FoldArrayInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.InstanceofInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.JavaArrayStoreInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.JvmPushForWriteInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.MethodCallInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.MethodReferenceInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.NotInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.NumericBinaryInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.PrimitiveConversionInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.SimpleEqWithoutCorrectionInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.StringConcatInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.ThrowInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.TypeCastInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.TrapTracker;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors.ArrayElementDescriptor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors.AssertionDisabledDescriptor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors.GetterDescriptor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors.ThisDescriptor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems.ArrayIndexProblem;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems.ConsumedStreamProblem;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems.ContractFailureProblem;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems.NegativeArraySizeProblem;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer.EnterFinallyTrap;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer.EnterFinallyTrap.TwrFinally;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer.ExceptionTransfer;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer.ExitFinallyTransfer;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer.InsideFinallyTrap;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer.InsideInlinedBlockTrap;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer.InstructionTransfer;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer.TryCatchTrap;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer.TryCatchTrap.CatchClauseDescriptor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer.TryCatchTrap.JavaCatchClauseDescriptor;
import consulo.language.dataFlow.ir.DfaAnchor;
import consulo.language.dataFlow.ir.ClosureInstruction;
import consulo.language.dataFlow.ir.ConditionalGotoInstruction;
import consulo.language.dataFlow.ir.ControlFlow;
import consulo.language.dataFlow.ir.ControlFlow.ControlFlowOffset;
import consulo.language.dataFlow.ir.ControlFlow.DeferredOffset;
import consulo.language.dataFlow.ir.ControlTransferInstruction;
import consulo.language.dataFlow.ir.DataFlowIRProvider;
import consulo.language.dataFlow.ir.DupInstruction;
import consulo.language.dataFlow.ir.EnsureInstruction;
import consulo.language.dataFlow.ir.EvalUnknownInstruction;
import consulo.language.dataFlow.ir.FinishElementInstruction;
import consulo.language.dataFlow.ir.FlushFieldsInstruction;
import consulo.language.dataFlow.ir.FlushVariableInstruction;
import consulo.language.dataFlow.ir.GetQualifiedValueInstruction;
import consulo.language.dataFlow.ir.GotoInstruction;
import consulo.language.dataFlow.ir.Instruction;
import consulo.language.dataFlow.ir.PopInstruction;
import consulo.language.dataFlow.ir.PushInstruction;
import consulo.language.dataFlow.ir.PushValueInstruction;
import consulo.language.dataFlow.ir.ResultOfInstruction;
import consulo.language.dataFlow.ir.ReturnInstruction;
import consulo.language.dataFlow.ir.SimpleAssignmentInstruction;
import consulo.language.dataFlow.ir.SpliceInstruction;
import consulo.language.dataFlow.ir.SwapInstruction;
import consulo.language.dataFlow.ir.WrapDerivedVariableInstruction;
import consulo.language.dataFlow.rangeSet.LongRangeBinOp;
import consulo.language.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfStreamStateType;
import consulo.language.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import consulo.language.dataFlow.value.DfaControlTransferValue;
import consulo.language.dataFlow.value.DfaControlTransferValue.Trap;
import consulo.language.dataFlow.value.DfaTypeValue;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import consulo.language.dataFlow.value.RelationType;
import consulo.language.dataFlow.value.VariableDescriptor;
import com.intellij.java.language.impl.psi.util.JavaPsiSwitchUtil;
import consulo.logging.Logger;
import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.psi.JavaCodeFragment;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.java.language.psi.JavaResolveResult;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.LambdaUtil;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiArrayAccessExpression;
import com.intellij.java.language.psi.PsiArrayInitializerExpression;
import com.intellij.java.language.psi.PsiArrayType;
import com.intellij.java.language.psi.PsiAssertStatement;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiBlockStatement;
import com.intellij.java.language.psi.PsiBreakStatement;
import com.intellij.java.language.psi.PsiCall;
import com.intellij.java.language.psi.PsiCapturedWildcardType;
import com.intellij.java.language.psi.PsiCaseLabelElement;
import com.intellij.java.language.psi.PsiCaseLabelElementList;
import com.intellij.java.language.psi.PsiCatchSection;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassInitializer;
import com.intellij.java.language.psi.PsiClassObjectAccessExpression;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiConditionalExpression;
import com.intellij.java.language.psi.PsiConstructorCall;
import com.intellij.java.language.psi.PsiContinueStatement;
import com.intellij.java.language.psi.PsiDeclarationStatement;
import com.intellij.java.language.psi.PsiDeconstructionPattern;
import com.intellij.java.language.psi.PsiDefaultCaseLabelElement;
import com.intellij.java.language.psi.PsiDoWhileStatement;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiEmptyStatement;
import com.intellij.java.language.psi.PsiEnumConstant;
import consulo.language.psi.PsiErrorElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionCodeFragment;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiExpressionListStatement;
import com.intellij.java.language.psi.PsiExpressionStatement;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiForStatement;
import com.intellij.java.language.psi.PsiForeachPatternStatement;
import com.intellij.java.language.psi.PsiForeachStatement;
import com.intellij.java.language.psi.PsiForeachStatementBase;
import com.intellij.java.language.psi.PsiFragment;
import com.intellij.java.language.psi.PsiIfStatement;
import com.intellij.java.language.psi.PsiInstanceOfExpression;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiLabeledStatement;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiLambdaExpressionType;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.PsiLoopStatement;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiMethodReferenceExpression;
import com.intellij.java.language.psi.PsiMethodReferenceType;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiNewExpression;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiParenthesizedExpression;
import com.intellij.java.language.psi.PsiPattern;
import com.intellij.java.language.psi.PsiPatternVariable;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.intellij.java.language.psi.PsiPostfixExpression;
import com.intellij.java.language.psi.PsiPrefixExpression;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiRecordComponent;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiResourceExpression;
import com.intellij.java.language.psi.PsiResourceList;
import com.intellij.java.language.psi.PsiResourceListElement;
import com.intellij.java.language.psi.PsiResourceVariable;
import com.intellij.java.language.psi.PsiReturnStatement;
import com.intellij.java.language.psi.PsiStatement;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiSwitchBlock;
import com.intellij.java.language.psi.PsiSwitchExpression;
import com.intellij.java.language.psi.PsiSwitchLabelStatement;
import com.intellij.java.language.psi.PsiSwitchLabelStatementBase;
import com.intellij.java.language.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.java.language.psi.PsiSwitchStatement;
import com.intellij.java.language.psi.PsiSynchronizedStatement;
import com.intellij.java.language.psi.PsiTemplate;
import com.intellij.java.language.psi.PsiTemplateExpression;
import com.intellij.java.language.psi.PsiThisExpression;
import com.intellij.java.language.psi.PsiThrowStatement;
import com.intellij.java.language.psi.PsiTryStatement;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeCastExpression;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.PsiTypeTestPattern;
import com.intellij.java.language.psi.PsiTypes;
import com.intellij.java.language.psi.PsiUnaryExpression;
import com.intellij.java.language.psi.PsiVariable;
import com.intellij.java.language.psi.PsiWhileStatement;
import com.intellij.java.language.psi.PsiWildcardType;
import com.intellij.java.language.psi.PsiYieldStatement;
import com.intellij.java.language.psi.augment.PsiAugmentProvider;
import com.intellij.java.language.psi.util.JavaVarTypeUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiEmptyExpressionImpl;
import consulo.language.ast.IElementType;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.impl.psi.util.JavaPsiPatternUtil;
import com.intellij.java.language.impl.psi.util.JavaPsiRecordUtil;
import com.intellij.java.language.impl.psi.util.JavaPsiStringTemplateUtil;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.psi.PsiUtilCore;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.ObjectUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.FList;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.siyeh.ig.psiutils.CountingLoop;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.InstanceOfUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.NullabilityProblemKind.deconstructionMatchException;
import static com.intellij.java.language.psi.CommonClassNames.JAVA_LANG_ASSERTION_ERROR;
import static com.intellij.java.language.psi.CommonClassNames.JAVA_LANG_ERROR;
import static com.intellij.java.language.psi.CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION;
import static com.intellij.java.language.psi.CommonClassNames.JAVA_LANG_THROWABLE;
import static com.intellij.java.language.psi.CommonClassNames.JAVA_UTIL_ARRAYS;
import static com.intellij.java.language.psi.CommonClassNames.JAVA_UTIL_COLLECTION;
import static com.intellij.java.language.psi.CommonClassNames.JAVA_UTIL_LIST;
import static com.intellij.java.language.psi.CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM;

public class ControlFlowAnalyzer extends JavaElementVisitor {
  private static final Logger LOG = Logger.getInstance(ControlFlowAnalyzer.class);
  private static final CallMatcher LIST_INITIALIZER = CallMatcher.anyOf(
    CallMatcher.staticCall(JAVA_UTIL_ARRAYS, "asList"),
    CallMatcher.staticCall(JAVA_UTIL_LIST, "of"));
  static final int MAX_UNROLL_SIZE = 3;
  private static final int MAX_ARRAY_INDEX_FOR_INITIALIZER = 32;
  private final PsiElement myCodeFragment;
  private final boolean myInlining;
  private final DfaValueFactory myFactory;
  private final TrapTracker myTrapTracker;
  private ControlFlow myCurrentFlow;
  private final Map<PsiExpression, NullabilityProblemKind<? super PsiExpression>> myCustomNullabilityProblems = new HashMap<>();
  private ExpressionBlockContext myExpressionBlockContext;

  /**
   * @param valueFactory factory to create values
   * @param codeFragment code fragment to analyze:
   *                     normally a PsiCodeBlock or PsiExpression.
   *                     If PsiWhileStatement or PsiDoWhileStatement then only one loop iteration will be analyzed
   *                     (similar to analyzing the loop body, but condition is analyzed as well).
   *                     If PsiClass then class initializers + field initializers will be analyzed
   * @param inlining if true inlining is performed for known method calls
   */
  ControlFlowAnalyzer(DfaValueFactory valueFactory, PsiElement codeFragment, boolean inlining) {
    myInlining = inlining;
    myFactory = valueFactory;
    myCodeFragment = codeFragment;
    myTrapTracker = new TrapTracker(valueFactory, JavaClassDef.typeConstraintFactory(codeFragment));
  }

  private void buildClassInitializerFlow(PsiClass psiClass, boolean isStatic) {
    for (PsiElement element = psiClass.getFirstChild(); element != null; element = element.getNextSibling()) {
      if (element instanceof PsiField field &&
          !field.hasInitializer() && field.hasModifierProperty(PsiModifier.STATIC) == isStatic) {
        visitField(field);
      }
    }
    if (!isStatic &&
        ContainerUtil.exists(psiClass.getProject().getExtensionList(ImplicitUsageProvider.class), p -> p instanceof JavaImplicitUsageProvider javaProvider && javaProvider.isClassWithCustomizedInitialization(psiClass))) {
      addInstruction(new EscapeInstruction(List.of(new ThisDescriptor(psiClass))));
      addInstruction(new FlushFieldsInstruction());
    }
    for (PsiElement element = psiClass.getFirstChild(); element != null; element = element.getNextSibling()) {
      if ((element instanceof PsiField field &&
           field.hasInitializer() && PsiAugmentProvider.canTrustFieldInitializer(field) || element instanceof PsiClassInitializer) &&
          ((PsiMember)element).hasModifierProperty(PsiModifier.STATIC) == isStatic) {
        element.accept(this);
      }
    }
    if (!isStatic) {
      addInstruction(new EndOfInstanceInitializerInstruction());
    }
    addInstruction(new FlushFieldsInstruction());
  }

  @Nullable ControlFlow buildControlFlow() {
    myCurrentFlow = new ControlFlow(myFactory, myCodeFragment);
    addInstruction(new FinishElementInstruction(null)); // to initialize LVA
    try {
      if (myCodeFragment instanceof PsiClass psiClass) {
        // if(unknown) { staticInitializer(); } else { instanceInitializer(); }
        pushUnknown();
        ConditionalGotoInstruction conditionalGoto = new ConditionalGotoInstruction(null, DfType.TOP);
        addInstruction(conditionalGoto);
        buildClassInitializerFlow(psiClass, true);
        GotoInstruction unconditionalGoto = new GotoInstruction(null);
        addInstruction(unconditionalGoto);
        conditionalGoto.setOffset(getInstructionCount());
        buildClassInitializerFlow(psiClass, false);
        unconditionalGoto.setOffset(getInstructionCount());
      } else {
        myCodeFragment.accept(this);
      }
    }
    catch (CannotAnalyzeException e) {
      return null;
    }

    myCurrentFlow.finish();
    return myCurrentFlow;
  }

  DfaValueFactory getFactory() {
    return myFactory;
  }

  private void removeVariable(@Nullable PsiVariable variable) {
    if (variable == null) return;
    myCurrentFlow.addInstruction(new FlushVariableInstruction(PlainDescriptor.createVariableValue(getFactory(), variable)));
  }

  void addInstruction(Instruction i) {
    myCurrentFlow.addInstruction(i);
  }

  private static Instruction createSwitchLabelMatchInstruction(@Nullable PsiType comparedType, PsiExpression label) {
    JavaSwitchLabelTakenAnchor anchor = new JavaSwitchLabelTakenAnchor(label);
    if (comparedType != null && isFloatingPoint(comparedType) && comparedType.equals(label.getType())) {
      return new SimpleEqWithoutCorrectionInstruction(anchor);
    }
    return new BooleanBinaryInstruction(RelationType.EQ, true, anchor);
  }

  private static boolean isFloatingPoint(@Nullable PsiType type) {
    return PsiTypes.floatType().equals(type) || PsiTypes.doubleType().equals(type);
  }

  int getInstructionCount() {
    return myCurrentFlow.getInstructionCount();
  }

  private ControlFlowOffset getEndOffset(PsiElement element) {
    assert !(element instanceof PsiExpression) || element instanceof PsiSwitchExpression;
    return myCurrentFlow.getEndOffset(element);
  }

  private ControlFlowOffset getStartOffset(PsiElement element) {
    assert !(element instanceof PsiExpression) || element instanceof PsiSwitchExpression;
    return myCurrentFlow.getStartOffset(element);
  }

  private void startElement(PsiElement element) {
    myCurrentFlow.startElement(element);
  }

  private void finishElement(PsiElement element) {
    myCurrentFlow.finishElement(element);
    if (element instanceof PsiField || (element instanceof PsiStatement && !(element instanceof PsiReturnStatement) &&
        !(element instanceof PsiSwitchLabeledRuleStatement))) {
      int startOffset = myCurrentFlow.getStartOffset(element).getInstructionOffset();
      List<VariableDescriptor> synthetics = myFactory.getTempVariableDescriptorsFrom(startOffset);
      FinishElementInstruction instruction = new FinishElementInstruction(element);
      instruction.flushVars(synthetics);
      addInstruction(instruction);
    }
  }

  @Override
  public void visitErrorElement(PsiErrorElement element) {
    throw new CannotAnalyzeException();
  }

  @Override public void visitAssignmentExpression(PsiAssignmentExpression expression) {
    PsiExpression rExpr = expression.getRExpression();

    startElement(expression);
    if (rExpr == null) {
      pushUnknown();
      finishElement(expression);
      return;
    }

    IElementType op = expression.getOperationTokenType();
    PsiType type = expression.getType();
    PsiExpression lExpr = expression.getLExpression();
    PsiArrayAccessExpression arrayStore = ObjectUtil.tryCast(lExpr, PsiArrayAccessExpression.class);
    if (arrayStore != null) {
      pushArrayAndIndex(arrayStore);
    } else {
      lExpr.accept(this);
    }
    if (op == JavaTokenType.EQ) {
      rExpr.accept(this);
      generateBoxingUnboxingInstructionFor(rExpr, type);
    }
    else {
      if (arrayStore != null) {
        // duplicate array and index on the stack
        addInstruction(new SpliceInstruction(2, 1, 0, 1, 0));
        readArrayElement(arrayStore);
      } else {
        addInstruction(new DupInstruction());
      }
      IElementType sign = TypeConversionUtil.convertEQtoOperation(op);
      PsiType resType = TypeConversionUtil.calcTypeForBinaryExpression(lExpr.getType(), rExpr.getType(), sign, true);
      generateBoxingUnboxingInstructionFor(lExpr, resType);
      rExpr.accept(this);
      generateBoxingUnboxingInstructionFor(rExpr, resType);
      generateBinOp(new JavaExpressionAnchor(expression), sign, rExpr, resType);
      generateBoxingUnboxingInstructionFor(rExpr, resType, type, false);
    }

    if (arrayStore != null) {
      DfaControlTransferValue transfer = createTransfer("java.lang.ArrayIndexOutOfBoundsException");
      VariableDescriptor staticDescriptor = ArrayElementDescriptor.fromArrayAccess(arrayStore);
      addInstruction(new JavaArrayStoreInstruction(arrayStore, rExpr, transfer, staticDescriptor));
    } else {
      addInstruction(new AssignInstruction(rExpr, JavaDfaValueFactory.getExpressionDfaValue(myFactory, lExpr)));
    }
    addNullCheck(expression);

    finishElement(expression);
  }

  @Override public void visitAssertStatement(PsiAssertStatement statement) {
    startElement(statement);
    addInstruction(new PushInstruction(AssertionDisabledDescriptor.createAssertionsDisabledVar(myFactory), null));
    ConditionalGotoInstruction jump = new ConditionalGotoInstruction(null, DfTypes.TRUE);
    addInstruction(jump);
    final PsiExpression condition = statement.getAssertCondition();
    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiTypes.booleanType());
      addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), DfTypes.TRUE, condition));
      final PsiExpression description = statement.getAssertDescription();
      if (description != null) {
        description.accept(this);
      }

      addInstruction(new ThrowInstruction(myTrapTracker.transferValue(JAVA_LANG_ASSERTION_ERROR), statement));
    }
    jump.setOffset(getInstructionCount());
    finishElement(statement);
  }

  @Override public void visitDeclarationStatement(PsiDeclarationStatement statement) {
    startElement(statement);
    int startSize = getInstructionCount();

    PsiElement[] elements = statement.getDeclaredElements();
    for (PsiElement element : elements) {
      if (element instanceof PsiClass) {
        handleClosure(element);
      }
      else if (element instanceof PsiVariable variable) {
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
          initializeVariable(variable, initializer);
        }
      }
    }
    if (getInstructionCount() == startSize) {
      // Add no-op instruction if the statement has no instructions at all,
      // so it could be anchored in debugger.
      addInstruction(new SpliceInstruction(0));
    }

    finishElement(statement);
  }

  @Override
  public void visitField(PsiField field) {
    PsiExpression initializer = field.getInitializer();
    if (initializer != null) {
      startElement(field);
      initializeVariable(field, initializer);
      finishElement(field);
    }
    else if (!field.hasModifierProperty(PsiModifier.FINAL) && !UnusedSymbolUtil.isImplicitWrite(field)) {
      // initialize with default value
      DfaVariableValue dfaVariable = PlainDescriptor.createVariableValue(myFactory, field);
      new CFGBuilder(this).assignAndPop(dfaVariable, DfTypes.defaultValue(field.getType()));
    }
  }

  @Override
  public void visitClassInitializer(PsiClassInitializer initializer) {
    visitCodeBlock(initializer.getBody());
  }

  private void initializeVariable(PsiVariable variable, PsiExpression initializer) {
    if (JavaDfaValueFactory.ignoreInitializer(variable)) return;
    DfaVariableValue dfaVariable = PlainDescriptor.createVariableValue(myFactory, variable);
    addInstruction(new JvmPushForWriteInstruction(dfaVariable));
    initializer.accept(this);
    generateBoxingUnboxingInstructionFor(initializer, variable.getType());
    addInstruction(new AssignInstruction(initializer, dfaVariable));
    addInstruction(new PopInstruction());
  }

  @Override
  public void visitCodeFragment(JavaCodeFragment codeFragment) {
    startElement(codeFragment);
    if (codeFragment instanceof PsiExpressionCodeFragment expressionCodeFragment) {
      PsiExpression expression = expressionCodeFragment.getExpression();
      if (expression != null) {
        expression.accept(this);
      }
    }
    finishElement(codeFragment);
  }

  @Override public void visitCodeBlock(PsiCodeBlock block) {
    startElement(block);

    PsiStatement[] blockStatements = block.getStatements();
    for (PsiStatement statement : blockStatements) {
      statement.accept(this);
    }

    flushCodeBlockVariables(blockStatements, block.getParent());

    finishElement(block);
  }

  private void flushCodeBlockVariables(PsiStatement [] statements, @Nullable PsiElement parent) {
    for (PsiStatement statement : statements) {
      if (statement instanceof PsiDeclarationStatement declarationStatement) {
        for (PsiElement declaration : declarationStatement.getDeclaredElements()) {
          if (declaration instanceof PsiVariable variable) {
            removeVariable(variable);
          }
        }
      }
    }
    if (parent instanceof PsiCatchSection catchSection) {
      removeVariable(catchSection.getParameter());
    }
    else if (parent instanceof PsiForeachStatement forEach) {
      removeVariable(forEach.getIterationParameter());
    }
    else if (parent instanceof PsiForStatement forStatement) {
      PsiStatement statement = forStatement.getInitialization();
      if (statement instanceof PsiDeclarationStatement declarationStatement) {
        for (PsiElement declaration : declarationStatement.getDeclaredElements()) {
          if (declaration instanceof PsiVariable variable) {
            removeVariable(variable);
          }
        }
      }
    }
    else if (parent instanceof PsiTryStatement tryStatement) {
      PsiResourceList list = tryStatement.getResourceList();
      if (list != null) {
        for (PsiResourceListElement resource : list) {
          if (resource instanceof PsiResourceVariable variable) {
            removeVariable(variable);
          }
        }
      }
    }
  }

  @Override public void visitBlockStatement(PsiBlockStatement statement) {
    startElement(statement);
    statement.getCodeBlock().accept(this);
    finishElement(statement);
  }

  @Override public void visitBreakStatement(PsiBreakStatement statement) {
    startElement(statement);
    jumpOut(statement.findExitedStatement());
    finishElement(statement);
  }

  @Override
  public void visitYieldStatement(PsiYieldStatement statement) {
    startElement(statement);
    PsiSwitchExpression enclosing = statement.findEnclosingExpression();
    PsiExpression expression = statement.getExpression();
    if (enclosing != null && myExpressionBlockContext != null && myExpressionBlockContext.myCodeBlock == enclosing.getBody()) {
      myExpressionBlockContext.generateReturn(expression, this);
    } else {
      // yield in incorrect location or only part of switch is analyzed
      if (expression != null) {
        expression.accept(this);
        addInstruction(new PopInstruction());
      }
      jumpOut(enclosing);
    }
    finishElement(statement);
  }

  void addNullCheck(PsiExpression expression) {
    addNullCheck(NullabilityProblemKind.fromContext(expression, myCustomNullabilityProblems));
  }

  void addNullCheck(NullabilityProblemKind.@Nullable NullabilityProblem<?> problem) {
    if (problem != null) {
      DfaControlTransferValue transfer =
        problem.thrownException() != null ? myTrapTracker.maybeTransferValue(problem.thrownException()) : null;
      addInstruction(new CheckNotNullInstruction(problem, transfer));
    }
  }

  private void jumpOut(PsiElement exitedStatement) {
    if (exitedStatement != null && PsiTreeUtil.isAncestor(myCodeFragment, exitedStatement, false)) {
      controlTransfer(createTransfer(exitedStatement, exitedStatement),
                      myTrapTracker.getTrapsInsideElement(exitedStatement));
    } else {
      // Jumping out of analyzed code fragment
      controlTransfer(DfaControlTransferValue.RETURN_TRANSFER, myTrapTracker.getTrapsInsideElement(myCodeFragment));
    }
  }

  private void controlTransfer(DfaControlTransferValue.TransferTarget target, FList<Trap> traps) {
    addInstruction(new ControlTransferInstruction(myFactory.controlTransfer(target, traps)));
  }

  @Override public void visitContinueStatement(PsiContinueStatement statement) {
    startElement(statement);
    PsiStatement continuedStatement = statement.findContinuedStatement();
    if (continuedStatement instanceof PsiLoopStatement loopStatement && PsiTreeUtil.isAncestor(myCodeFragment, continuedStatement, true)) {
      PsiStatement body = loopStatement.getBody();
      controlTransfer(createTransfer(body, body), myTrapTracker.getTrapsInsideElement(body));
    }
    else {
      // Jumping out of analyzed code fragment
      controlTransfer(DfaControlTransferValue.RETURN_TRANSFER, myTrapTracker.getTrapsInsideElement(myCodeFragment));
    }
    finishElement(statement);
  }

  @Override public void visitDoWhileStatement(PsiDoWhileStatement statement) {
    startElement(statement);

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
      PsiExpression condition = statement.getCondition();
      if (condition != null) {
        condition.accept(this);
        generateBoxingUnboxingInstructionFor(condition, PsiTypes.booleanType());
        if (statement == myCodeFragment) {
          addInstruction(new PopInstruction());
        } else {
          addInstruction(new ConditionalGotoInstruction(getStartOffset(statement), DfTypes.TRUE, condition));
        }
      }
    }

    finishElement(statement);
  }

  @Override public void visitEmptyStatement(PsiEmptyStatement statement) {
    startElement(statement);
    finishElement(statement);
  }

  @Override public void visitExpressionStatement(PsiExpressionStatement statement) {
    startElement(statement);
    final PsiExpression expr = statement.getExpression();
    expr.accept(this);
    addInstruction(new PopInstruction());
    finishElement(statement);
  }

  @Override public void visitExpressionListStatement(PsiExpressionListStatement statement) {
    startElement(statement);
    PsiExpression[] expressions = statement.getExpressionList().getExpressions();
    for (PsiExpression expr : expressions) {
      expr.accept(this);
      addInstruction(new PopInstruction());
    }
    finishElement(statement);
  }

  private DfaValue getIteratedElement(PsiType type, PsiExpression iteratedValue) {
    PsiExpression[] expressions = null;
    if (iteratedValue instanceof PsiNewExpression newExpression) {
      PsiArrayInitializerExpression initializer = newExpression.getArrayInitializer();
      if (initializer != null) {
        expressions = initializer.getInitializers();
      }
    }
    else if (iteratedValue instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiVariable arrayVar) {
      expressions = ExpressionUtils.getConstantArrayElements(arrayVar);
    }
    if (iteratedValue instanceof PsiMethodCallExpression call && LIST_INITIALIZER.test(call)) {
      expressions = call.getArgumentList().getExpressions();
    }
    return expressions == null ? getFactory().getUnknown() : JavaDfaValueFactory.createCommonValue(getFactory(), expressions, type);
  }

  @Override
  public void visitForeachPatternStatement(PsiForeachPatternStatement statement) {
    processForeach(statement);
  }

  @Override 
  public void visitForeachStatement(PsiForeachStatement statement) {
    processForeach(statement);
  }
  
  private void processForeach(PsiForeachStatementBase statement) {
    startElement(statement);
    final PsiExpression iteratedValue = PsiUtil.skipParenthesizedExprDown(statement.getIteratedValue());

    ControlFlowOffset loopEndOffset = getEndOffset(statement);
    boolean hasSizeCheck = false;

    if (iteratedValue != null) {
      iteratedValue.accept(this);

      PsiType type = iteratedValue.getType();
      SpecialField length = null;
      if (type instanceof PsiArrayType) {
        length = SpecialField.ARRAY_LENGTH;
      }
      else if (InheritanceUtil.isInheritor(type, JAVA_UTIL_COLLECTION)) {
        length = SpecialField.COLLECTION_SIZE;
      }
      if (length != null) {
        addInstruction(new GetQualifiedValueInstruction(length));
        addInstruction(new ConditionalGotoInstruction(loopEndOffset, DfTypes.intValue(0)));
        hasSizeCheck = true;
      } else {
        addInstruction(new PopInstruction());
      }
    }

    ControlFlowOffset offset = myCurrentFlow.getNextOffset();
    if (statement instanceof PsiForeachStatement normalForeach) {
      PsiParameter parameter = normalForeach.getIterationParameter();
      DfaVariableValue dfaVariable = PlainDescriptor.createVariableValue(myFactory, parameter);
      DfaValue commonValue = getIteratedElement(parameter.getType(), iteratedValue);
      if (DfaTypeValue.isUnknown(commonValue)) {
        addInstruction(new FlushVariableInstruction(dfaVariable));
      } else {
        new CFGBuilder(this).pushForWrite(dfaVariable).push(commonValue).assign().pop();
      }
    }
    else if (statement instanceof PsiForeachPatternStatement patternForeach &&
             patternForeach.getIterationPattern() instanceof PsiDeconstructionPattern pattern) {
      PsiType contextType = JavaPsiPatternUtil.getContextType(pattern);
      if (contextType == null) {
        contextType = TypeUtils.getObjectType(statement);
      }
      DfaValue commonValue = getIteratedElement(contextType, iteratedValue);
      addInstruction(new PushInstruction(commonValue, null));
      DeferredOffset endPattern = new DeferredOffset();
      processPattern(pattern, pattern, contextType,null, endPattern);
      endPattern.setOffset(getInstructionCount());
      addInstruction(new EnsureInstruction(null, RelationType.EQ, DfTypes.TRUE, null));
      addInstruction(new PopInstruction());
    }

    if (!hasSizeCheck) {
      pushUnknown();
      addInstruction(new ConditionalGotoInstruction(loopEndOffset, DfType.TOP));
    }

    final PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    if (hasSizeCheck) {
      pushUnknown();
      addInstruction(new ConditionalGotoInstruction(loopEndOffset, DfType.TOP));
    }

    addInstruction(new GotoInstruction(offset));

    finishElement(statement);
  }

  @Override public void visitForStatement(PsiForStatement statement) {
    startElement(statement);
    final ArrayList<PsiElement> declaredVariables = new ArrayList<>();

    PsiStatement initialization = statement.getInitialization();
    if (initialization != null) {
      initialization.accept(this);
      initialization.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          visitElement(expression);
        }

        @Override
        public void visitDeclarationStatement(PsiDeclarationStatement statement) {
          PsiElement[] declaredElements = statement.getDeclaredElements();
          for (PsiElement element : declaredElements) {
            if (element instanceof PsiVariable) {
              declaredVariables.add(element);
            }
          }
        }
      });
    }

    PsiExpression condition = statement.getCondition();
    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiTypes.booleanType());
    }
    else {
      addInstruction(new PushValueInstruction(statement.getRParenth() == null ? DfTypes.BOOLEAN : DfTypes.TRUE));
    }
    addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), DfTypes.FALSE, condition));

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    ControlFlowOffset offset = initialization != null ? getEndOffset(initialization) : getStartOffset(statement);

    if (!addCountingLoopBound(statement, offset)) {
      PsiStatement update = statement.getUpdate();
      if (update != null) {
        update.accept(this);
      }
      addInstruction(new GotoInstruction(offset));
    }

    finishElement(statement);

    for (PsiElement declaredVariable : declaredVariables) {
      PsiVariable psiVariable = (PsiVariable)declaredVariable;
      removeVariable(psiVariable);
    }
  }

  private static @Nullable Long asLong(PsiExpression expression) {
    Object value = ExpressionUtils.computeConstantExpression(expression);
    if(value instanceof Integer || value instanceof Long) {
      return ((Number)value).longValue();
    }
    return null;
  }

  /**
   * Add known-to-be-true condition inside counting loop, effectively converting
   * {@code for(int i=origin; i<bound; i++)} to
   * {@code int i = origin; while(i < bound) {... i++; if(i <= origin) break;}}.
   * This adds a range knowledge to data flow analysis.
   * <p>
   * Does nothing if the statement is not a counting loop.
   *
   * @param statement counting loop candidate.
   * @param startOffset loop start offset (jump target for back-branch)
   */
  private boolean addCountingLoopBound(PsiForStatement statement, ControlFlowOffset startOffset) {
    CountingLoop loop = CountingLoop.from(statement);
    if (loop == null || loop.isDescending()) return false;
    PsiLocalVariable counter = loop.getCounter();
    PsiType counterType = counter.getType();
    Long end = asLong(loop.getBound());
    if (loop.isIncluding() && !(PsiTypes.longType().equals(counterType) && PsiTypes.intType().equals(loop.getBound().getType()))) {
      // could be for(int i=0; i<=Integer.MAX_VALUE; i++) which will overflow: conservatively skip this
      if (end == null || end == Long.MAX_VALUE || end == Integer.MAX_VALUE) return false;
    }
    PsiExpression initializer = loop.getInitializer();
    if (!PsiTypes.intType().equals(counterType) && !PsiTypes.longType().equals(counterType)) return false;
    DfaValue origin = null;
    Object initialValue = ExpressionUtils.computeConstantExpression(initializer);
    if (initialValue instanceof Number) {
      origin = myFactory.fromDfType(DfTypes.constant(initialValue, counterType));
    }
    else if (initializer instanceof PsiReferenceExpression ref) {
      PsiVariable initialVariable = ObjectUtil.tryCast(ref.resolve(), PsiVariable.class);
      if (PsiUtil.isJvmLocalVariable(initialVariable)
          && !VariableAccessUtils.variableIsAssigned(initialVariable, statement.getBody())) {
        origin = PlainDescriptor.createVariableValue(myFactory, initialVariable);
      }
    }
    if (origin == null) return false;
    Long start = asLong(initializer);
    long diff = start == null || end == null ? -1 : end - start;
    DfaVariableValue loopVar = PlainDescriptor.createVariableValue(myFactory, counter);
    if(diff >= 0 && diff <= MAX_UNROLL_SIZE) {
      // Unroll small loops
      Objects.requireNonNull(statement.getUpdate()).accept(this);
      // It's possible that a nested loop may cause widening, dismissing our efforts to track the loop variable
      // In this case, we should at least ensure that it's not lower than the start. 
      addInstruction(new PushInstruction(loopVar, null));
      DfType startBound = PsiTypes.intType().equals(counterType) ? DfTypes.intValue(Math.toIntExact(start)) : DfTypes.longValue(start);
      addInstruction(new EnsureInstruction(null, RelationType.GE, startBound, null));
      addInstruction(new PopInstruction());
      addInstruction(new GotoInstruction(startOffset, false));
      return true;
    }
    else if (start != null) {
      long maxValue = end == null ? Long.MAX_VALUE : loop.isIncluding() ? end + 1 : end;
      if (start >= maxValue && !loop.mayOverflow()) {
        addInstruction(new GotoInstruction(getEndOffset(statement)));
      }
      else {
        LongRangeSet rangeSet = start >= maxValue
                                ? LongRangeSet.all().subtract(LongRangeSet.range(maxValue + 1, start))
                                : LongRangeSet.range(start + 1L, maxValue);
        DfType range = DfTypes.rangeClamped(rangeSet, JvmPsiRangeSetUtil.getLongRangeType(counterType));
        new CFGBuilder(this).assignAndPop(loopVar, range);
      }
    } else {
      // loop like for(int i = start; i != end; i++)
      if (loop.mayOverflow()) return false;
      new CFGBuilder(this).assign(loopVar, DfType.TOP)
                          .push(origin)
                          .compare(RelationType.LE);
      addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), DfTypes.TRUE, null));
    }
    addInstruction(new GotoInstruction(startOffset));
    return true;
  }

  @Override public void visitIfStatement(PsiIfStatement statement) {
    startElement(statement);

    PsiExpression condition = statement.getCondition();

    PsiStatement thenStatement = statement.getThenBranch();
    PsiStatement elseStatement = statement.getElseBranch();

    DeferredOffset skipThenOffset = new DeferredOffset();

    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiTypes.booleanType());
      addInstruction(new ConditionalGotoInstruction(skipThenOffset, DfTypes.FALSE, condition));
    }

    if (thenStatement != null) {
      addInstruction(new FinishElementInstruction(null));
      thenStatement.accept(this);
    }

    if (elseStatement != null) {
      DeferredOffset skipElseOffset = new DeferredOffset();
      Instruction instruction = new GotoInstruction(skipElseOffset);
      addInstruction(instruction);
      skipThenOffset.setOffset(getInstructionCount());
      addInstruction(new FinishElementInstruction(null));
      elseStatement.accept(this);
      skipElseOffset.setOffset(getInstructionCount());
    } else {
      skipThenOffset.setOffset(getInstructionCount());
    }

    finishElement(statement);
  }

  // in case of JspTemplateStatement
  @Override public void visitStatement(PsiStatement statement) {
    startElement(statement);
    finishElement(statement);
  }

  @Override public void visitLabeledStatement(PsiLabeledStatement statement) {
    startElement(statement);
    PsiStatement childStatement = statement.getStatement();
    if (childStatement != null) {
      childStatement.accept(this);
    }
    finishElement(statement);
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    startElement(expression);
    DfaValue dfaValue = JavaDfaValueFactory.getExpressionDfaValue(myFactory, expression);
    push(dfaValue == null ? myFactory.getUnknown() : dfaValue, expression);
    handleClosure(expression);
    finishElement(expression);
  }

  private void handleClosure(PsiElement closure) {
    Set<VariableDescriptor> descriptors = new HashSet<>();
    closure.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement target = expression.resolve();
        VariableDescriptor descriptor = JavaDfaValueFactory.getAccessedVariableOrGetter(target);
        if (descriptor != null) {
          descriptors.add(descriptor);
        }
        if (JavaDfaValueFactory.getQualifierOrThisValue(myFactory, expression) instanceof DfaVariableValue dfaVar) {
          descriptors.add(dfaVar.getDescriptor());
        }
      }

      @Override
      public void visitThisExpression(PsiThisExpression expression) {
        super.visitThisExpression(expression);
        DfaValue value = JavaDfaValueFactory.getExpressionDfaValue(getFactory(), expression);
        if (value instanceof DfaVariableValue dfaVar) {
          descriptors.add(dfaVar.getDescriptor());
        }
      }
    });
    if (!descriptors.isEmpty()) {
      addInstruction(new EscapeInstruction(List.copyOf(descriptors)));
    }
    List<PsiElement> closures;
    if (closure instanceof PsiClass psiClass) {
      closures = getClosures(psiClass);
    }
    else if (closure instanceof PsiLambdaExpression lambda) {
      closures = ContainerUtil.createMaybeSingletonList(lambda.getBody());
    }
    else {
      closures = List.of();
    }
    if (!closures.isEmpty()) {
      addInstruction(new ClosureInstruction(closures));
    }
  }

  @Override public void visitReturnStatement(PsiReturnStatement statement) {
    startElement(statement);

    PsiExpression returnValue = statement.getReturnValue();

    if (myExpressionBlockContext != null) {
      // We treat return inside switch expression (which is disallowed syntax) as yield
      myExpressionBlockContext.generateReturn(returnValue, this);
    } else {

      if (returnValue != null) {
        returnValue.accept(this);
        PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiMember.class, PsiLambdaExpression.class);
        if (method != null) {
          generateBoxingUnboxingInstructionFor(returnValue, method.getReturnType());
        }
        else {
          final PsiLambdaExpression lambdaExpression =
            PsiTreeUtil.getParentOfType(statement, PsiLambdaExpression.class, true, PsiMember.class);
          if (lambdaExpression != null) {
            generateBoxingUnboxingInstructionFor(returnValue, LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression));
          }
        }
        if (InheritanceUtil.isInheritor(returnValue.getType(), JAVA_UTIL_STREAM_BASE_STREAM)) {
          addConsumedStreamCheckInstructions(returnValue, null);
        }
        addInstruction(new PopInstruction());
      }

      addInstruction(new ReturnInstruction(myFactory, myTrapTracker.trapStack(), statement));
    }
    finishElement(statement);
  }

  private void addConsumedStreamCheckInstructions(@Nullable PsiExpression reference, @Nullable String exception) {
    if (reference == null) {
      return;
    }
    DfaControlTransferValue transferValue = exception != null ? createTransfer(exception) : null;
    addInstruction(new DupInstruction());
    addInstruction(new GetQualifiedValueInstruction(SpecialField.CONSUMED_STREAM));
    addInstruction(new EnsureInstruction(new ConsumedStreamProblem(reference), RelationType.NE, DfStreamStateType.CONSUMED, transferValue));
    addInstruction(new PopInstruction());
  }

  @Override
  public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    startElement(statement);
    PsiCaseLabelElementList labelElementList = statement.getCaseLabelElementList();
    if (labelElementList != null) {
      for (PsiCaseLabelElement element : labelElementList.getElements()) {
        if (element instanceof PsiDefaultCaseLabelElement) {
          element.accept(this);
        }
      }
    }
    finishElement(statement);
  }

  @Override
  public void visitDefaultCaseLabelElement(PsiDefaultCaseLabelElement element) {
    startElement(element);
    finishElement(element);
  }

  @Override
  public void visitSwitchLabeledRuleStatement(PsiSwitchLabeledRuleStatement statement) {
    PsiSwitchBlock switchBlock = statement.getEnclosingSwitchBlock();
    if (switchBlock == null) return;
    startElement(statement);
    PsiCaseLabelElementList labelElementList = statement.getCaseLabelElementList();
    if (labelElementList != null) {
      for (PsiCaseLabelElement element : labelElementList.getElements()) {
        if (element instanceof PsiDefaultCaseLabelElement) {
          element.accept(this);
        }
      }
    }
    PsiStatement body = statement.getBody();
    PsiCodeBlock switchBody = switchBlock.getBody();
    boolean expressionSwitch = myExpressionBlockContext != null && myExpressionBlockContext.myCodeBlock == switchBody;
    if (expressionSwitch && body instanceof PsiExpressionStatement expressionStatement) {
      myExpressionBlockContext.generateReturn(expressionStatement.getExpression(), this);
    }
    else {
      if (body != null) {
        body.accept(this);
      }
      if (ControlFlowUtils.statementMayCompleteNormally(body)) {
        jumpOut(expressionSwitch ? switchBody : switchBlock);
      }
    }
    finishElement(statement);
  }

  @Override public void visitSwitchStatement(PsiSwitchStatement switchStmt) {
    startElement(switchStmt);
    processSwitch(switchStmt);
    finishElement(switchStmt);
  }

  @Override
  public void visitSwitchExpression(PsiSwitchExpression expression) {
    PsiCodeBlock body = expression.getBody();
    if (body == null) {
      processSwitch(expression);
      pushUnknown();
    } else {
      startElement(expression);
      PsiType expressionType = expression.getType();
      DfaVariableValue resultVariable = createTempVariable(expressionType);
      enterExpressionBlock(body, Nullability.UNKNOWN, resultVariable, expressionType);
      processSwitch(expression);
      exitExpressionBlock();
      push(resultVariable, expression);
      finishElement(expression);
    }
  }

  private void push(DfaValue value, PsiExpression expression) {
    addInstruction(new PushInstruction(value, new JavaExpressionAnchor(expression)));
  }

  private void push(DfType dfType, PsiExpression expression) {
    addInstruction(new PushValueInstruction(dfType, new JavaExpressionAnchor(expression)));
  }

  private void processSwitch(PsiSwitchBlock switchBlock) {
    PsiExpression selector = PsiUtil.skipParenthesizedExprDown(switchBlock.getExpression());
    DfaVariableValue expressionValue = null;
    boolean syntheticVar = true;
    PsiType targetType = null;
    if (selector != null) {
      targetType = selector.getType();
      DfaValue selectorValue = JavaDfaValueFactory.getExpressionDfaValue(myFactory, selector);
      if (selectorValue instanceof DfaVariableValue dfaVar && !dfaVar.isFlushableByCalls()) {
        expressionValue = dfaVar;
        syntheticVar = false;
      }
      selector.accept(this);
      if (syntheticVar) {
        expressionValue = targetType == null ? 
                          myCurrentFlow.createTempVariable(DfType.TOP) : createTempVariable(targetType);
        addInstruction(new SimpleAssignmentInstruction(null, expressionValue));
      }
      addInstruction(new PopInstruction());
    }

    PsiCodeBlock body = switchBlock.getBody();

    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      PsiElement defaultLabel = null;
      for (PsiStatement statement : statements) {
        if (!(statement instanceof PsiSwitchLabelStatementBase psiLabelStatement)) {
          continue;
        }
        if (psiLabelStatement.isDefaultCase()) {
          defaultLabel = psiLabelStatement;
          continue;
        }
        try {
          PsiExpression guard = psiLabelStatement.getGuardExpression();
          ControlFlowOffset offset = getStartOffset(statement);
          ControlFlowOffset targetOffset = guard == null ? offset : new DeferredOffset();
          PsiCaseLabelElementList labelElementList = psiLabelStatement.getCaseLabelElementList();
          if (labelElementList != null) {
            for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
              if (labelElement instanceof PsiDefaultCaseLabelElement) {
                defaultLabel = labelElement;
                continue;
              }
              if (labelElement instanceof PsiExpression expr) {
                boolean enumConstant = expr instanceof PsiReferenceExpression ref &&
                                       ref.resolve() instanceof PsiEnumConstant;
                if (expressionValue != null && (enumConstant || PsiUtil.isConstantExpression(expr))) {
                  if (PsiPrimitiveType.getUnboxedType(targetType) == null) {
                    addInstruction(new PushInstruction(expressionValue, null));
                    expr.accept(this);
                    addInstruction(createSwitchLabelMatchInstruction(targetType, expr));
                    addInstruction(new ConditionalGotoInstruction(targetOffset, DfTypes.TRUE));
                  }
                  else {
                    addInstruction(new PushInstruction(expressionValue, null));
                    DeferredOffset condGotoOffset = new DeferredOffset();
                    addInstruction(new ConditionalGotoInstruction(condGotoOffset, DfTypes.NULL));

                    addInstruction(new PushInstruction(expressionValue, null));
                    generateBoxingUnboxingInstructionFor(selector, PsiPrimitiveType.getUnboxedType(targetType));
                    expr.accept(this);
                    addInstruction(createSwitchLabelMatchInstruction(PsiPrimitiveType.getUnboxedType(targetType), expr));
                    DeferredOffset gotoOffset = new DeferredOffset();
                    addInstruction(new GotoInstruction(gotoOffset));

                    PushValueInstruction pushValInstr = new PushValueInstruction(DfTypes.FALSE, new JavaSwitchLabelTakenAnchor(expr));
                    addInstruction(pushValInstr);
                    condGotoOffset.setOffset(pushValInstr.getIndex());

                    ConditionalGotoInstruction exitFromSwitchBranchInstr = new ConditionalGotoInstruction(targetOffset, DfTypes.TRUE);
                    addInstruction(exitFromSwitchBranchInstr);
                    gotoOffset.setOffset(exitFromSwitchBranchInstr.getIndex());
                  }
                }
                else if (expressionValue != null && ExpressionUtils.isNullLiteral(expr)) {
                  addInstruction(new PushInstruction(expressionValue, null));
                  expr.accept(this);
                  addInstruction(new BooleanBinaryInstruction(RelationType.EQ, true, new JavaSwitchLabelTakenAnchor(expr)));
                  addInstruction(new ConditionalGotoInstruction(targetOffset, DfTypes.TRUE));
                }
                else {
                  pushUnknown();
                  addInstruction(new ConditionalGotoInstruction(targetOffset, DfTypes.TRUE));
                }
              }
              else if (expressionValue != null && targetType != null) {
                if (labelElement instanceof PsiPattern pattern) {
                  processPatternInSwitch(pattern, expressionValue, targetType);
                  addInstruction(new ResultOfInstruction(new JavaSwitchLabelTakenAnchor(pattern)));
                  addInstruction(new ConditionalGotoInstruction(targetOffset, DfTypes.TRUE));
                }
              }
            }
          }
          if (guard != null) {
            DeferredOffset endGuardOffset = new DeferredOffset();
            addInstruction(new GotoInstruction(endGuardOffset));
            ((DeferredOffset)targetOffset).setOffset(getInstructionCount());
            guard.accept(this);
            generateBoxingUnboxingInstructionFor(guard, PsiTypes.booleanType());
            addInstruction(new ResultOfInstruction(new JavaSwitchLabelTakenAnchor(guard)));
            addInstruction(new ConditionalGotoInstruction(offset, DfTypes.TRUE));
            endGuardOffset.setOffset(getInstructionCount());
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      if (defaultLabel != null) {
        addInstruction(new GotoInstruction(getStartOffset(defaultLabel)));
      }
      else if (switchBlock instanceof PsiSwitchExpression) {
        addInstruction(new ThrowInstruction(myTrapTracker.transferValue("java.lang.IncompatibleClassChangeError"), null));
      }
      else {
        addInstruction(new GotoInstruction(getEndOffset(body)));
      }

      body.accept(this);
    }

    if (syntheticVar && expressionValue != null) {
      addInstruction(new FlushVariableInstruction(expressionValue));
    }
  }

  private void processPatternInSwitch(PsiPattern pattern, DfaVariableValue expressionValue, PsiType checkType) {
    DeferredOffset endPatternOffset = new DeferredOffset();
    addInstruction(new PushInstruction(expressionValue, null));
    processPattern(pattern, pattern, checkType, null, endPatternOffset);
    endPatternOffset.setOffset(getInstructionCount());
  }

  private void processPatternInInstanceof(PsiPattern pattern, PsiInstanceOfExpression expression,
                                          PsiType checkType) {
    boolean potentiallyRedundantInstanceOf = pattern instanceof PsiTypeTestPattern ||
                                             pattern instanceof PsiDeconstructionPattern dec &&
                                             JavaPsiPatternUtil.hasUnconditionalComponents(dec);
    DfaAnchor instanceofAnchor = potentiallyRedundantInstanceOf ? new JavaExpressionAnchor(expression) : null;
    DeferredOffset endPatternOffset = new DeferredOffset();
    processPattern(pattern, pattern, checkType, instanceofAnchor, endPatternOffset);
    endPatternOffset.setOffset(getInstructionCount());
    if (!potentiallyRedundantInstanceOf) {
      addInstruction(new ResultOfInstruction(new JavaExpressionAnchor(expression)));
    }
  }

  private void processPattern(PsiPattern sourcePattern, @Nullable PsiPattern innerPattern,
                              PsiType checkType, @Nullable DfaAnchor instanceofAnchor, DeferredOffset endPatternOffset) {
    if (innerPattern == null) return;
    else if (innerPattern instanceof PsiDeconstructionPattern deconstructionPattern) {
      PsiPatternVariable variable = deconstructionPattern.getPatternVariable();
      // The nullability written on a pattern type is ignored, so take it from the type of the matched expression
      PsiType effectiveType = JavaPsiPatternUtil.getEffectivePatternType(deconstructionPattern);
      PsiType patternType = effectiveType != null ? effectiveType : deconstructionPattern.getTypeElement().getType();
      DfaVariableValue patternDfaVar = variable == null ? createTempVariable(patternType) :
                                       PlainDescriptor.createVariableValue(getFactory(), variable);

      addTypeCheckPattern(sourcePattern, innerPattern, checkType, endPatternOffset, patternDfaVar, instanceofAnchor);

      addInstruction(new PopInstruction());

      PsiPattern[] components = deconstructionPattern.getDeconstructionList().getDeconstructionComponents();
      if (patternType instanceof PsiClassType patternClassType) {
        PsiClassType.ClassResolveResult resolveResult = patternClassType.resolveGenerics();
        PsiClass recordClass = resolveResult.getElement();
        boolean unchecked = JavaGenericsUtil.isUncheckedCast(patternClassType, checkType);
        // Capture the wildcards, as a bare wildcard component type (like in 'Rec<?>(Object o)') makes the nested pattern conditional
        PsiSubstitutor substitutor = unchecked ? PsiSubstitutor.EMPTY :
                                     ((PsiClassType)PsiUtil.captureToplevelWildcards(patternClassType, deconstructionPattern))
                                       .resolveGenerics().getSubstitutor();
        if (recordClass != null && recordClass.isRecord()) {
          PsiRecordComponent[] recordComponents = recordClass.getRecordComponents();
          if (components.length == recordComponents.length) {
            for (int i = 0; i < components.length; i++) {
              PsiRecordComponent recordComponent = recordComponents[i];
              PsiPattern patternComponent = components[i];
              PsiMethod accessor = JavaPsiRecordUtil.getAccessorForRecordComponent(recordComponent);
              if (accessor == null) continue;
              PsiField field = PropertyUtil.getFieldOfGetter(accessor);
              VariableDescriptor descriptor = field == null ? new GetterDescriptor(accessor) : new PlainDescriptor(field);
              DfaVariableValue accessorDfaVar = getFactory().getVarFactory().createVariableValue(descriptor, patternDfaVar);
              JavaSwitchDeconstructionLabelAnchor labelAnchor = new JavaSwitchDeconstructionLabelAnchor(patternComponent);
              addInstruction(new PushInstruction(accessorDfaVar, labelAnchor));
              if (JavaPsiSwitchUtil.mayCauseMatchExceptionDuringDeconstruction(deconstructionPattern, recordComponent, patternComponent, Set.of())) {
                addNullCheck(deconstructionMatchException.problem(labelAnchor, patternComponent));
              }
              PsiType componentType = JavaVarTypeUtil.getUpwardProjection(substitutor.substitute(recordComponent.getType()));
              processPattern(sourcePattern, patternComponent, componentType, null, endPatternOffset);
            }
          }
        }
      }
    }
    else if (innerPattern instanceof PsiTypeTestPattern typeTestPattern) {
      PsiPatternVariable variable = typeTestPattern.getPatternVariable();
      if (variable == null) return;
      DfaVariableValue patternDfaVar = PlainDescriptor.createVariableValue(getFactory(), variable);

      addTypeCheckPattern(sourcePattern, innerPattern, checkType, endPatternOffset, patternDfaVar, instanceofAnchor);

      addInstruction(new PopInstruction());
    }

    if (sourcePattern == innerPattern) {
      addInstruction(new PushValueInstruction(DfTypes.TRUE));
    }
  }

  private void addTypeCheckPattern(PsiPattern sourcePattern,
                                   PsiPattern innerPattern,
                                   PsiType checkType,
                                   DeferredOffset endPatternOffset,
                                   DfaVariableValue patternDfaVar,
                                   @Nullable DfaAnchor instanceofAnchor) {
    if (sourcePattern == innerPattern || !JavaPsiPatternUtil.isUnconditionalForType(innerPattern, checkType)) {
      addPatternTypeTest(innerPattern, instanceofAnchor, endPatternOffset, patternDfaVar, checkType);
    }
    else {
      // The assigned value keeps the type of the record accessor, which loses the type arguments of the pattern.
      // The declared type of the pattern variable has them, so narrow the value to it.
      DfType declaredType = patternDfaVar.getInherentType();
      if (!(checkType instanceof PsiPrimitiveType) &&
          declaredType instanceof DfReferenceType &&
          !declaredType.equals(patternDfaVar.getDfType())) {
        addInstruction(new EnsureInstruction(null, RelationType.IS, declaredType, null));
      }
      addInstruction(new SimpleAssignmentInstruction(null, patternDfaVar));
    }
  }

  private void addPatternTypeTest(PsiPattern pattern,
                                  @Nullable DfaAnchor instanceofAnchor,
                                  DeferredOffset endPatternOffset,
                                  DfaVariableValue patternDfaVar,
                                  PsiType checkType) {
    DeferredOffset condGotoOffset;
    addInstruction(new DupInstruction());
    PsiType patternType = JavaPsiPatternUtil.getPatternType(pattern);
    generateInstanceOfInstructions(pattern, instanceofAnchor, checkType, patternType);

    condGotoOffset = new DeferredOffset();
    addInstruction(new ConditionalGotoInstruction(condGotoOffset, DfTypes.TRUE));

    addInstruction(new PopInstruction());
    addInstruction(new PushValueInstruction(DfTypes.FALSE));
    addInstruction(new GotoInstruction(endPatternOffset));
    int nextIndex = getInstructionCount();
    generateBoxingUnboxingInstructionFor(pattern, checkType, patternType, false);
    SimpleAssignmentInstruction assignmentInstr = new SimpleAssignmentInstruction(null, patternDfaVar);
    addInstruction(assignmentInstr);
    condGotoOffset.setOffset(nextIndex);
  }

  private void generateInstanceOfInstructions(PsiElement context,
                         @Nullable DfaAnchor instanceofAnchor,
                         PsiType checkType,
                         @Nullable PsiType patternType) {
    checkType = TypeConversionUtil.erasure(checkType);
    if (patternType == null ||
        ((checkType instanceof PsiPrimitiveType || patternType instanceof PsiPrimitiveType) &&
         (!PsiUtil.isAvailable(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS, context) || !TypeConversionUtil.areTypesConvertible(checkType, patternType))) ||
        isUnresolvedType(checkType) || isUnresolvedType(patternType)) {
      addInstruction(new PopInstruction());
      pushUnknown();
      return;
    }
    if (checkType instanceof PsiPrimitiveType && patternType instanceof PsiPrimitiveType) {
      if (checkType.equals(patternType) || JavaPsiPatternUtil.isExactPrimitiveWideningConversion(checkType, patternType)) {
        addInstruction(new PopInstruction());
        addInstruction(new PushValueInstruction(DfTypes.booleanValue(true)));
        if (instanceofAnchor != null) {
          addInstruction(new ResultOfInstruction(instanceofAnchor));
        }
      }
      else {
        generateExactTestingConversion(context, checkType, patternType, instanceofAnchor);
      }
    }
    else if (patternType instanceof PsiPrimitiveType patternPrimitiveType) {
      PsiPrimitiveType unboxedCheckedType = PsiPrimitiveType.getUnboxedType(checkType);
      if (unboxedCheckedType != null) {
        boolean isWideningConversion = JavaPsiPatternUtil.isExactPrimitiveWideningConversion(unboxedCheckedType, patternPrimitiveType) ||
                                       unboxedCheckedType.equals(patternPrimitiveType);
        if (!isWideningConversion) {
          addInstruction(new DupInstruction());
        }
        addInstruction(new PushValueInstruction(DfTypes.NULL));
        addInstruction(new BooleanBinaryInstruction(RelationType.NE, false,
                                                    isWideningConversion ? instanceofAnchor : null));
        if (!isWideningConversion) {
          CFGBuilder builder = new CFGBuilder(this) //checkValue resultNotNull
            .push(DfTypes.booleanValue(false)) //checkValue resultNotNull false
            .ifCondition(RelationType.EQ) //checkValue
            .pop() //
            .push(DfTypes.booleanValue(false)) //false
            .elseBranch(); //checkValue
          generateBoxingUnboxingInstructionFor(context, checkType, unboxedCheckedType, false);
          generateInstanceOfInstructions(context, null, unboxedCheckedType, patternPrimitiveType);
          builder
            .end();
          if (instanceofAnchor != null) {
            addInstruction(new ResultOfInstruction(instanceofAnchor));
          }
        }
      }
      else {
        PsiClassType boxedType = patternPrimitiveType.getBoxedType(context);
        addInstruction(new PushValueInstruction(DfTypes.typedObject(boxedType, Nullability.NOT_NULL)));
        addInstruction(new InstanceofInstruction(instanceofAnchor, false));
      }
    }
    else {
      if (checkType instanceof PsiPrimitiveType checkPrimitiveType) {
        PsiClassType boxedType = checkPrimitiveType.getBoxedType(context);
        generateBoxingUnboxingInstructionFor(context, checkType, boxedType, false);
      }
      addInstruction(new PushValueInstruction(DfTypes.typedObject(patternType, Nullability.NOT_NULL)));
      addInstruction(new InstanceofInstruction(instanceofAnchor, false));
    }
  }

  private static boolean isUnresolvedType(PsiType type) {
    return type.getDeepComponentType() instanceof PsiClassType ct && ct.resolve() == null;
  }

  private void generateExactTestingConversion(PsiElement context,
                                              PsiType checkType,
                                              PsiType patternType,
                                              @Nullable DfaAnchor instanceofAnchor) {
    PsiType exactlyPromotedType = JavaPsiPatternUtil.getExactlyPromotedType(context, checkType, patternType);
    boolean checkTypeIsDouble = checkType.equals(PsiTypes.doubleType());
    boolean checkTypeIsFloat = checkType.equals(PsiTypes.floatType());
    boolean checkTypeIsLong = checkType.equals(PsiTypes.longType());
    if (exactlyPromotedType.equalsToText("java.math.BigDecimal")) {
      //there is no need to use BigDecimal explicitly, because
      //there is some further simplification (see java.lang.runtime.ExactConversionsSupport)
      if (checkTypeIsLong) {
        exactlyPromotedType = PsiTypes.longType();
      }
      else if (checkTypeIsFloat) {
        exactlyPromotedType = PsiTypes.floatType();
      }
      else {
        exactlyPromotedType = PsiTypes.doubleType();
      }
    }
    CFGBuilder builder = new CFGBuilder(this);
    boolean generateOr = false;
    boolean generateAnd = false;
    if (checkTypeIsFloat || checkTypeIsDouble) {
      if (patternType.equals(PsiTypes.floatType()) ||
          patternType.equals(PsiTypes.doubleType())) {
        generateOr = true;
        builder //stack: checkValue
          .dup() //checkValue checkValue
          .dup() //checkValue checkValue checkValue
          .compare(RelationType.EQ); //checkValue isNotNan
        addInstruction(new NotInstruction(null)); //checkValue isNan
        builder.swap(); //isNan checkValue
      }
      else {
        generateAnd = true;
        PsiClassType boxedType = checkTypeIsFloat
                                 ? PsiTypes.floatType().getBoxedType(context)
                                 : PsiTypes.doubleType().getBoxedType(context);
        builder //stack: checkValue
          .dup(); // checkValue checkValue
        generateBoxingUnboxingInstructionFor(context, checkType, boxedType, false); //checkValue ReferenceOfCheckValue
        builder.push(checkTypeIsFloat
                     ? DfTypes.floatValue(-0.0f)
                     : DfTypes.doubleValue(-0.0f)) ;// checkValue ReferenceOfCheckValue -0.0
        generateBoxingUnboxingInstructionFor(context, PsiTypes.doubleType(), boxedType, false); //checkValue ReferenceOfCheckValue ReferenceNegativeZero
        addInstruction(new BooleanBinaryInstruction(RelationType.NE, true, null)); //checkValue IsNotMinusZero
        builder.swap(); //IsNotMinusZero checkValue
        if (patternType.equals(PsiTypes.longType())) {
          builder.dup()//IsNotMinusZero checkValue checkValue
            //see com.google.common.math.DoubleMath.MAX_LONG_AS_DOUBLE_PLUS_ONE for explanation
            .push(checkTypeIsFloat
                  ? DfTypes.floatValue(0x1p63F)
                  : DfTypes.doubleValue(0x1p63)) //IsNotMinusZero checkValue checkValue maxLong
            .compare(RelationType.NE) //IsNotMinusZero checkValue notMax
            .splice(3, 1, 0, 2);  //checkValue IsNotMinusZero  notMax
          addInstruction(new BooleanAndOrInstruction(false, null));//checkValue result
          builder.swap(); //result checkValue
        }
      }
    } else if(checkTypeIsLong &&
              (patternType.equals(PsiTypes.floatType()) ||
               patternType.equals(PsiTypes.doubleType()))){
      generateAnd = true;
        builder //checkValue
          .dup() //checkValue checkValue
          .push(DfTypes.longValue(Long.MAX_VALUE)) //checkValue checkValue Max_long
          .compare(RelationType.NE) //checkValue NotMaxLong
          .swap(); //NotMaxLong checkValue
    }
    builder.dup(); //something checkValue checkValue
    generateBoxingUnboxingInstructionFor(context, checkType, exactlyPromotedType, false); //something checkValue casted
    builder.swap(); //something casted checkValue
    generateBoxingUnboxingInstructionFor(context, checkType, patternType, false);
    generateBoxingUnboxingInstructionFor(context, patternType, exactlyPromotedType, false);
    addInstruction(new BooleanBinaryInstruction(RelationType.EQ, false, generateOr || generateAnd ? null : instanceofAnchor));
    if (generateOr) {
      addInstruction(new BooleanAndOrInstruction(true, instanceofAnchor));
    }
    if (generateAnd) {
      addInstruction(new BooleanAndOrInstruction(false, instanceofAnchor));
    }
  }

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    startElement(expression);

    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier != null) {
      qualifier.accept(this);
    }
    else {
      pushUnknown();
    }

    addInstruction(new MethodReferenceInstruction(expression));

    finishElement(expression);
  }

  @Override public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
    startElement(statement);

    PsiExpression lock = statement.getLockExpression();
    if (lock != null) {
      lock.accept(this);
      addInstruction(new PopInstruction());
    }

    addInstruction(new FlushFieldsInstruction());

    PsiCodeBlock body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    finishElement(statement);
  }

  @Override
  public void visitTemplateExpression(PsiTemplateExpression expression) {
    PsiExpression processor = PsiUtil.skipParenthesizedExprDown(expression.getProcessor());
    if (processConcatTemplate(expression)) {
      return;
    }
    if (processor != null) {
      processor.accept(this);
    } else {
      pushUnknown(); // incomplete code
    }
    PsiTemplate template = expression.getTemplate();
    if (template != null) {
      for (PsiExpression embeddedExpression : template.getEmbeddedExpressions()) {
        embeddedExpression.accept(this);
        addInstruction(new PopInstruction());
      }
    }
    addInstruction(new MethodCallInstruction(expression, List.of()));
    if (myTrapTracker.shouldHandleException()) {
      addThrows(ExceptionUtil.getOwnUnhandledExceptions(expression));
    }
    addNullCheck(expression);
  }

  private boolean processConcatTemplate(PsiTemplateExpression expression) {
    PsiExpression processor = PsiUtil.skipParenthesizedExprDown(expression.getProcessor());
    if (!JavaPsiStringTemplateUtil.isStrTemplate(processor)) return false;
    PsiLiteralExpression literalExpression = expression.getLiteralExpression();
    if (literalExpression != null) {
      literalExpression.accept(this);
      return true;
    }
    PsiTemplate template = expression.getTemplate();
    if (template == null) {
      pushUnknown();
      return true;
    }
    PsiType stringType = expression.getType();
    if (stringType == null) return false;
    TypeConstraint constraint = TypeConstraints.exact(stringType); 
    List<PsiExpression> expressions = template.getEmbeddedExpressions();
    List<PsiFragment> fragments = template.getFragments();
    int count = expressions.size();
    if (count + 1 != fragments.size()) return false;
    for (int i = 0; i < count; i++) {
      PsiFragment fragment = fragments.get(i);
      Object value = fragment.getValue();
      if (value != null) {
        addInstruction(new PushValueInstruction(DfTypes.referenceConstant(value, stringType)));
      } else {
        pushUnknown();
      }
      if (i > 0) {
        addInstruction(new StringConcatInstruction(null, constraint));
      }
      PsiExpression embeddedExpression = expressions.get(i);
      if (embeddedExpression instanceof PsiEmptyExpressionImpl) {
        addInstruction(new PushValueInstruction(DfTypes.NULL));
      } else {
        embeddedExpression.accept(this);
      }
      addInstruction(new StringConcatInstruction(null, constraint));
    }
    PsiFragment lastFragment = fragments.get(count);
    Object value = lastFragment.getValue();
    if (value != null) {
      addInstruction(new PushValueInstruction(DfTypes.referenceConstant(value, stringType)));
    } else {
      pushUnknown();
    }
    if (count > 0) {
      addInstruction(new StringConcatInstruction(new JavaExpressionAnchor(expression), constraint));
    }
    return true;
  }

  @Override public void visitThrowStatement(PsiThrowStatement statement) {
    startElement(statement);

    PsiExpression exception = statement.getException();

    if (exception != null) {
      exception.accept(this);

      addInstruction(new PopInstruction());
      throwException(exception.getType(), statement);
    }

    finishElement(statement);
  }

  void addConditionalErrorThrow() {
    if (!myTrapTracker.shouldHandleException()) {
      return;
    }
    DfaControlTransferValue transfer = myTrapTracker.transferValue(JAVA_LANG_ERROR);
    addInstruction(new EnsureInstruction(null, RelationType.EQ, DfType.TOP, transfer));
  }

  @Override
  public void visitTryStatement(PsiTryStatement statement) {
    startElement(statement);

    PsiResourceList resourceList = statement.getResourceList();
    PsiCodeBlock tryBlock = statement.getTryBlock();
    PsiCodeBlock finallyBlock = statement.getFinallyBlock();

    EnterFinallyTrap finallyDescriptor = finallyBlock != null ? new EnterFinallyTrap(finallyBlock, getStartOffset(finallyBlock)) : null;
    if (finallyDescriptor != null) {
      pushTrap(finallyDescriptor);
    }

    PsiCatchSection[] sections = statement.getCatchSections();
    if (sections.length > 0) {
      LinkedHashMap<CatchClauseDescriptor, ControlFlowOffset> clauses = new LinkedHashMap<>();
      for (PsiCatchSection section : sections) {
        PsiCodeBlock catchBlock = section.getCatchBlock();
        if (catchBlock != null) {
          clauses.put(new JavaCatchClauseDescriptor(section), getStartOffset(catchBlock));
        }
      }
      pushTrap(new TryCatchTrap(statement, clauses));
    }

    processTryWithResources(resourceList, tryBlock);

    InstructionTransfer gotoEnd = createTransfer(statement, tryBlock);
    FList<Trap> singleFinally = FList.createFromReversed(ContainerUtil.createMaybeSingletonList(finallyDescriptor));
    controlTransfer(gotoEnd, singleFinally);

    if (sections.length > 0) {
      popTrap(TryCatchTrap.class);
    }

    for (PsiCatchSection section : sections) {
      PsiCodeBlock catchBlock = section.getCatchBlock();
      if (catchBlock != null) {
        visitCodeBlock(catchBlock);
      }
      controlTransfer(gotoEnd, singleFinally);
    }

    if (finallyBlock != null) {
      popTrap(EnterFinallyTrap.class);
      pushTrap(new InsideFinallyTrap(finallyBlock));

      finallyBlock.accept(this);
      controlTransfer(new ExitFinallyTransfer(finallyDescriptor), FList.emptyList());

      popTrap(InsideFinallyTrap.class);
    }

    finishElement(statement);
  }

  private InstructionTransfer createTransfer(PsiElement exitedStatement, PsiElement blockToFlush) {
    List<VariableDescriptor> varsToFlush = ContainerUtil.map(PsiTreeUtil.findChildrenOfType(blockToFlush, PsiVariable.class),
                                                             PlainDescriptor::new);
    return new InstructionTransfer(getEndOffset(exitedStatement), varsToFlush);
  }

  void pushTrap(Trap elem) {
    myTrapTracker.pushTrap(elem);
  }

  void popTrap(Class<? extends Trap> aClass) {
    myTrapTracker.popTrap(aClass);
  }

  private void processTryWithResources(@Nullable PsiResourceList resourceList, @Nullable PsiCodeBlock tryBlock) {
    Set<PsiClassType> closerExceptions = Collections.emptySet();
    TwrFinally twrFinallyDescriptor = null;
    if (resourceList != null) {
      resourceList.accept(this);

      closerExceptions = StreamEx.of(resourceList.iterator()).flatCollection(ExceptionUtil::getCloserExceptions).toSet();
      twrFinallyDescriptor = new TwrFinally(resourceList, getStartOffset(resourceList));
      pushTrap(twrFinallyDescriptor);
    }

    if (tryBlock != null) {
      tryBlock.accept(this);
    }

    if (twrFinallyDescriptor != null) {
      InstructionTransfer gotoEnd = createTransfer(resourceList, tryBlock);
      controlTransfer(gotoEnd, FList.createFromReversed(ContainerUtil.createMaybeSingletonList(twrFinallyDescriptor)));
      popTrap(TwrFinally.class);
      pushTrap(new InsideFinallyTrap(resourceList));
      startElement(resourceList);
      addInstruction(new FlushFieldsInstruction());
      addThrows(closerExceptions);
      controlTransfer(new ExitFinallyTransfer(twrFinallyDescriptor), FList.emptyList()); // DfaControlTransferValue is on stack
      finishElement(resourceList);
      popTrap(InsideFinallyTrap.class);
    }
  }

  @Override
  public void visitResourceList(PsiResourceList resourceList) {
    for (PsiResourceListElement resource : resourceList) {
      if (resource instanceof PsiResourceVariable variable) {
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
          initializeVariable(variable, initializer);
        }
      }
      else if (resource instanceof PsiResourceExpression expression) {
        expression.getExpression().accept(this);
        addInstruction(new PopInstruction());
      }
    }
  }

  @Override public void visitWhileStatement(PsiWhileStatement statement) {
    startElement(statement);

    PsiExpression condition = statement.getCondition();

    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiTypes.booleanType());
    } else {
      pushUnknown();
    }
    addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), DfTypes.FALSE, condition));

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    if (statement != myCodeFragment) {
      addInstruction(new GotoInstruction(getStartOffset(statement)));
    }

    finishElement(statement);
  }

  @Override public void visitExpressionList(PsiExpressionList list) {
    startElement(list);

    PsiExpression[] expressions = list.getExpressions();
    for (PsiExpression expression : expressions) {
      expression.accept(this);
    }

    finishElement(list);
  }

  @Override public void visitExpression(PsiExpression expression) {
    startElement(expression);
    DfaValue dfaValue = JavaDfaValueFactory.getExpressionDfaValue(myFactory, expression);
    push(dfaValue == null ? myFactory.getUnknown() : dfaValue, expression);
    finishElement(expression);
  }

  @Override
  public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
    startElement(expression);
    pushArrayAndIndex(expression);
    readArrayElement(expression);
    addNullCheck(expression);
    finishElement(expression);
  }

  private void pushArrayAndIndex(PsiArrayAccessExpression arrayStore) {
    arrayStore.getArrayExpression().accept(this);
    PsiExpression index = arrayStore.getIndexExpression();
    if (index != null) {
      index.accept(this);
      generateBoxingUnboxingInstructionFor(index, PsiTypes.intType());
    } else {
      pushUnknown();
    }
  }

  private void readArrayElement(PsiArrayAccessExpression expression) {
    DfaControlTransferValue transfer = createTransfer("java.lang.ArrayIndexOutOfBoundsException");
    addInstruction(new ArrayAccessInstruction(new JavaExpressionAnchor(expression), new ArrayIndexProblem(expression), transfer,
                                              ArrayElementDescriptor.fromArrayAccess(expression)));
  }

  private @Nullable DfaVariableValue getTargetVariable(PsiExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (expression instanceof PsiArrayInitializerExpression && parent instanceof PsiNewExpression) {
      parent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
    }
    if (parent instanceof PsiVariable var) {
      // initialization
      return PlainDescriptor.createVariableValue(getFactory(), var);
    }
    if (parent instanceof PsiAssignmentExpression assignmentExpression &&
        assignmentExpression.getOperationTokenType().equals(JavaTokenType.EQ) &&
        PsiTreeUtil.isAncestor(assignmentExpression.getRExpression(), expression, false) &&
        JavaDfaValueFactory.getExpressionDfaValue(getFactory(), assignmentExpression.getLExpression()) instanceof DfaVariableValue var) {
      return var;
    }
    return null;
  }

  @Override
  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    startElement(expression);
    initializeArray(expression, expression);
    finishElement(expression);
  }

  private void initializeArray(PsiArrayInitializerExpression expression, PsiExpression originalExpression) {
    PsiType type = expression.getType();
    PsiType componentType = type instanceof PsiArrayType arrayType ? arrayType.getComponentType() : null;
    DfaVariableValue var = getTargetVariable(expression);
    DfaVariableValue arrayWriteTarget = var;
    if (var == null) {
      var = createTempVariable(type);
    }
    PsiExpression[] initializers = expression.getInitializers();
    if (arrayWriteTarget != null) {
      PsiVariable arrayVariable = ObjectUtil.tryCast(arrayWriteTarget.getPsiVariable(), PsiVariable.class);
      if (arrayWriteTarget.isFlushableByCalls() ||
          arrayVariable == null ||
          VariableAccessUtils.variableIsUsed(arrayVariable, expression) ||
          ExpressionUtils.getConstantArrayElements(arrayVariable) != null ||
          !(ArrayElementDescriptor.getArrayElementValue(getFactory(), arrayWriteTarget, 0) instanceof DfaVariableValue)) {
        arrayWriteTarget = null;
      }
    }
    DfType arrayType = SpecialField.ARRAY_LENGTH.asDfType(DfTypes.intValue(initializers.length))
      .meet(type == null ? DfTypes.OBJECT_OR_NULL : TypeConstraints.exact(type).asDfType())
      .meet(DfTypes.LOCAL_OBJECT);
    if (arrayWriteTarget != null) {
      addInstruction(new JvmPushForWriteInstruction(arrayWriteTarget));
      push(arrayType, expression);
      addInstruction(new AssignInstruction(originalExpression, arrayWriteTarget));
      int index = 0;
      for (PsiExpression initializer : initializers) {
        DfaValue target = null;
        if (index < MAX_ARRAY_INDEX_FOR_INITIALIZER) {
          target = Objects.requireNonNull(ArrayElementDescriptor.getArrayElementValue(getFactory(), arrayWriteTarget, index));
        }
        index++;
        addInstruction(new JvmPushForWriteInstruction(target == null ? myFactory.getUnknown() : target));
        initializer.accept(this);
        if (componentType != null) {
          generateBoxingUnboxingInstructionFor(initializer, componentType);
        }
        addInstruction(new AssignInstruction(initializer, null));
        addInstruction(new PopInstruction());
      }
    }
    else {
      for (PsiExpression initializer : initializers) {
        addInstruction(new JvmPushForWriteInstruction(myFactory.getUnknown()));
        initializer.accept(this);
        if (componentType != null) {
          generateBoxingUnboxingInstructionFor(initializer, componentType);
        }
        // Assign to handle escaping if necessary
        addInstruction(new AssignInstruction(initializer, null));
        addInstruction(new PopInstruction());
      }
      if (DfaValueFactory.isTempVariable(var)) {
        addInstruction(new JvmPushForWriteInstruction(var));
        push(arrayType, expression);
        addInstruction(new AssignInstruction(null, var));
      } else {
        push(arrayType, expression);
      }
    }
  }

  @Override
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
    startElement(expression);

    DfaValue dfaValue = JavaDfaValueFactory.getExpressionDfaValue(myFactory, expression);
    if (dfaValue != null) {
      push(dfaValue, expression);
      finishElement(expression);
      return;
    }

    if (PsiUtilCore.hasErrorElementChild(expression)) {
      pushUnknown();
      finishElement(expression);
      return;
    }

    PsiExpression[] operands = expression.getOperands();
    IElementType op = expression.getOperationTokenType();
    if (op == JavaTokenType.ANDAND) {
      generateShortCircuitAndOr(expression, operands, expression.getType(), true);
    }
    else if (op == JavaTokenType.OROR) {
      generateShortCircuitAndOr(expression, operands, expression.getType(), false);
    }
    else {
      generateBinOpChain(expression, op, operands);
    }
    finishElement(expression);
  }

  private void checkZeroDivisor(PsiType resType) {
    DfaControlTransferValue transfer = createTransfer("java.lang.ArithmeticException");
    addInstruction(new EnsureInstruction(null, RelationType.NE, PsiTypes.longType().equals(resType) ? DfTypes.longValue(0) : DfTypes.intValue(0),
                                         transfer, true));
  }

  private void generateBinOpChain(PsiPolyadicExpression expression, IElementType op, PsiExpression[] operands) {
    PsiExpression lExpr = operands[0];
    lExpr.accept(this);
    PsiType lType = lExpr.getType();
    addImplicitToStringThrows(op, lType);

    for (int i = 1; i < operands.length; i++) {
      PsiExpression rExpr = operands[i];
      PsiType rType = rExpr.getType();

      acceptBinaryRightOperand(op, lExpr, lType, rExpr, rType);
      addImplicitToStringThrows(op, rType);
      PsiType resType = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, op, true);
      DfaAnchor anchor = i == operands.length - 1 ? new JavaExpressionAnchor(expression) :
                         new JavaPolyadicPartAnchor(expression, i);
      generateBinOp(anchor, op, rExpr, resType);

      lExpr = rExpr;
      lType = resType;
    }
  }

  private void addImplicitToStringThrows(IElementType op, PsiType type) {
    if (myTrapTracker.shouldHandleException() && op.equals(JavaTokenType.PLUS) && 
        !TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(type) && !TypeUtils.isJavaLangString(type)) {
      addThrows(List.of());
    }
  }

  private void generateBinOp(DfaAnchor anchor, IElementType op, PsiExpression rExpr, PsiType resType) {
    if ((op == JavaTokenType.DIV || op == JavaTokenType.PERC) && resType != null && PsiTypes.longType().isAssignableFrom(resType)) {
      Object divisorValue = ExpressionUtils.computeConstantExpression(rExpr);
      if (!(divisorValue instanceof Number number) || (number.longValue() == 0)) {
        checkZeroDivisor(resType);
      }
    }
    Instruction inst;
    if (op.equals(JavaTokenType.PLUS) && TypeUtils.isJavaLangString(resType)) {
      inst = new StringConcatInstruction(anchor, TypeConstraints.exact(resType));
    }
    else if (PsiTypes.booleanType().equals(resType)) {
      if (op.equals(JavaTokenType.AND)) {
        inst = new BooleanAndOrInstruction(false, anchor);
      }
      else if (op.equals(JavaTokenType.OR)) {
        inst = new BooleanAndOrInstruction(true, anchor);
      }
      else {
        RelationType relation = op == JavaTokenType.XOR ? RelationType.NE : DfaPsiUtil.getRelationByToken(op);
        inst = relation != null ? new BooleanBinaryInstruction(relation, false, anchor) :
               new EvalUnknownInstruction(anchor, 2);
      }
    }
    else {
      inst = new NumericBinaryInstruction(JvmPsiRangeSetUtil.binOpFromToken(op), anchor);
    }
    addInstruction(inst);
  }

  private void acceptBinaryRightOperand(IElementType op,
                                        PsiExpression lExpr, @Nullable PsiType lType,
                                        PsiExpression rExpr, @Nullable PsiType rType) {
    boolean comparing = op == JavaTokenType.EQEQ || op == JavaTokenType.NE;
    boolean comparingRef = comparing
                           && !TypeConversionUtil.isPrimitiveAndNotNull(lType)
                           && !TypeConversionUtil.isPrimitiveAndNotNull(rType);

    boolean comparingPrimitiveNumeric = !comparingRef && ComparisonUtils.isComparisonOperation(op) &&
                                        TypeConversionUtil.isNumericType(lType) &&
                                        TypeConversionUtil.isNumericType(rType);

    // comparing object and primitive is not compilable code, but we try to balance types to avoid noise warnings
    boolean comparingObjectAndPrimitive = comparing && !comparingRef && !comparingPrimitiveNumeric &&
                                          (TypeConversionUtil.isNumericType(lType) || TypeConversionUtil.isNumericType(rType));

    boolean shift = op == JavaTokenType.GTGT || op == JavaTokenType.LTLT || op == JavaTokenType.GTGTGT;

    PsiType leftCast = null;
    PsiType rightCast = null;
    if (comparingObjectAndPrimitive) {
      leftCast = rightCast = TypeConversionUtil.isNumericType(lType) ? rType : lType;
    }
    else if (shift) {
      leftCast = PsiTypes.longType().equals(PsiPrimitiveType.getOptionallyUnboxedType(lType)) ? PsiTypes.longType() : PsiTypes.intType();
      rightCast = PsiTypes.longType().equals(PsiPrimitiveType.getOptionallyUnboxedType(rType)) ? PsiTypes.longType() : PsiTypes.intType();
    }
    else if (!comparingRef) {
      leftCast = rightCast = TypeConversionUtil.unboxAndBalanceTypes(lType, rType);
    }

    if (leftCast != null) {
      generateBoxingUnboxingInstructionFor(lExpr, lType, leftCast, false);
    }

    rExpr.accept(this);
    if (rightCast != null) {
      generateBoxingUnboxingInstructionFor(rExpr, rType, rightCast, false);
    }
  }

  void generateBoxingUnboxingInstructionFor(PsiExpression expression, PsiType expectedType) {
    generateBoxingUnboxingInstructionFor(expression, expression.getType(), expectedType, false);
  }

  void generateBoxingUnboxingInstructionFor(PsiElement context, PsiType actualType, PsiType expectedType, boolean explicit) {
    if (PsiTypes.voidType().equals(expectedType)) return;

    if (TypeConversionUtil.isPrimitiveAndNotNull(expectedType) &&
        TypeConversionUtil.isAssignableFromPrimitiveWrapper(toBound(actualType))) {
      addInstruction(new GetQualifiedValueInstruction(SpecialField.UNBOX));
      actualType = PsiPrimitiveType.getUnboxedType(actualType);
    }
    expectedType = toBound(expectedType);
    if (TypeConversionUtil.isPrimitiveAndNotNull(actualType) &&
        TypeConversionUtil.isAssignableFromPrimitiveWrapper(expectedType)) {
      addConditionalErrorThrow();
      PsiType boxedType = TypeConversionUtil.isPrimitiveWrapper(expectedType) ? expectedType :
                          ((PsiPrimitiveType)actualType).getBoxedType(context);
      PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(boxedType);
      if (unboxedType != null && !unboxedType.equals(actualType)) {
        addInstruction(new PrimitiveConversionInstruction(unboxedType, null));
      }
      addInstruction(new WrapDerivedVariableInstruction(DfTypes.typedObject(boxedType, Nullability.NOT_NULL), SpecialField.UNBOX));
    }
    else if (!Objects.equals(actualType, expectedType) &&
             TypeConversionUtil.isPrimitiveAndNotNull(actualType) &&
             TypeConversionUtil.isPrimitiveAndNotNull(expectedType) &&
             TypeConversionUtil.isNumericType(actualType) &&
             TypeConversionUtil.isNumericType(expectedType)) {
      DfaAnchor anchor = (explicit && context instanceof PsiExpression psiExpression) ? new JavaExpressionAnchor(psiExpression) : null;
      addInstruction(new PrimitiveConversionInstruction((PsiPrimitiveType)expectedType, anchor));
    }
  }

  private static PsiType toBound(PsiType type) {
    if (type instanceof PsiWildcardType wildcardType && wildcardType.isExtends()) {
      return wildcardType.getBound();
    }
    if (type instanceof PsiCapturedWildcardType capturedWildcardType) {
      return capturedWildcardType.getUpperBound();
    }
    return type;
  }

  private void generateShortCircuitAndOr(PsiExpression expression, PsiExpression[] operands, PsiType exprType, boolean and) {
    DeferredOffset endOffset = new DeferredOffset();
    for (int i = 0; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, exprType);

      PsiExpression nextOperand = i == operands.length - 1 ? null : operands[i + 1];
      if (nextOperand != null) {
        DeferredOffset nextOffset = new DeferredOffset();
        addInstruction(new ConditionalGotoInstruction(nextOffset, DfTypes.booleanValue(and), operand));
        push(DfTypes.booleanValue(!and), expression);
        addInstruction(new GotoInstruction(endOffset));
        nextOffset.setOffset(getInstructionCount());
        addInstruction(new FinishElementInstruction(null));
      }
    }
    endOffset.setOffset(getInstructionCount());
    addInstruction(new ResultOfInstruction(new JavaExpressionAnchor(expression)));
  }

  @Override public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    startElement(expression);
    PsiTypeElement operand = expression.getOperand();
    DfType classConstant = DfTypes.referenceConstant(operand.getType(), expression.getType());
    push(classConstant, expression);
    finishElement(expression);
  }

  @Override public void visitConditionalExpression(PsiConditionalExpression expression) {
    startElement(expression);

    PsiExpression thenExpression = expression.getThenExpression();
    if (thenExpression != null) {
      PsiExpression condition = expression.getCondition();
      PsiExpression elseExpression = expression.getElseExpression();
      DeferredOffset elseOffset = new DeferredOffset();
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiTypes.booleanType());
      PsiType type = expression.getType();
      addInstruction(new ConditionalGotoInstruction(elseOffset, DfTypes.FALSE, PsiUtil.skipParenthesizedExprDown(condition)));
      thenExpression.accept(this);
      generateBoxingUnboxingInstructionFor(thenExpression,type);

      DeferredOffset endOffset = new DeferredOffset();
      addInstruction(new GotoInstruction(endOffset));

      elseOffset.setOffset(getInstructionCount());
      if (elseExpression != null) {
        elseExpression.accept(this);
        generateBoxingUnboxingInstructionFor(elseExpression,type);
      }
      else {
        pushUnknown();
      }
      endOffset.setOffset(getInstructionCount());
    }
    else {
      pushUnknown();
    }

    finishElement(expression);
  }

  void pushUnknown() {
    addInstruction(new PushValueInstruction(DfType.TOP, null));
  }

  @Override public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    startElement(expression);

    PsiPattern pattern = expression.getPattern();
    PsiExpression operand = expression.getOperand();
    PsiType operandType = operand.getType();
    PsiTypeElement checkType = InstanceOfUtils.findCheckTypeElement(expression);
    CFGBuilder builder = new CFGBuilder(this);
    if (pattern == null) {
      if (checkType == null || operandType == null) {
        pushUnknown();
      }
      else {
        buildSimpleInstanceof(builder, expression, operand, operandType, checkType);
      }
    }
    else if (operandType != null) {
      builder
        .pushForWrite(createTempVariable(operand.getType()))
        .pushExpression(operand)
        .assign();
      processPatternInInstanceof(pattern, expression, operandType);
    }
    else {
      pushUnknown();
    }

    finishElement(expression);
  }

  private void buildSimpleInstanceof(CFGBuilder builder,
                                     PsiInstanceOfExpression expression,
                                     PsiExpression operand,
                                     PsiType operandType,
                                     PsiTypeElement checkType) {
    PsiType type = checkType.getType();
    builder
      .pushExpression(operand);
    generateInstanceOfInstructions(expression, new JavaExpressionAnchor(expression), operandType, type);
  }

  void addMethodThrows(@Nullable PsiMember methodOrClass) {
    if (myTrapTracker.shouldHandleException()) {
      if (methodOrClass == null) {
        DfaControlTransferValue transfer = myTrapTracker.transferValue(JAVA_LANG_THROWABLE);
        addInstruction(new EnsureInstruction(null, RelationType.EQ, DfType.TOP, transfer));
      } else {
        addThrows(methodOrClass instanceof PsiMethod method ? Arrays.asList(method.getThrowsList().getReferencedTypes()) : List.of());
      }
    }
  }

  private void addThrows(Collection<? extends PsiType> exceptions) {
    StreamEx<TypeConstraint> allExceptions = StreamEx.of(JAVA_LANG_ERROR, JAVA_LANG_RUNTIME_EXCEPTION)
      .map(fqn -> myTrapTracker.transfer(fqn).getThrowable());
    if (!exceptions.isEmpty()) {
      allExceptions = allExceptions.append(StreamEx.of(exceptions).map(TypeConstraints::instanceOf))
        .map(TypeConstraint::tryNegate).nonNull()
        .reduce(TypeConstraints.TOP, TypeConstraint::meet)
        .notInstanceOfTypes()
        .map(TypeConstraint.Exact::instanceOf);
    }
    allExceptions
      .map(exc -> myTrapTracker.transferValue(new ExceptionTransfer(exc)))
      .map(transfer -> new EnsureInstruction(null, RelationType.EQ, DfType.TOP, transfer))
      .forEach(this::addInstruction);
  }

  void throwException(@Nullable PsiType ref, @Nullable PsiElement anchor) {
    if (ref != null) {
      DfaControlTransferValue value = myTrapTracker.transferValue(new ExceptionTransfer(TypeConstraints.instanceOf(ref)));
      addInstruction(new ThrowInstruction(value, anchor));
    }
  }

  @Override
  public void visitMethodCallExpression(PsiMethodCallExpression call) {
    ArrayDeque<PsiMethodCallExpression> calls = new ArrayDeque<>();
    while (true) {
      calls.addFirst(call);
      startElement(call);

      if (tryInline(call)) {
        finishElement(call);
        calls.removeFirst();
        break;
      }

      PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();

      if (qualifierExpression == null) {
        DfaValue thisVariable = JavaDfaValueFactory.getQualifierOrThisValue(myFactory, call.getMethodExpression());
        if (thisVariable != null) {
          addInstruction(new PushInstruction(thisVariable, null));
        }
        else {
          pushUnknown();
        }
        break;
      } else if (!qualifierExpression.isPhysical() && call.isPhysical()) {
        // Possible -- see com.intellij.psi.impl.source.jsp.jspJava.JspMethodCallImpl.getMethodExpression
        pushUnknown();
        break;
      }
      call = ObjectUtil.tryCast(PsiUtil.skipParenthesizedExprDown(qualifierExpression), PsiMethodCallExpression.class);
      if (call == null) {
        qualifierExpression.accept(this);
        break;
      }
    }

    calls.forEach(this::finishCall);
  }

  private boolean tryInline(PsiMethodCallExpression call) {
    if (myInlining) {
      for (CallInliner inliner: INLINERS) {
        if (inliner.tryInlineCall(new CFGBuilder(this), call)) {
          addNullCheck(call);
          return true;
        }
      }
    }
    return false;
  }

  private void finishCall(PsiMethodCallExpression call) {
    PsiExpression[] expressions = call.getArgumentList().getExpressions();
    PsiReferenceExpression methodExpression = call.getMethodExpression();
    JavaResolveResult result = methodExpression.advancedResolve(false);
    PsiMethod method = ObjectUtil.tryCast(result.getElement(), PsiMethod.class);
    PsiParameter[] parameters = method != null ? method.getParameterList().getParameters() : null;

    if (method != null && ConsumedStreamUtils.isCheckedCallForConsumedStream(method)) {
      addConsumedStreamCheckInstructions(methodExpression.getQualifierExpression(), "java.lang.IllegalStateException");
    }

    for (int i = 0; i < expressions.length; i++) {
      PsiExpression paramExpr = expressions[i];
      paramExpr.accept(this);
      if (parameters != null && i < parameters.length) {
        generateBoxingUnboxingInstructionFor(paramExpr, result.getSubstitutor().substitute(parameters[i].getType()));
      }
    }
    foldVarArgs(call, parameters);

    addBareCall(call, methodExpression);
    finishElement(call);
  }

  void addBareCall(@Nullable PsiMethodCallExpression expression, PsiReferenceExpression reference) {
    addConditionalErrorThrow();
    PsiMethod method = ObjectUtil.tryCast(reference.resolve(), PsiMethod.class);
    List<? extends MethodContract> contracts =
      method == null ? Collections.emptyList() :
      DfaUtil.addRangeContracts(method, JavaMethodContractUtil.getMethodCallContracts(method, expression));
    PsiExpression anchor;
    if (expression == null) {
      assert reference instanceof PsiMethodReferenceExpression;
      addInstruction(new MethodCallInstruction((PsiMethodReferenceExpression)reference, contracts));
      anchor = reference;
    }
    else {
      if (ExpressionUtils.isVoidContext(expression) && ContainerUtil.all(contracts, c ->
        c.getReturnValue() != ContractReturnValue.fail() && !(c.getReturnValue() instanceof ContractReturnValue.ParameterReturnValue))) {
        // Do not track contracts if return value is not used
        contracts = Collections.emptyList();
      }
      addInstruction(new MethodCallInstruction(expression, contracts));
      anchor = expression;
    }
    processFailResult(method, contracts, anchor);

    addMethodThrows(method);
    if (expression != null) {
      addNullCheck(expression);
    }
  }

  private void processFailResult(@Nullable PsiMethod method, List<? extends MethodContract> contracts, PsiExpression anchor) {
    if ((CustomMethodHandlers.isConstantCall(method) && !PsiTypes.booleanType().equals(method.getReturnType())) 
        || ContainerUtil.exists(contracts, c -> c.getReturnValue().isFail())) {
      DfaControlTransferValue transfer = createTransfer(JAVA_LANG_THROWABLE);
      // if a contract resulted in 'fail', handle it
      addInstruction(new EnsureInstruction(new ContractFailureProblem(anchor), RelationType.NE, DfType.FAIL, transfer));
    }
  }

  @Nullable DfaControlTransferValue createTransfer(String exception) {
    return myTrapTracker.maybeTransferValue(exception);
  }

  @Override
  public void visitEnumConstant(PsiEnumConstant enumConstant) {
    if (enumConstant.getArgumentList() == null) return;

    pushUnknown();
    pushConstructorArguments(enumConstant);
    addInstruction(new MethodCallInstruction(enumConstant, Collections.emptyList()));
    addInstruction(new PopInstruction());
  }

  @Override public void visitNewExpression(PsiNewExpression expression) {
    startElement(expression);

    PsiType type = expression.getType();
    if (type instanceof PsiArrayType) {
      PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
      if (arrayInitializer != null) {
        initializeArray(arrayInitializer, expression);
        return;
      }
      final PsiExpression[] dimensions = expression.getArrayDimensions();
      int dims = dimensions.length;
      if (dims > 0) {
        for (final PsiExpression dimension : dimensions) {
          dimension.accept(this);
          generateBoxingUnboxingInstructionFor(dimension, PsiTypes.intType());
        }
        DfaControlTransferValue transfer = createTransfer("java.lang.NegativeArraySizeException");
        for (int i = dims - 1; i >= 0; i--) {
          addInstruction(new EnsureInstruction(new NegativeArraySizeProblem(dimensions[i]),
                                               RelationType.GE, DfTypes.intValue(0), transfer, true));
          if (i != 0) {
            addInstruction(new PopInstruction());
          }
        }
      }
      else {
        pushUnknown();
      }
      DfType arrayValue = TypeConstraints.exact(type).asDfType().meet(DfTypes.LOCAL_OBJECT);
      addInstruction(new WrapDerivedVariableInstruction(arrayValue, SpecialField.ARRAY_LENGTH, new JavaExpressionAnchor(expression)));

      initializeSmallArray((PsiArrayType)type, dimensions);
    }
    else {
      PsiExpression qualifier = expression.getQualifier();
      if (qualifier != null) {
        qualifier.accept(this);
      } else {
        DfaValue qualifierValue = myFactory.getUnknown();
        PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
        if (aClass != null && !aClass.hasModifierProperty(PsiModifier.STATIC)) {
          PsiClass outerClass = aClass.getContainingClass();
          if (outerClass != null && InheritanceUtil.hasEnclosingInstanceInScope(outerClass, expression, true, false)) {
            qualifierValue = ThisDescriptor.createThisValue(myFactory, outerClass);
          }
        }
        addInstruction(new PushInstruction(qualifierValue, null));
      }

      PsiMember constructorOrClass = pushConstructorArguments(expression);
      PsiMethod constructor = ObjectUtil.tryCast(constructorOrClass, PsiMethod.class);
      PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      if (anonymousClass != null) {
        handleClosure(anonymousClass);
      }

      addConditionalErrorThrow();
      if (isInitializableRecord(constructor, expression)) {
        initializeRecordConstructor(constructor, expression);
      }
      else {
        List<? extends MethodContract> contracts = constructor == null ? Collections.emptyList() :
                                                   JavaMethodContractUtil.getMethodCallContracts(constructor, null);
        contracts = DfaUtil.addRangeContracts(constructor, contracts);
        addInstruction(new MethodCallInstruction(expression, contracts));
        processFailResult(constructor, contracts, expression);
      }

      addMethodThrows(constructorOrClass);
    }

    finishElement(expression);
  }

  private static boolean isInitializableRecord(@Nullable PsiMethod constructor,
                                               PsiNewExpression expression) {
    if (constructor == null ||
        !JavaPsiRecordUtil.isCanonicalConstructor(constructor) ||
        JavaPsiRecordUtil.isCompactConstructor(constructor) ||
        JavaPsiRecordUtil.isExplicitCanonicalConstructor(constructor)) {
      return false;
    }
    PsiClass recordClass = constructor.getContainingClass();
    if (recordClass == null || !recordClass.isRecord()) return false;
    PsiRecordComponent[] components = recordClass.getRecordComponents();
    PsiExpressionList argumentList = expression.getArgumentList();
    int slotCount = MethodCallUtils.isVarArgCall(expression)
                    ? constructor.getParameterList().getParametersCount()
                    : (argumentList == null ? 0 : argumentList.getExpressionCount());
    if (slotCount == 0) return false;
    return slotCount == components.length;
  }

  /**
   * Creates the instructions modeling an implicit canonical record constructor call. On entry the stack holds
   * {@code [qualifier, arg0, ..., argN]}; on exit it holds the freshly created record object.
   */
  private void initializeRecordConstructor(PsiMethod constructor, PsiNewExpression expression) {
    PsiClass recordClass = Objects.requireNonNull(constructor.getContainingClass());
    DfType baseType = TypeConstraints.exactClass(recordClass).asDfType();
    PsiRecordComponent[] components = recordClass.getRecordComponents();
    DfaVariableValue result = myCurrentFlow.createTempVariable(baseType);
    DfType recordType = baseType.meet(DfTypes.NOT_NULL_OBJECT).meet(DfTypes.LOCAL_OBJECT);
    addInstruction(new PushValueInstruction(recordType, null));
    addInstruction(new SimpleAssignmentInstruction(null, result));
    addInstruction(new PopInstruction());
    for (int i = components.length - 1; i >= 0; i--) {
      PsiField field = JavaPsiRecordUtil.getFieldForComponent(components[i]);
      DfaValue fieldValue = field == null ? null : new PlainDescriptor(field).createValue(myFactory, result);
      if (fieldValue instanceof DfaVariableValue fieldVar) {
        addInstruction(new SimpleAssignmentInstruction(null, fieldVar));
      }
      addInstruction(new PopInstruction());
    }

    addInstruction(new PopInstruction()); //qualifier
    addInstruction(new PushInstruction(result, new JavaExpressionAnchor(expression)));
  }

  private void initializeSmallArray(PsiArrayType type, PsiExpression[] dimensions) {
    if (dimensions.length != 1) return;
    PsiType componentType = type.getComponentType();
    // Ignore objects as they may produce false NPE warnings due to non-perfect loop handling
    if (!(componentType instanceof PsiPrimitiveType)) return;
    Object val = ExpressionUtils.computeConstantExpression(dimensions[0]);
    if (val instanceof Integer) {
      int lengthValue = (Integer)val;
      if (lengthValue > 0 && lengthValue <= MAX_UNROLL_SIZE) {
        DfaVariableValue var = createTempVariable(type);
        addInstruction(new SimpleAssignmentInstruction(null, var));
        addInstruction(new PushValueInstruction(DfTypes.defaultValue(componentType)));
        for (int i = lengthValue - 1; i >= 0; i--) {
          DfaValue value = ArrayElementDescriptor.getArrayElementValue(getFactory(), var, i);
          if (value instanceof DfaVariableValue) {
            addInstruction(new SimpleAssignmentInstruction(null, (DfaVariableValue)value));
          }
        }
        addInstruction(new PopInstruction());
      }
    }
  }

  /**
   * @return resolved constructor, class (in case of default constructor), or null if resolve failes
   */
  private @Nullable PsiMember pushConstructorArguments(PsiConstructorCall call) {
    PsiExpressionList args = call.getArgumentList();
    PsiMember ctr = call.resolveConstructor();
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (call instanceof PsiNewExpression newExpression) {
      if (ctr == null) {
        PsiJavaCodeReferenceElement classRef = newExpression.getClassReference();
        ctr = classRef == null ? null : ObjectUtil.tryCast(classRef.resolve(), PsiClass.class);
      }
      substitutor = call.resolveMethodGenerics().getSubstitutor();
    }
    if (args != null) {
      PsiExpression[] arguments = args.getExpressions();
      PsiParameter[] parameters = ctr instanceof PsiMethod method ? method.getParameterList().getParameters() : null;
      for (int i = 0; i < arguments.length; i++) {
        PsiExpression argument = arguments[i];
        argument.accept(this);
        if (parameters != null && i < parameters.length) {
          generateBoxingUnboxingInstructionFor(argument, substitutor.substitute(parameters[i].getType()));
        }
      }
      foldVarArgs(call, parameters);
    }
    return ctr;
  }

  private void foldVarArgs(PsiCall call, PsiParameter[] parameters) {
    if (!MethodCallUtils.isVarArgCall(call)) return;
    PsiExpressionList args = call.getArgumentList();
    if (args == null) return;
    PsiParameter lastParameter = ArrayUtil.getLastElement(parameters);
    if (lastParameter != null && lastParameter.isVarArgs()) {
      int arraySize = args.getExpressionCount() - parameters.length + 1;
      if (arraySize >= 0) {
        addInstruction(new FoldArrayInstruction(null, DfTypes.typedObject(lastParameter.getType(), Nullability.NOT_NULL), arraySize));
      }
    }
  }

  @Override public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
    startElement(expression);
    PsiExpression inner = expression.getExpression();
    if (inner != null) {
      inner.accept(this);
    }
    else {
      pushUnknown();
    }
    finishElement(expression);
  }

  @Override public void visitPostfixExpression(PsiPostfixExpression expression) {
    startElement(expression);

    PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());
    if (operand != null) {
      processIncrementDecrement(expression, operand, true);
    } else {
      pushUnknown();
    }

    finishElement(expression);
  }

  private void processIncrementDecrement(PsiUnaryExpression expression, PsiExpression operand, boolean postIncrement) {
    LongRangeBinOp token;
    IElementType exprTokenType = expression.getOperationTokenType();
    if (exprTokenType.equals(JavaTokenType.MINUSMINUS)) {
      token = LongRangeBinOp.MINUS;
    }
    else if (exprTokenType.equals(JavaTokenType.PLUSPLUS)) {
      token = LongRangeBinOp.PLUS;
    }
    else {
      throw new IllegalArgumentException("Unexpected token: " + exprTokenType);
    }
    PsiType operandType = operand.getType();
    PsiArrayAccessExpression arrayStore = ObjectUtil.tryCast(operand, PsiArrayAccessExpression.class);
    if (arrayStore == null) {
      operand.accept(this);
      addInstruction(new DupInstruction());
      // stack: operand, old_value
    } else {
      pushArrayAndIndex(arrayStore);
      addInstruction(new SpliceInstruction(2, 1, 0, 1, 0));
      readArrayElement(arrayStore);
      // stack: array, index, old_value
    }
    if (postIncrement) {
      if (arrayStore != null) {
        addInstruction(new SpliceInstruction(3, 0, 2, 1, 0));
        // stack: old_value, array, index, old_value
      } else {
        addInstruction(new SpliceInstruction(2, 0, 1, 0));
        // stack: old_value, operand, old_value
      }
    }
    PsiPrimitiveType unboxedType = PsiPrimitiveType.getOptionallyUnboxedType(operandType);
    if (unboxedType == null) {
      // Unknown type; likely erroneous code: replace old_value with unknown
      addInstruction(new PopInstruction());
      pushUnknown();
    } else {
      generateBoxingUnboxingInstructionFor(operand, unboxedType);
      PsiType resultType = TypeConversionUtil.binaryNumericPromotion(unboxedType, PsiTypes.intType());
      Object addend = TypeConversionUtil.computeCastTo(1, resultType);
      addInstruction(new PushValueInstruction(DfTypes.primitiveConstant(addend)));
      addInstruction(new NumericBinaryInstruction(token, null));
      if (!unboxedType.equals(resultType)) {
        addInstruction(new PrimitiveConversionInstruction(unboxedType, null));
      }
      if (!(operandType instanceof PsiPrimitiveType)) {
        addInstruction(new WrapDerivedVariableInstruction(DfTypes.typedObject(operandType, Nullability.NOT_NULL), SpecialField.UNBOX));
      }
    }
    DfaValue dest = JavaDfaValueFactory.getExpressionDfaValue(myFactory, operand);
    if (arrayStore != null) {
      VariableDescriptor staticDescriptor = ArrayElementDescriptor.fromArrayAccess(arrayStore);
      addInstruction(new JavaArrayStoreInstruction(arrayStore, null, null, staticDescriptor));
    } else {
      addInstruction(new AssignInstruction(operand, null, dest));
    }
    if (postIncrement) {
      addInstruction(new PopInstruction());
    }
    addInstruction(new ResultOfInstruction(new JavaExpressionAnchor(expression)));
  }

  @Override public void visitPrefixExpression(PsiPrefixExpression expression) {
    startElement(expression);

    DfaValue dfaValue = expression.getOperationTokenType() == JavaTokenType.EXCL ? null : JavaDfaValueFactory
      .getExpressionDfaValue(myFactory, expression);
    if (dfaValue != null) {
      // Constant expression is computed: just push the result
      push(dfaValue, expression);
    }
    else {
      PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());

      if (operand == null) {
        pushUnknown();
      }
      else {
        if (PsiUtil.isIncrementDecrementOperation(expression)) {
          processIncrementDecrement(expression, operand, false);
        }
        else {
          operand.accept(this);
          PsiType type = expression.getType();
          PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
          generateBoxingUnboxingInstructionFor(operand, unboxed == null ? type : unboxed);
          if (expression.getOperationTokenType() == JavaTokenType.EXCL) {
            addInstruction(new NotInstruction(new JavaExpressionAnchor(expression)));
          }
          else if (expression.getOperationTokenType() == JavaTokenType.MINUS && (PsiTypes.intType().equals(type) || PsiTypes.longType()
            .equals(type))) {
            addInstruction(new PushValueInstruction(DfTypes.defaultValue(type)));
            addInstruction(new SwapInstruction());
            addInstruction(new NumericBinaryInstruction(LongRangeBinOp.MINUS, new JavaExpressionAnchor(expression)));
          }
          else {
            addInstruction(new PopInstruction());
            pushUnknown();
          }
        }
      }
    }

    finishElement(expression);
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    startElement(expression);
    pushReferenceExpression(expression);
    addNullCheck(expression);
    finishElement(expression);
  }

  private void pushReferenceExpression(PsiReferenceExpression expression) {
    JavaExpressionAnchor anchor = new JavaExpressionAnchor(expression);
    PsiVariable var = ObjectUtil.tryCast(expression.resolve(), PsiVariable.class);
    // complex assignments (e.g. "|=") are both reading and writing
    boolean writing = PsiUtil.isAccessedForWriting(expression) && !PsiUtil.isAccessedForReading(expression);
    if (!writing && var != null && !PlainDescriptor.hasInitializationHacks(var)) {
      DfaValue constValue = JavaDfaValueFactory.getConstantFromVariable(myFactory, var);
      if (constValue != null && !JavaDfaValueFactory.maybeUninitializedConstant(constValue, expression, var)) {
        addInstruction(new PushInstruction(constValue, anchor));
        return;
      }
    }
    final PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (var instanceof PsiField field && !field.hasModifierProperty(PsiModifier.STATIC) && !writing) {
      DfaValue effectiveQualifier = JavaDfaValueFactory.getQualifierOrThisValue(myFactory, expression);
      if (qualifierExpression == null) {
        addInstruction(new PushInstruction(effectiveQualifier == null ? myFactory.getUnknown() : effectiveQualifier, null));
      } else {
        qualifierExpression.accept(this);
      }
      VariableDescriptor descriptor = Objects.requireNonNull(JavaDfaValueFactory.getAccessedVariableOrGetter(field));
      if (effectiveQualifier instanceof DfaVariableValue qualifierVar && 
          JavaDfaHelpers.mayLeakFromType(descriptor.getDfType(qualifierVar)) &&
          JavaDfaHelpers.mayLeakFromExpression(expression)) {
        addInstruction(new EscapeInstruction(List.of(qualifierVar.getDescriptor())));
      }
      addInstruction(new GetQualifiedValueInstruction(descriptor, anchor));
      return;
    }
    if (qualifierExpression != null && !(qualifierExpression instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiClass)) {
      qualifierExpression.accept(this);
      addInstruction(new PopInstruction());
    }

    DfaValue value = JavaDfaValueFactory.getExpressionDfaValue(myFactory, expression);
    if (writing) {
      addInstruction(new JvmPushForWriteInstruction(value == null ? myFactory.getUnknown() : value));
    }
    else {
      addInstruction(new PushInstruction(value == null ? myFactory.getUnknown() : value, anchor));
    }
  }

  @Override public void visitLiteralExpression(PsiLiteralExpression expression) {
    startElement(expression);

    DfType dfType = DfaPsiUtil.fromLiteral(expression);
    push(dfType, expression);
    if (dfType == DfTypes.NULL) {
      addNullCheck(expression);
    }

    finishElement(expression);
  }

  @Override public void visitTypeCastExpression(PsiTypeCastExpression castExpression) {
    startElement(castExpression);
    PsiExpression operand = castExpression.getOperand();

    PsiType operandType = operand == null ? null : operand.getType();
    if (operand != null) {
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(castExpression, operandType, castExpression.getType(), true);
    }
    else {
      addInstruction(new PushValueInstruction(DfTypes.typedObject(castExpression.getType(), Nullability.UNKNOWN)));
    }

    final PsiTypeElement typeElement = castExpression.getCastType();
    if (typeElement != null && operandType != null && !(typeElement.getType() instanceof PsiPrimitiveType) &&
        !(operandType instanceof PsiLambdaExpressionType) && !(operandType instanceof PsiMethodReferenceType)) {
      DfaControlTransferValue transfer = createTransfer("java.lang.ClassCastException");
      addInstruction(new TypeCastInstruction(castExpression, operand, typeElement.getType(), transfer));
    } else {
      addInstruction(new ResultOfInstruction(new JavaExpressionAnchor(castExpression)));
    }
    finishElement(castExpression);
  }

  @Override public void visitClass(PsiClass aClass) {
  }

  final boolean wasAdded(PsiElement element) {
    return element != null &&
           myCurrentFlow.getStartOffset(element).getInstructionOffset() > -1 &&
           myCurrentFlow.getEndOffset(element).getInstructionOffset() > -1;
  }

  public void removeLambda(PsiLambdaExpression lambda) {
    int start = myCurrentFlow.getStartOffset(lambda).getInstructionOffset();
    int end = myCurrentFlow.getEndOffset(lambda).getInstructionOffset();
    for (int i = start; i < end; i++) {
      Instruction inst = myCurrentFlow.getInstruction(i);
      if (inst instanceof EscapeInstruction || inst instanceof ClosureInstruction) {
        myCurrentFlow.makeNop(i);
      }
    }
  }

  /**
   * Inline code block (lambda or method body) into this CFG. Incoming parameters are assumed to be handled already (if necessary)
   *
   * @param block block to inline
   * @param resultNullability desired nullability returned by block return statement
   * @param target a variable to store the block result (returned via {@code return} statement)
   * @param type resulting type
   */
  void inlineBlock(PsiCodeBlock block,
                   Nullability resultNullability,
                   DfaVariableValue target,
                   @Nullable PsiType type) {
    enterExpressionBlock(block, resultNullability, target, type);
    block.accept(this);
    exitExpressionBlock();
  }

  private void enterExpressionBlock(PsiCodeBlock block,
                                    Nullability resultNullability,
                                    DfaVariableValue target,
                                    @Nullable PsiType type) {
    // Transfer value is pushed to avoid emptying stack beyond this point
    pushTrap(new InsideInlinedBlockTrap(block));
    addInstruction(new PushInstruction(myFactory.controlTransfer(DfaControlTransferValue.RETURN_TRANSFER, FList.emptyList()), null));
    myExpressionBlockContext =
      new ExpressionBlockContext(myExpressionBlockContext, block, resultNullability == Nullability.NOT_NULL, target, type);
    startElement(block);
  }

  private void exitExpressionBlock() {
    finishElement(myExpressionBlockContext.myCodeBlock);
    myExpressionBlockContext = myExpressionBlockContext.myPreviousBlock;
    popTrap(InsideInlinedBlockTrap.class);
    // Pop transfer value
    addInstruction(new PopInstruction());
  }

  private static List<PsiElement> getClosures(PsiClass nestedClass) {
    List<PsiElement> closures = new ArrayList<>();
    closures.add(nestedClass); // process fields and initializers
    for (PsiMethod method : nestedClass.getMethods()) {
      PsiCodeBlock body = method.getBody();
      if (body != null && (method.isPhysical() || !nestedClass.isPhysical())) {
        // Skip analysis of non-physical methods of physical class (possibly autogenerated by some plugin like Lombok)
        closures.add(body);
      }
    }
    return closures;
  }

  /**
   * Register custom nullability problem for given expression or its subexpressions (e.g. ternary branches).
   * This would override default problem detection.
   *
   * @param expression an expression which nullness may cause the problem
   * @param problem a problem to check. Use {@link NullabilityProblemKind#noProblem} to suppress the problem which
   *                would be detected by default.
   */
  void addCustomNullabilityProblem(PsiExpression expression, NullabilityProblemKind<? super PsiExpression> problem) {
    myCustomNullabilityProblems.put(expression, problem);
  }

  /**
   * Unregister custom nullability problem previously registered via {@link #addCustomNullabilityProblem(PsiExpression, NullabilityProblemKind)}.
   * Call this after given expression is fully analyzed.
   *
   * @param expression an expression to deregister.
   */
  void removeCustomNullabilityProblem(PsiExpression expression) {
    myCustomNullabilityProblems.remove(expression);
  }

  /**
   * Create a synthetic variable (not declared in the original code) to be used within this control flow.
   *
   * @param type a type of variable to create
   * @return newly created variable
   */
  DfaVariableValue createTempVariable(@Nullable PsiType type) {
    DfType dfType = type == null ? DfType.BOTTOM : DfTypes.typedObject(type, Nullability.UNKNOWN);
    return myCurrentFlow.createTempVariable(dfType);
  }

  /**
   * @param expression expression to test
   * @return true if some inliner may add constraints on the precise type of given expression
   */
  public static boolean inlinerMayInferPreciseType(PsiExpression expression) {
    return ContainerUtil.exists(INLINERS, inliner -> inliner.mayInferPreciseType(expression));
  }

  /**
   * Create control flow for given PSI block (method body, lambda expression, etc.) and return it. May return cached block.
   * It's prohibited to change the resulting control flow (e.g. add instructions, update their indices, update flush variable lists, etc.)
   *
   * @param psiBlock psi-block
   * @param targetFactory factory to bind the PSI block to
   * @param useInliners whether to use inliners
   * @return resulting control flow; null if it cannot be built (e.g. if the code block contains unrecoverable errors)
   */
  public static @Nullable ControlFlow buildFlow(PsiElement psiBlock, DfaValueFactory targetFactory, boolean useInliners) {
    if (!useInliners) {
      return new ControlFlowAnalyzer(targetFactory, psiBlock, false).buildControlFlow();
    }
    return DataFlowIRProvider.forElement(psiBlock, targetFactory);
  }

  private static class CannotAnalyzeException extends RuntimeException {
    private CannotAnalyzeException() {
      super(null, null, false, false);
    }
  }

  static class ExpressionBlockContext {
    final @Nullable ExpressionBlockContext myPreviousBlock;
    final PsiCodeBlock myCodeBlock;
    final boolean myForceNonNullBlockResult;
    final DfaVariableValue myTarget;
    private final @Nullable PsiType myTargetType;

    ExpressionBlockContext(@Nullable ExpressionBlockContext previousBlock,
                           PsiCodeBlock codeBlock,
                           boolean forceNonNullBlockResult,
                           DfaVariableValue target,
                           @Nullable PsiType targetType) {
      myPreviousBlock = previousBlock;
      myCodeBlock = codeBlock;
      myForceNonNullBlockResult = forceNonNullBlockResult;
      myTarget = target;
      myTargetType = targetType;
    }

    boolean isSwitch() {
      return myCodeBlock.getParent() instanceof PsiSwitchExpression;
    }

    void generateReturn(PsiExpression returnValue, ControlFlowAnalyzer analyzer) {
      if (returnValue != null) {
        analyzer.addInstruction(new JvmPushForWriteInstruction(myTarget));
        if (!isSwitch()) {
          analyzer.addCustomNullabilityProblem(returnValue, myForceNonNullBlockResult ?
                                                            NullabilityProblemKind.nullableFunctionReturn : NullabilityProblemKind.noProblem);
        }
        returnValue.accept(analyzer);
        analyzer.removeCustomNullabilityProblem(returnValue);
        analyzer.generateBoxingUnboxingInstructionFor(returnValue, myTargetType);
        analyzer.addInstruction(new AssignInstruction(returnValue, null));
        analyzer.addInstruction(new PopInstruction());
      }

      analyzer.jumpOut(myCodeBlock);
    }
  }

  private static final CallInliner[] INLINERS = {
    new AssertJInliner(), new OptionalChainInliner(), new LambdaInliner(), new CollectionUpdateInliner(),
    new StreamChainInliner(), new MapUpdateInliner(), new MapGetOrDefaultInliner(), new AssumeInliner(), new ClassMethodsInliner(),
    new AssertAllInliner(), new AllNotNullInliner(), new BoxingInliner(), new SimpleMethodInliner(), new AccessorInliner(),
    new TransformInliner(), new EnumCompareInliner(), new IndexOfInliner(), new AssertInstanceOfInliner()
  };
}
