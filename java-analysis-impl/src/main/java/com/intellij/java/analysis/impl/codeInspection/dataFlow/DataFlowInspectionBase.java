// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import com.intellij.java.language.codeInsight.AnnotationTargetUtil;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.util.ExpressionUtil;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullabilityAnnotationInfo;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.java.analysis.codeInsight.intention.QuickFixFactory;
import consulo.language.editor.intention.BaseIntentionAction;
import com.intellij.java.analysis.codeInspection.AbstractBaseJavaLocalInspectionTool;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import com.intellij.java.analysis.impl.codeInspection.SetInspectionOptionFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.NullabilityProblemKind.NullabilityProblem;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.fix.RedundantInstanceofFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.fix.ReplaceWithArgumentFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.fix.ReplaceWithObjectsEqualsFix;
import consulo.language.dataFlow.interpreter.RunnerResult;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaMethodReferenceReturnAnchor;
import consulo.language.dataFlow.ir.ControlFlow;
import consulo.language.dataFlow.ir.Instruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.analysis.impl.codeInspection.nullable.NullableStuffInspectionBase;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.language.impl.psi.util.JavaPsiSwitchUtil;
import consulo.localize.LocalizeValue;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.logging.Logger;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.application.util.registry.Registry;
import consulo.util.lang.StringUtil;
import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.LambdaUtil;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiArrayType;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiCallExpression;
import com.intellij.java.language.psi.PsiCaseLabelElement;
import com.intellij.java.language.psi.PsiCaseLabelElementList;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassInitializer;
import com.intellij.java.language.psi.PsiCodeBlock;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import com.intellij.java.language.psi.PsiEnumConstantInitializer;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiForStatement;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiInstanceOfExpression;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiMethodReferenceExpression;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.PsiNewExpression;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiParenthesizedExpression;
import com.intellij.java.language.psi.PsiPattern;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiSwitchBlock;
import com.intellij.java.language.psi.PsiSwitchExpression;
import com.intellij.java.language.psi.PsiSwitchLabelStatement;
import com.intellij.java.language.psi.PsiSwitchLabelStatementBase;
import com.intellij.java.language.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.java.language.psi.PsiThrowStatement;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeCastExpression;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.intellij.java.language.psi.PsiTypeTestPattern;
import com.intellij.java.language.psi.PsiTypes;
import consulo.language.psi.PsiWhiteSpace;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.util.JavaPsiPatternUtil;
import com.intellij.java.language.impl.psi.util.JavaPsiRecordUtil;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.java.language.util.JavaPsiConstructorUtil;
import consulo.util.lang.ThreeState;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.collection.SmartHashSet;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.intellij.java.analysis.impl.codeInspection.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.intellij.java.analysis.impl.codeInspection.SwitchUtils;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import static consulo.util.lang.ObjectUtil.tryCast;

public abstract class DataFlowInspectionBase extends AbstractBaseJavaLocalInspectionTool<DataFlowInspectionStateBase> {
  private static final Logger LOG = Logger.getInstance(DataFlowInspectionBase.class);
  private static final String SHORT_NAME = "DataFlowIssue";
  // the options are in DataFlowInspectionStateBase (JetBrains keeps them as fields of the inspection with writeSettings)

  @Override
  public PsiElementVisitor buildVisitorImpl(ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            DataFlowInspectionStateBase state) {
    return new JavaElementVisitor() {

      @Override
      public void visitClass(PsiClass aClass) {
        if (aClass instanceof PsiTypeParameter) return;
        if (PsiUtil.isLocalOrAnonymousClass(aClass) && !(aClass instanceof PsiEnumConstantInitializer)) return;

        var runner = new StandardDataFlowRunner(holder.getProject(), ThreeState.fromBoolean(state.IGNORE_ASSERT_STATEMENTS));
        DataFlowInstructionVisitor visitor =
          analyzeDfaWithNestedClosures(state, aClass, holder, runner, Collections.singletonList(runner.createMemoryState()));
        List<DfaMemoryState> states = visitor.getEndOfInitializerStates();
        boolean physical = aClass.isPhysical();
        for (PsiMethod method : aClass.getConstructors()) {
          if (physical && !method.isPhysical()) {
            // Constructor could be provided by, e.g. Lombok plugin: ignore it, we won't report any problems inside anyway
            continue;
          }
          analyzeMethod(method, runner, getConstructorInitialStates(aClass, method, runner, states));
        }
        reportParametricAssignmentProblems(state, new ProblemReporter(holder, aClass),
                                           ParametricNullableBoundChecker.analyzeParametricField(aClass, parametricOptions(state)));
      }

      @Override
      public void visitMethod(PsiMethod method) {
        if (method.isConstructor()) return;
        var runner = new StandardDataFlowRunner(holder.getProject(), ThreeState.fromBoolean(state.IGNORE_ASSERT_STATEMENTS));
        analyzeMethod(method, runner, Collections.singletonList(runner.createMemoryState()));
      }

      private void analyzeMethod(PsiMethod method, StandardDataFlowRunner runner, List<DfaMemoryState> initialStates) {
        PsiCodeBlock scope = method.getBody();
        if (scope == null) return;
        PsiClass containingClass = PsiTreeUtil.getParentOfType(method, PsiClass.class);
        if (containingClass != null && PsiUtil.isLocalOrAnonymousClass(containingClass) && !(containingClass instanceof PsiEnumConstantInitializer)) return;

        analyzeDfaWithNestedClosures(state, scope, holder, runner, initialStates);
        analyzeNullLiteralMethodArguments(state, method, holder);
        reportNullableReturnsProblems(state, new ProblemReporter(holder, scope),
                                      ParametricNullableBoundChecker.analyzeParametricNullableReturn(method, parametricOptions(state)),
                                      Nullability.NOT_NULL, true, null,
                                      NullableNotNullManager.getInstance(method.getProject()));
      }

      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        super.visitMethodReferenceExpression(expression);
        if (!state.REPORT_UNSOUND_WARNINGS) return;
        final PsiElement resolve = expression.resolve();
        if (resolve instanceof PsiMethod) {
          final PsiType methodReturnType = ((PsiMethod)resolve).getReturnType();
          if (TypeConversionUtil.isPrimitiveWrapper(methodReturnType) && NullableNotNullManager.isNullable((PsiMethod)resolve)) {
            final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(expression);
            if (TypeConversionUtil.isPrimitiveAndNotNull(returnType) && !PsiTypes.voidType().equals(returnType)) {
              holder.registerProblem(expression, JavaAnalysisBundle.message("dataflow.message.unboxing.method.reference"));
            }
          }
        }
      }
    };
  }

  protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
    return null;
  }

  private void analyzeNullLiteralMethodArguments(DataFlowInspectionStateBase state, PsiMethod method, ProblemsHolder holder) {
    if (state.REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER && holder.isOnTheFly()) {
      for (PsiParameter parameter : NullParameterConstraintChecker.checkMethodParameters(method)) {
        PsiIdentifier name = parameter.getNameIdentifier();
        if (name != null) {
          holder.registerProblem(name, JavaAnalysisBundle.message("dataflow.method.fails.with.null.argument"),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, createNavigateToNullParameterUsagesFix(parameter));
        }
      }
    }
  }

  /**
   * Computes the initial data flow states for analyzing a constructor body: a fresh state when the constructor delegates
   * to another constructor (a chained {@code this(...)} call) or to an impure implicit super constructor, otherwise a copy
   * of the states reached at the end of the class initializers.
   */
  static List<DfaMemoryState> getConstructorInitialStates(PsiClass aClass,
                                                                   PsiMethod constructor,
                                                                   StandardDataFlowRunner runner,
                                                                   List<DfaMemoryState> endOfInitializerStates) {
    PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
    if (JavaPsiConstructorUtil.isChainedConstructorCall(call) ||
        (call == null && DfaUtil.hasImplicitImpureSuperCall(aClass, constructor))) {
      return Collections.singletonList(runner.createMemoryState());
    }
    return ContainerUtil.map(endOfInitializerStates, DfaMemoryState::createCopy);
  }

  private DataFlowInstructionVisitor analyzeDfaWithNestedClosures(DataFlowInspectionStateBase state, PsiElement scope,
                                                                  ProblemsHolder holder,
                                                                  StandardDataFlowRunner dfaRunner,
                                                                  Collection<? extends DfaMemoryState> initialStates) {
    DataFlowInstructionVisitor visitor = new DataFlowInstructionVisitor(state.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE);
    ControlFlow flow = dfaRunner.buildFlow(scope);
    if (flow == null) return visitor;
    visitor.initInstanceOf(flow.getInstructions());
    RunnerResult rc = dfaRunner.analyzeFlow(scope, visitor, initialStates, flow);
    if (rc == RunnerResult.OK) {
      if (dfaRunner.wasForciblyMerged() &&
          (ApplicationManager.getApplication().isUnitTestMode() || Registry.is("ide.dfa.report.imprecise"))) {
        reportAnalysisQualityProblem(holder, scope, "dataflow.not.precise");
      }
      createDescription(state, holder, visitor, scope, flow.getInstructions());
      dfaRunner.forNestedClosures((closure, states) -> analyzeDfaWithNestedClosures(state, closure, holder, dfaRunner, states));
    }
    else if (rc == RunnerResult.TOO_COMPLEX) {
      reportAnalysisQualityProblem(holder, scope, "dataflow.too.complex");
    }
    return visitor;
  }

  private static void reportAnalysisQualityProblem(ProblemsHolder holder, PsiElement scope, String problemKey) {
    PsiIdentifier name = null;
    String message = null;
    if(scope.getParent() instanceof PsiMethod) {
      name = ((PsiMethod)scope.getParent()).getNameIdentifier();
      message = JavaAnalysisBundle.message(problemKey, "Method <code>#ref</code>");
    } else if(scope instanceof PsiClass) {
      name = ((PsiClass)scope).getNameIdentifier();
      message = JavaAnalysisBundle.message(problemKey, "Class initializer");
    }
    if (name != null) { // Might be null for synthetic methods like JSP page.
      holder.registerProblem(name, message, ProblemHighlightType.WEAK_WARNING);
    }
  }

  protected List<LocalQuickFix> createCastFixes(PsiTypeCastExpression castExpression,
                                                                  PsiType realType,
                                                                  boolean alwaysFails) {
    return Collections.emptyList();
  }

  protected List<LocalQuickFix> createNPEFixes(DataFlowInspectionStateBase state, @Nullable PsiExpression qualifier,
                                                                 PsiExpression expression,
                                                                 boolean alwaysNull) {
    return Collections.emptyList();
  }

  protected List<LocalQuickFix> createUnboxingNullableFixes(PsiExpression qualifier, PsiElement anchor) {
    return Collections.emptyList();
  }

  protected List<LocalQuickFix> createMethodReferenceNPEFixes(PsiMethodReferenceExpression methodRef) {
    return Collections.emptyList();
  }

  protected @Nullable LocalQuickFix createUnwrapSwitchLabelFix() {
    return null;
  }

  protected @Nullable LocalQuickFix createIntroduceVariableFix() {
    return null;
  }

  protected LocalQuickFix createRemoveAssignmentFix(PsiAssignmentExpression assignment) {
    return null;
  }

  private void createDescription(DataFlowInspectionStateBase state, ProblemsHolder holder,
                                 final DataFlowInstructionVisitor visitor,
                                 PsiElement scope,
                                 Instruction [] instructions) {
    ProblemReporter reporter = new ProblemReporter(holder, scope);

    reportFailingCasts(state, reporter, visitor);
    reportUnreachableSwitchBranches(visitor.getSwitchLabelsReachability(), holder);

    reportAlwaysFailingCalls(state, reporter, visitor);

    List<NullabilityProblem<?>> problems = NullabilityProblemKind.postprocessNullabilityProblems(visitor.problems().toList());
    reportNullabilityProblems(state, reporter, problems);
    reportNullableReturns(state, reporter, problems, scope);

    reportRedundantInstanceOf(visitor, reporter);

    reportArrayAccessProblems(holder, visitor);

    reportArrayStoreProblems(holder, visitor);

    if (state.REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL && visitor.isAlwaysReturnsNotNull(instructions)) {
      reportAlwaysReturnsNotNull(state, holder, scope);
    }

    reportMutabilityViolations(holder, visitor.getMutabilityViolations(true),
                               JavaAnalysisBundle.message("dataflow.message.immutable.modified"));
    reportMutabilityViolations(holder, visitor.getMutabilityViolations(false),
                               JavaAnalysisBundle.message("dataflow.message.immutable.passed"));

    reportDuplicateAssignments(state, reporter, visitor);
    reportPointlessSameArguments(reporter, visitor);
    reportStreamConsumed(state, holder, visitor);
  }

  private static void reportRedundantInstanceOf(DataFlowInstructionVisitor visitor, ProblemReporter reporter) {
    visitor.redundantInstanceOfs().forEach(anchor -> {
      PsiExpression expression =
        anchor instanceof JavaExpressionAnchor ? ((JavaExpressionAnchor)anchor).getExpression() :
        anchor instanceof JavaMethodReferenceReturnAnchor ? ((JavaMethodReferenceReturnAnchor)anchor).getMethodReferenceExpression() :
        null;
      if (expression == null || shouldBeSuppressed(expression)) return;
      if (ContainerUtil.exists(JavaPsiPatternUtil.getExposedPatternVariables(expression),
                               var -> VariableAccessUtils.variableIsUsed(var, var.getDeclarationScope()))) {
        return;
      }
      LocalQuickFix action = new RedundantInstanceofFix(expression);
      reporter.registerProblem(expression,
                               JavaAnalysisBundle.message("dataflow.message.redundant.instanceof"),
                               action);
    });
  }

  private void reportUnreachableSwitchBranches(Map<PsiCaseLabelElement, ThreeState> labelReachability, ProblemsHolder holder) {
    if (labelReachability.isEmpty()) return;
    Set<PsiSwitchBlock> coveredSwitches = new HashSet<>();
    Map<PsiCaseLabelElement, PsiSwitchBlock> unreachableLabels = new HashMap<>();

    for (Map.Entry<PsiCaseLabelElement, ThreeState> entry : labelReachability.entrySet()) {
      if (entry.getValue() != ThreeState.YES) continue;
      PsiCaseLabelElement label = entry.getKey();
      PsiSwitchLabelStatementBase labelStatement = PsiImplUtil.getSwitchLabel(label);
      if (labelStatement == null) continue; // could be a guard
      PsiExpression guardExpression = labelStatement.getGuardExpression();
      if (guardExpression != null) {
        ThreeState guardReachability = labelReachability.get(guardExpression);
        if (guardReachability != ThreeState.YES) continue;
      }
      PsiSwitchBlock switchBlock = labelStatement.getEnclosingSwitchBlock();
      if (switchBlock == null) continue;
      if (!canRemoveTheOnlyReachableLabel(label, switchBlock)) continue;
      if (!StreamEx.iterate(labelStatement, Objects::nonNull, l -> PsiTreeUtil.getPrevSiblingOfType(l, PsiSwitchLabelStatementBase.class))
        .skip(1).map(PsiSwitchLabelStatementBase::getCaseLabelElementList)
        .nonNull().flatArray(PsiCaseLabelElementList::getElements)
        .append(StreamEx.iterate(label, Objects::nonNull, l -> PsiTreeUtil.getPrevSiblingOfType(l, PsiCaseLabelElement.class)).skip(1))
        .allMatch(l -> labelReachability.get(l) == ThreeState.NO)) {

        // Add all labels after always-reachable one as unreachable
        StreamEx.iterate(labelStatement, Objects::nonNull, l -> PsiTreeUtil.getNextSiblingOfType(l, PsiSwitchLabelStatementBase.class))
          .remove(SwitchUtils::isDefaultLabel)
          .skip(1).map(PsiSwitchLabelStatementBase::getCaseLabelElementList)
          .nonNull().flatArray(PsiCaseLabelElementList::getElements)
          .append(StreamEx.iterate(label, Objects::nonNull, l -> PsiTreeUtil.getNextSiblingOfType(l, PsiCaseLabelElement.class)).skip(1))
          .forEach(l -> unreachableLabels.put(l, switchBlock));
        continue;
      }
      coveredSwitches.add(switchBlock);
      LocalQuickFix unwrapFix;
      if ((switchBlock instanceof PsiSwitchExpression && !CodeBlockSurrounder.canSurround(((PsiSwitchExpression)switchBlock))) ||
          SwitchUtils.findRemovableUnreachableBranches(label, switchBlock).isEmpty()) {
        unwrapFix = null;
      }
      else {
        unwrapFix = createUnwrapSwitchLabelFix();
      }
      holder.registerProblem(label, JavaAnalysisBundle.message("dataflow.message.only.switch.label"),
                             LocalQuickFix.notNullElements(unwrapFix));
    }

    for (Map.Entry<PsiCaseLabelElement, ThreeState> entry : labelReachability.entrySet()) {
      if (entry.getValue() != ThreeState.NO) continue;
      PsiCaseLabelElement label = entry.getKey();
      PsiSwitchLabelStatementBase labelStatement = PsiImplUtil.getSwitchLabel(label);
      if (labelStatement == null) continue;
      PsiSwitchBlock switchBlock = labelStatement.getEnclosingSwitchBlock();
      if (switchBlock == null || coveredSwitches.contains(switchBlock)) continue;
      unreachableLabels.put(label, switchBlock);
    }
    unreachableLabels.forEach((label, switchBlock) -> {
      if (isThrowing(label)) return;
      // duplicate case label is a compilation error so no need to highlight by the inspection
      Set<PsiElement> suspiciousElements = findSuspiciousLabelElements(switchBlock);
      if (!suspiciousElements.contains(label)) {
        holder.newProblem(LocalizeValue.of(JavaAnalysisBundle.message("dataflow.message.unreachable.switch.label")))
            .range(label)
            .withOptionalFix(createDeleteLabelFix(label)).create();
      }
    });
  }

  protected @Nullable LocalQuickFix createDeleteLabelFix(PsiCaseLabelElement label) {
    return null;
  }

  private static boolean isThrowing(PsiCaseLabelElement label) {
    PsiCaseLabelElementList caseLabelList = tryCast(label.getParent(), PsiCaseLabelElementList.class);
    if (caseLabelList == null) return false;
    PsiSwitchLabelStatementBase labelStatement = tryCast(caseLabelList.getParent(), PsiSwitchLabelStatementBase.class);
    if (labelStatement == null) return false;
    if (labelStatement instanceof PsiSwitchLabeledRuleStatement) {
      return ControlFlowUtils.stripBraces(((PsiSwitchLabeledRuleStatement)labelStatement).getBody()) instanceof PsiThrowStatement;
    }
    if (labelStatement instanceof PsiSwitchLabelStatement) {
      PsiElement cur = labelStatement;
      while(true) {
        PsiElement next = cur.getNextSibling();
        if (!(next instanceof PsiComment) && !(next instanceof PsiWhiteSpace) && !(next instanceof PsiSwitchLabelStatement)) {
          return next instanceof PsiThrowStatement;
        }
        cur = next;
      }
    }
    return false;
  }

  private static boolean canRemoveTheOnlyReachableLabel(PsiCaseLabelElement label, PsiSwitchBlock switchBlock) {
    if (!(label instanceof PsiPattern)) return true;
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return false;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return false;
    if (!JavaPsiPatternUtil.isUnconditionalForType(label, selectorType)) return true;
    int branchCount = SwitchUtils.calculateBranchCount(switchBlock);
    // it's a compilation error if switch contains both default and an unconditional pattern, so no additional suggestion is needed
    return branchCount > 1;
  }

  private static void reportPointlessSameArguments(ProblemReporter reporter, DataFlowInstructionVisitor visitor) {
    visitor.pointlessSameArguments().forKeyValue((expr, eq) -> {
      PsiElement name = expr.getReferenceNameElement();
      if (name != null) {
        PsiExpression[] expressions = PsiExpression.EMPTY_ARRAY;
        if (expr.getParent() instanceof PsiMethodCallExpression) {
          expressions = ((PsiMethodCallExpression)expr.getParent()).getArgumentList().getExpressions();
          if (expressions.length == 2 && PsiUtil.isConstantExpression(expressions[0]) && PsiUtil.isConstantExpression(expressions[1]) &&
              !EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(expressions[0], expressions[1])) {
            return;
          }
        }
        if (eq.firstArgEqualToResult()) {
          String message = eq.argsEqual() ? JavaAnalysisBundle.message("dataflow.message.pointless.same.arguments") :
                           JavaAnalysisBundle.message("dataflow.message.pointless.same.argument.and.result", 1);
          LocalQuickFix fix = expressions.length == 2 ? new ReplaceWithArgumentFix(expressions[0], 0) : null;
          reporter.registerProblem(name, message, LocalQuickFix.notNullElements(fix));
        }
        else if (eq.argsEqual()) {
          reporter.registerProblem(name, JavaAnalysisBundle.message("dataflow.message.pointless.same.arguments"));
        }
        else if (eq.secondArgEqualToResult()) {
          LocalQuickFix fix = expressions.length == 2 ? new ReplaceWithArgumentFix(expressions[1], 1) : null;
          reporter.registerProblem(name, JavaAnalysisBundle.message("dataflow.message.pointless.same.argument.and.result", 2),
                                   LocalQuickFix.notNullElements(fix));
        }
      }
    });
  }

  private void reportDuplicateAssignments(DataFlowInspectionStateBase state, ProblemReporter reporter, DataFlowInstructionVisitor visitor) {
    visitor.sameValueAssignments().forEach(expr -> {
      expr = PsiUtil.skipParenthesizedExprDown(expr);
      if (expr == null) return;
      PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(expr, PsiAssignmentExpression.class);
      PsiElement context = PsiTreeUtil.getParentOfType(expr, PsiForStatement.class, PsiClassInitializer.class);
      if (context instanceof PsiForStatement && PsiTreeUtil.isAncestor(((PsiForStatement)context).getInitialization(), expr, true)) {
        return;
      }
      if (expr instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiField field) {
        // Final field assignment: even if redundant according to DFA model (e.g. this.field = null),
        // it's necessary due to language semantics
        if (field.hasModifierProperty(PsiModifier.FINAL)) return;
        if (context instanceof PsiClassInitializer) {
          if (assignment != null) {
            Object constValue = ExpressionUtils.computeConstantExpression(assignment.getRExpression());
            if (constValue == PsiTypesUtil.getDefaultValue(expr.getType())) {
              if ((field.hasModifierProperty(PsiModifier.STATIC) || ExpressionUtil.isEffectivelyUnqualified(ref)) &&
                  field.getContainingClass() == ((PsiClassInitializer)context).getContainingClass()) {
                return;
              }
            }
          }
        }
      }
      DfType value = CommonDataflow.getDfType(expr, state.IGNORE_ASSERT_STATEMENTS);
      // reported by ConstantValueInspection
      if (value == DfTypes.TRUE || value == DfTypes.FALSE) return;
      String message = assignment != null && !assignment.getOperationTokenType().equals(JavaTokenType.EQ)
                       ? JavaAnalysisBundle.message("dataflow.message.redundant.update")
                       : JavaAnalysisBundle.message("dataflow.message.redundant.assignment");
      reporter.registerProblem(expr, message, LocalQuickFix.notNullElements(createRemoveAssignmentFix(assignment)));
    });
  }

  private void reportMutabilityViolations(ProblemsHolder holder, Set<PsiElement> violations, String message) {
    for (PsiElement violation : violations) {
      holder.registerProblem(violation, message, LocalQuickFix.notNullElements(createMutabilityViolationFix(violation)));
    }
  }

  protected LocalQuickFix createMutabilityViolationFix(PsiElement violation) {
    return null;
  }

  protected void reportNullabilityProblems(DataFlowInspectionStateBase state, ProblemReporter reporter, List<NullabilityProblem<?>> problems) {
    for (NullabilityProblem<?> problem : problems) {
      PsiExpression expression = problem.getDereferencedExpression();
      boolean nullLiteral = ExpressionUtils.isNullLiteral(expression);
      boolean alwaysNull = problem.isAlwaysNull(state.IGNORE_ASSERT_STATEMENTS);
      if (!state.REPORT_UNSOUND_WARNINGS && !alwaysNull) {
        continue;
      }
      // Expression of null type: could be failed LVTI, skip it to avoid confusion
      if (expression != null && !nullLiteral && PsiTypes.nullType().equals(expression.getType())) continue;
      NullabilityProblemKind.innerClassNPE.ifMyProblem(problem, newExpression -> {
        List<LocalQuickFix> fixes = createNPEFixes(state, newExpression.getQualifier(), newExpression, alwaysNull);
        reporter
          .registerProblem(getElementToHighlight(newExpression), problem.getMessage(state.IGNORE_ASSERT_STATEMENTS),
                           fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      });
      NullabilityProblemKind.callMethodRefNPE.ifMyProblem(problem, methodRef ->
        reporter.registerProblem(methodRef, JavaAnalysisBundle.message("dataflow.message.npe.methodref.invocation"),
                                 createMethodReferenceNPEFixes(methodRef).toArray(LocalQuickFix.EMPTY_ARRAY)));
      NullabilityProblemKind.callNPE.ifMyProblem(problem, call ->
        reportCallMayProduceNpe(state, reporter, problem.getMessage(state.IGNORE_ASSERT_STATEMENTS), call, alwaysNull));
      NullabilityProblemKind.passingToNotNullParameter.ifMyProblem(problem, _ -> {
        List<LocalQuickFix> fixes = createNPEFixes(state, expression, expression, alwaysNull);
        reporter.registerProblem(expression, problem.getMessage(state.IGNORE_ASSERT_STATEMENTS), fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      });
      NullabilityProblemKind.passingToNotNullMethodRefParameter.ifMyProblem(problem, methodRef -> {
        LocalQuickFix[] fixes = createMethodReferenceNPEFixes(methodRef).toArray(LocalQuickFix.EMPTY_ARRAY);
        reporter.registerProblem(methodRef, JavaAnalysisBundle.message("dataflow.message.passing.nullable.argument.methodref"), fixes);
      });
      NullabilityProblemKind.unboxingMethodRefParameter.ifMyProblem(problem, methodRef -> {
        LocalQuickFix[] fixes = createMethodReferenceNPEFixes(methodRef).toArray(LocalQuickFix.EMPTY_ARRAY);
        reporter.registerProblem(methodRef, JavaAnalysisBundle.message("dataflow.message.unboxing.nullable.argument.methodref"), fixes);
      });
      NullabilityProblemKind.arrayAccessNPE.ifMyProblem(problem, arrayAccess -> {
        LocalQuickFix[] fixes = createNPEFixes(state, arrayAccess.getArrayExpression(), arrayAccess,
                                               alwaysNull).toArray(LocalQuickFix.EMPTY_ARRAY);
        reporter.registerProblem(arrayAccess, problem.getMessage(state.IGNORE_ASSERT_STATEMENTS), fixes);
      });
      NullabilityProblemKind.templateNPE.ifMyProblem(problem, template -> {
        PsiExpression processor = template.getProcessor();
        LocalQuickFix[] fixes = createNPEFixes(state, processor, template, alwaysNull).toArray(LocalQuickFix.EMPTY_ARRAY);
        reporter.registerProblem(processor, problem.getMessage(state.IGNORE_ASSERT_STATEMENTS), fixes);
      });
      NullabilityProblemKind.fieldAccessNPE.ifMyProblem(problem, element -> {
        PsiElement parent = element.getParent();
        PsiExpression fieldAccess = parent instanceof PsiReferenceExpression ? (PsiExpression)parent : element;
        LocalQuickFix[] fix = createNPEFixes(state, element, fieldAccess, alwaysNull).toArray(LocalQuickFix.EMPTY_ARRAY);
        reporter.registerProblem(element, problem.getMessage(state.IGNORE_ASSERT_STATEMENTS), fix);
      });
      NullabilityProblemKind.unboxingNullable.ifMyProblem(problem, element -> {
        PsiExpression anchor = expression;
        if (anchor instanceof PsiTypeCastExpression && anchor.getType() instanceof PsiPrimitiveType) {
          anchor = Objects.requireNonNull(((PsiTypeCastExpression)anchor).getOperand());
        }
        if (anchor != null) {
          LocalQuickFix[] fixes = createUnboxingNullableFixes(anchor, element).toArray(LocalQuickFix.EMPTY_ARRAY);
          reporter.registerProblem(anchor, problem.getMessage(state.IGNORE_ASSERT_STATEMENTS), fixes);
        }
      });
      NullabilityProblemKind.nullableFunctionReturn.ifMyProblem(
        problem, expr -> reporter.registerProblem(expression == null ? expr : expression, problem.getMessage(state.IGNORE_ASSERT_STATEMENTS)));
      Consumer<PsiExpression> reportNullability = _ -> reportNullabilityProblem(state, reporter, problem, expression);
      NullabilityProblemKind.assigningToNotNull.ifMyProblem(problem, reportNullability);
      NullabilityProblemKind.storingToNotNullArray.ifMyProblem(problem, reportNullability);
      if (state.REPORT_MATCHED_EXCEPTION) {
        NullabilityProblemKind.deconstructionMatchException.ifMyProblem(problem, pattern -> {
          reportDeconstructionMatchExceptionProblem(state, reporter, problem, pattern);
        });
      }
      if (state.SUGGEST_NULLABLE_ANNOTATIONS) {
        NullabilityProblemKind.passingToNonAnnotatedMethodRefParameter.ifMyProblem(
          problem, methodRef -> reportNullableArgumentPassedToNonAnnotatedMethodRef(state, reporter, problem, methodRef));
        NullabilityProblemKind.passingToNonAnnotatedParameter.ifMyProblem(
          problem,
          top -> reportNullableArgumentsPassedToNonAnnotated(state, reporter, problem.getMessage(state.IGNORE_ASSERT_STATEMENTS), expression, top,
                                                             alwaysNull));
        NullabilityProblemKind.assigningToNonAnnotatedField.ifMyProblem(
          problem, top -> reportNullableAssignedToNonAnnotatedField(state, reporter, top, expression, problem.getMessage(state.IGNORE_ASSERT_STATEMENTS),
                                                                    alwaysNull));
      }
    }
  }

  private void reportDeconstructionMatchExceptionProblem(DataFlowInspectionStateBase state, ProblemReporter reporter,
                                                         NullabilityProblem<?> problem,
                                                         PsiPattern pattern) {
    LocalQuickFix modCommandAction = createSwitchAddDefault(pattern);
    if (modCommandAction == null) {
      reporter.registerProblem(pattern, problem.getMessage(state.IGNORE_ASSERT_STATEMENTS));
      return;
    }
    reporter.registerProblem(pattern, problem.getMessage(state.IGNORE_ASSERT_STATEMENTS), modCommandAction);
  }

  private static @Nullable LocalQuickFix createSwitchAddDefault(PsiPattern pattern) {
    QuickFixFactory quickFixFactory = QuickFixFactory.getInstance();
    PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(pattern, PsiSwitchBlock.class);
    if (switchBlock == null) return null;
    LocalQuickFix modCommandAction = tryCast(quickFixFactory.createAddSwitchDefaultFix(switchBlock, LocalizeValue.empty()), LocalQuickFix.class);
    return modCommandAction;
  }

  private void reportNullabilityProblem(DataFlowInspectionStateBase state, ProblemReporter reporter,
                                        NullabilityProblem<?> problem,
                                        PsiExpression expr) {
    LocalQuickFix[] fixes = createNPEFixes(state, expr, expr, problem.isAlwaysNull(state.IGNORE_ASSERT_STATEMENTS))
      .toArray(LocalQuickFix.EMPTY_ARRAY);
    reporter.registerProblem(expr, problem.getMessage(state.IGNORE_ASSERT_STATEMENTS), fixes);
  }

  private static void reportArrayAccessProblems(ProblemsHolder holder, DataFlowInstructionVisitor visitor) {
    visitor.outOfBoundsArrayAccesses().forEach(access -> {
      PsiExpression indexExpression = access.getIndexExpression();
      if (indexExpression != null) {
        holder.registerProblem(indexExpression, JavaAnalysisBundle.message("dataflow.message.array.index.out.of.bounds"));
      }
    });
    visitor.negativeArraySizes().forEach(dimExpression -> {
      holder.registerProblem(dimExpression, JavaAnalysisBundle.message("dataflow.message.negative.array.size"));
    });
  }

  private void reportStreamConsumed(DataFlowInspectionStateBase state, ProblemsHolder holder, DataFlowInstructionVisitor visitor) {
    visitor.streamConsumed().forKeyValue((psiElement, alwaysFails) -> {
      if (!state.REPORT_UNSOUND_WARNINGS && !alwaysFails) return;
      holder.registerProblem(psiElement, JavaAnalysisBundle.message(alwaysFails ? "dataflow.message.stream.consumed.always" :
                                                                    "dataflow.message.stream.consumed"));
    });
  }

  private static void reportArrayStoreProblems(ProblemsHolder holder, DataFlowInstructionVisitor visitor) {
    visitor.getArrayStoreProblems().forEach(
      (assignment, types) -> holder.registerProblem(assignment.getOperationSign(), JavaAnalysisBundle
        .message("dataflow.message.arraystore", types.getFirst().getCanonicalText(), types.getSecond().getCanonicalText())));
  }

  private void reportAlwaysReturnsNotNull(DataFlowInspectionStateBase state, ProblemsHolder holder, PsiElement scope) {
    if (!(scope.getParent() instanceof PsiMethod method) || PsiUtil.canBeOverridden(method)) return;

    NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(scope.getProject()).findOwnNullabilityInfo(method);
    if (info == null || info.getNullability() != Nullability.NULLABLE) return;
    if (TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_VOID, method.getReturnType())) return;

    PsiAnnotation annotation = info.getAnnotation();
    if (!annotation.isPhysical() || alsoAppliesToInternalSubType(annotation, method)) return;

    PsiJavaCodeReferenceElement annoName = annotation.getNameReferenceElement();
    assert annoName != null;
    String msg = JavaAnalysisBundle
      .message("dataflow.message.return.notnull.from.nullable", NullableStuffInspectionBase.getPresentableAnnoName(annotation),
               method.getName());
    holder.newProblem(LocalizeValue.of(msg))
      .range(annoName)
      .withOptionalFix(AddAnnotationPsiFix.createAddNotNullFix(method))
      .withFix(new SetInspectionOptionFix<>(this, (i, v) -> i.REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL = v,
                                            LocalizeValue.of(JavaAnalysisBundle.message(
                                              "inspection.data.flow.turn.off.nullable.returning.notnull.quickfix")),
                                            false))
      .create();
  }

  private static boolean alsoAppliesToInternalSubType(PsiAnnotation annotation, PsiMethod method) {
    return AnnotationTargetUtil.isTypeAnnotation(annotation) && method.getReturnType() instanceof PsiArrayType;
  }

  private void reportAlwaysFailingCalls(DataFlowInspectionStateBase state, ProblemReporter reporter, DataFlowInstructionVisitor visitor) {
    visitor.alwaysFailingCalls().remove(TestUtils::isExceptionExpected).forEach(anchor -> {
      List<? extends MethodContract> contracts = DataFlowInstructionVisitor.getContracts(anchor);
      if (contracts != null) {
        PsiMethod method = anchor instanceof PsiCallExpression call ? call.resolveMethod() :
                           anchor instanceof PsiMethodReferenceExpression methodRef ? tryCast(methodRef.resolve(), PsiMethod.class) : null;
        contracts = DfaUtil.addRangeContracts(method, contracts);
      }
      if (contracts == null) return;
      String message = getContractMessage(contracts);
      LocalQuickFix causeFix = createExplainFix(state, anchor, new TrackingRunner.FailingCallDfaProblemType());
      reporter.registerProblem(getElementToHighlight(anchor), message, LocalQuickFix.notNullElements(causeFix));
    });
  }

  private static String getContractMessage(List<? extends MethodContract> contracts) {
    List<? extends MethodContract> failedContracts = ContainerUtil.filter(contracts, mc -> mc.getReturnValue().isFail());
    if (failedContracts.isEmpty()) {
      return JavaAnalysisBundle.message("dataflow.message.fail");
    }
    if (ContainerUtil.and(failedContracts, mc -> !mc.getConditions().isEmpty() &&
                                            ContainerUtil.and(mc.getConditions(), ContractValue::isBoundCheckingCondition))) {
      return JavaAnalysisBundle.message("dataflow.message.contract.fail.index");
    }
    return JavaAnalysisBundle.message("dataflow.message.contract.fail");
  }

  private static PsiElement getElementToHighlight(PsiElement element) {
    PsiJavaCodeReferenceElement ref;
    if (element instanceof PsiNewExpression newExpression) {
      ref = newExpression.getClassReference();
    }
    else if (element instanceof PsiMethodCallExpression callExpression) {
      ref = callExpression.getMethodExpression();
    }
    else {
      return element;
    }
    if (ref != null) {
      PsiElement name = ref.getReferenceNameElement();
      return name != null ? name : ref;
    }
    return element;
  }

  private void reportNullableArgumentPassedToNonAnnotatedMethodRef(DataFlowInspectionStateBase state, ProblemReporter reporter,
                                                                   NullabilityProblem<?> problem,
                                                                   PsiMethodReferenceExpression methodRef) {
    PsiMethod target = tryCast(methodRef.resolve(), PsiMethod.class);
    if (target == null) return;
    PsiParameter[] parameters = target.getParameterList().getParameters();
    if (parameters.length == 0) return;
    PsiParameter parameter = parameters[0];
    if (!BaseIntentionAction.canModify(parameter)) return;
    LocalQuickFix fix = AddAnnotationPsiFix.createAddNullableFix(parameter);
    reporter.registerProblem(methodRef, problem.getMessage(state.IGNORE_ASSERT_STATEMENTS),
                             LocalQuickFix.notNullElements(parameters.length == 1 ? fix : null));
  }

  private void reportNullableArgumentsPassedToNonAnnotated(DataFlowInspectionStateBase state, ProblemReporter reporter,
                                                           String message,
                                                           PsiExpression expression,
                                                           PsiExpression top, boolean alwaysNull) {
    PsiParameter parameter = MethodCallUtils.getParameterForArgument(top);
    if (parameter == null) return;
    PsiModifierListOwner target = Objects.requireNonNullElse(JavaPsiRecordUtil.getComponentForCanonicalConstructorParameter(parameter), parameter);
    if (BaseIntentionAction.canModify(target)) {
      List<LocalQuickFix> fixes = createNPEFixes(state, expression, top, alwaysNull);
      fixes.add(AddAnnotationPsiFix.createAddNullableFix(target));
      reporter.registerProblem(expression, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    }
  }

  private void reportNullableAssignedToNonAnnotatedField(DataFlowInspectionStateBase state, ProblemReporter reporter,
                                                         PsiExpression top,
                                                         PsiExpression expression,
                                                         String message,
                                                         boolean alwaysNull) {
    PsiField field = getAssignedField(top);
    if (field != null) {
      List<LocalQuickFix> fixes = createNPEFixes(state, expression, top, alwaysNull);
      fixes.add(AddAnnotationPsiFix.createAddNullableFix(field));
      reporter.registerProblem(expression, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    }
  }

  private static @Nullable PsiField getAssignedField(PsiElement assignedValue) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(assignedValue.getParent());
    if (parent instanceof PsiAssignmentExpression) {
      PsiExpression lExpression = ((PsiAssignmentExpression)parent).getLExpression();
      PsiElement target = lExpression instanceof PsiReferenceExpression ? ((PsiReferenceExpression)lExpression).resolve() : null;
      return tryCast(target, PsiField.class);
    }
    return null;
  }

  private void reportCallMayProduceNpe(DataFlowInspectionStateBase state, ProblemReporter reporter, String message, PsiMethodCallExpression callExpression,
                                       boolean alwaysNull) {
    PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
    List<LocalQuickFix> fixes = createNPEFixes(state, methodExpression.getQualifierExpression(), callExpression, alwaysNull);
    if (!alwaysNull) {
      ContainerUtil.addIfNotNull(fixes, ReplaceWithObjectsEqualsFix.createFix(callExpression, methodExpression));
    }

    PsiElement toHighlight = getElementToHighlight(callExpression);
    reporter.registerProblem(toHighlight, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
  }

  private void reportFailingCasts(DataFlowInspectionStateBase state, ProblemReporter reporter, DataFlowInstructionVisitor visitor) {
    visitor.getFailingCastExpressions().forKeyValue((typeCast, info) -> {
      boolean alwaysFails = info.getFirst();
      PsiType realType = info.getSecond();
      if (!state.REPORT_UNSOUND_WARNINGS && !alwaysFails) return;
      PsiExpression operand = typeCast.getOperand();
      PsiTypeElement castType = typeCast.getCastType();
      if (ExpressionUtils.isNullLiteral(operand) || DfTypes.NULL.equals(CommonDataflow.getDfType(operand, state.IGNORE_ASSERT_STATEMENTS))) {
        // Skip reporting if cast operand is always null: null can be cast to anything
        return;
      }
      assert castType != null;
      assert operand != null;
      List<LocalQuickFix> fixes = new ArrayList<>(createCastFixes(typeCast, realType, alwaysFails));
      ContainerUtil.addIfNotNull(fixes, createExplainFix(state, typeCast, new TrackingRunner.CastDfaProblemType()));
      String text = PsiExpressionTrimRenderer.render(operand);
      String message = alwaysFails ?
                       JavaAnalysisBundle.message("dataflow.message.cce.always", text) :
                       JavaAnalysisBundle.message("dataflow.message.cce", text);
      reporter.registerProblem(castType, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    });
  }

  protected @Nullable LocalQuickFix createExplainFix(DataFlowInspectionStateBase state, PsiExpression anchor, TrackingRunner.DfaProblemType problemType) {
    return null;
  }

  private static boolean shouldBeSuppressed(PsiExpression anchor) {
    if (anchor instanceof PsiInstanceOfExpression) {
      PsiType type = ((PsiInstanceOfExpression)anchor).getOperand().getType();
      if (type == null || !TypeConstraints.instanceOf(type).isResolved()) return true;
      // 5.20.2 Removed restriction on pattern instanceof for unconditional patterns (JEP 427)
      if (PsiUtil.isAvailable(JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS, anchor)) return false;
      PsiPattern pattern = ((PsiInstanceOfExpression)anchor).getPattern();
      if (pattern instanceof PsiTypeTestPattern && ((PsiTypeTestPattern)pattern).getPatternVariable() != null) {
        PsiTypeElement checkType = ((PsiTypeTestPattern)pattern).getCheckType();
        // Reported as compilation error
        return checkType != null && checkType.getType().isAssignableFrom(type);
      }
    }
    return false;
  }

  private static @Nullable PsiMethod getScopeMethod(PsiElement block) {
    PsiElement parent = block.getParent();
    if (parent instanceof PsiMethod) return (PsiMethod)parent;
    if (parent instanceof PsiLambdaExpression) return LambdaUtil.getFunctionalInterfaceMethod(((PsiLambdaExpression)parent).getFunctionalInterfaceType());
    return null;
  }

  private void reportNullableReturns(DataFlowInspectionStateBase state, ProblemReporter reporter,
                                     List<NullabilityProblem<?>> problems,
                                     PsiElement block) {
    final PsiMethod method = getScopeMethod(block);
    if (method == null) return;
    NullableNotNullManager manager = NullableNotNullManager.getInstance(method.getProject());
    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(method);
    if (info == null || info.isInferred()) info = DfaPsiUtil.getTypeNullabilityInfo(PsiTypesUtil.getMethodReturnType(block));
    PsiAnnotation anno = info == null ? null : info.getAnnotation();
    Nullability nullability = info == null ? Nullability.UNKNOWN : info.getNullability();
    PsiType returnType = method.getReturnType();
    if (nullability == Nullability.NULLABLE) {
      // A type variable with a @Nullable upper bound (parametric nullness) is handled separately by
      // ParametricNullableReturnChecker; do not report it through the regular nullable-return path to avoid duplicates.
      if (!info.isInferred() || DfaPsiUtil.getTypeNullability(returnType) == Nullability.NULLABLE) return;
    }
    // In rare cases, inference may produce different result (e.g. if nullable method overrides non-null method)
    if (nullability == Nullability.NOT_NULL && info.isInferred()) return;

    // When the dedicated parametric checker will report this nullable return (option on, type-variable return type),
    // skip the generic suggest-@Nullable path to avoid a duplicate warning on the same return
    if (nullability != Nullability.NOT_NULL && state.REPORT_UNSPECIFIED_PARAMETRIC_NULLNESS
        && PsiUtil.resolveClassInClassTypeOnly(returnType) instanceof PsiTypeParameter) {
      return;
    }

    if (nullability != Nullability.NOT_NULL && (!state.SUGGEST_NULLABLE_ANNOTATIONS || block.getParent() instanceof PsiLambdaExpression)) return;

    // no warnings in void lambdas, where the expression is not returned anyway
    if (block instanceof PsiExpression && block.getParent() instanceof PsiLambdaExpression && PsiTypes.voidType().equals(returnType)) return;

    // no warnings for Void methods, where only null can be possibly returned
    if (returnType == null || returnType.equalsToText(CommonClassNames.JAVA_LANG_VOID)) return;

    reportNullableReturnsProblems(state, reporter, problems, nullability, false, anno, manager);
  }

  protected void reportNullableReturnsProblems(DataFlowInspectionStateBase state, ProblemReporter reporter,
                                               List<NullabilityProblem<?>> problems,
                                               Nullability nullability,
                                               boolean parametricReturn,
                                               @Nullable PsiAnnotation anno,
                                               NullableNotNullManager manager) {
    for (NullabilityProblem<PsiExpression> problem : StreamEx.of(problems).map(NullabilityProblemKind.nullableReturn::asMyProblem)
      .nonNull()) {
      if (problem == null) continue;
      final PsiExpression anchor = problem.getAnchor();
      PsiExpression expr = problem.getDereferencedExpression();

      boolean exactlyNull = problem.isAlwaysNull(state.IGNORE_ASSERT_STATEMENTS);
      if (!state.REPORT_UNSOUND_WARNINGS && !exactlyNull) continue;
      if (nullability == Nullability.NOT_NULL) {
        final String text;
        if (parametricReturn || anno == null) {
          // The return type is a (parametric) type variable that may be instantiated as non-null:
          // a @Nullable upper bound, an unspecified bound, or a plain unannotated type parameter.
          text = exactlyNull
                 ? JavaAnalysisBundle.message("dataflow.message.return.null.from.parametric")
                 : JavaAnalysisBundle.message("dataflow.message.return.nullable.from.parametric");
        }
        else {
          String presentable = NullableStuffInspectionBase.getPresentableAnnoName(anno);
          text = exactlyNull
                 ? JavaAnalysisBundle.message("dataflow.message.return.null.from.notnull", presentable)
                 : JavaAnalysisBundle.message("dataflow.message.return.nullable.from.notnull", presentable);
        }
        List<LocalQuickFix> fixes = createNPEFixes(state, expr, expr, exactlyNull);
        PsiMethod surroundingMethod = PsiTreeUtil.getParentOfType(anchor, PsiMethod.class, true, PsiLambdaExpression.class);
        if (surroundingMethod != null) {
          LocalQuickFix fix = AddAnnotationPsiFix.createAddNullableFix(surroundingMethod);
          if (fix != null) fixes = StreamEx.of(fixes).append(fix).toList();
        }
        reporter.registerProblem(expr, text, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
      else {
        final String defaultNullable = manager.getDefaultAnnotation(Nullability.NULLABLE, anchor);
        if (AnnotationUtil.isAnnotatingApplicable(anchor, defaultNullable)) {
          final String presentableNullable = StringUtil.getShortName(defaultNullable);
          final String text = exactlyNull
                              ? JavaAnalysisBundle.message("dataflow.message.return.null.from.notnullable", presentableNullable)
                              : JavaAnalysisBundle.message("dataflow.message.return.nullable.from.notnullable", presentableNullable);
          PsiMethod surroundingMethod = PsiTreeUtil.getParentOfType(anchor, PsiMethod.class, true, PsiLambdaExpression.class);
          final LocalQuickFix fix = surroundingMethod == null ? null :
                                    AddAnnotationPsiFix.createAddNullableFix(surroundingMethod);
          reporter.registerProblem(expr, text, LocalQuickFix.notNullElements(fix));
        }
      }
    }
  }

  private ParametricNullableBoundChecker.ParametricNullableBoundOptions parametricOptions(DataFlowInspectionStateBase state) {
    return new ParametricNullableBoundChecker.ParametricNullableBoundOptions(state.REPORT_UNSPECIFIED_PARAMETRIC_NULLNESS, state.IGNORE_ASSERT_STATEMENTS);
  }

  /**
   * Reports {@code null} assignments to fields whose type is a type variable that may be instantiated as non-null
   * (parametric nullness).
   */
  private void reportParametricAssignmentProblems(DataFlowInspectionStateBase state, ProblemReporter reporter, List<NullabilityProblem<?>> problems) {
    for (NullabilityProblem<?> problem : problems) {
      PsiExpression expression = problem.getDereferencedExpression();
      if (expression == null) continue;
      boolean alwaysNull = problem.isAlwaysNull(state.IGNORE_ASSERT_STATEMENTS);
      if (!state.REPORT_UNSOUND_WARNINGS && !alwaysNull) continue;
      LocalQuickFix[] fixes = createNPEFixes(state, expression, expression, alwaysNull).toArray(LocalQuickFix.EMPTY_ARRAY);
      reporter.registerProblem(expression, JavaAnalysisBundle.message("dataflow.message.assigning.null.parametric"), fixes);
    }
  }

  @Override
  public LocalizeValue getGroupDisplayName() {
    return InspectionLocalize.groupNamesProbableBugs();
  }

  @Override
  public String getShortName() {
    return SHORT_NAME;
  }

  /**
   * @param switchBlock switch statement/expression to check
   * @return a set of label elements that are duplicates. If a switch block contains patterns,
   * then dominated label elements will be also included in the result set.
   */
  private static Set<PsiElement> findSuspiciousLabelElements(PsiSwitchBlock switchBlock) {
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return Collections.emptySet();
    PsiType selectorType = selector.getType();
    if (selectorType == null) return Collections.emptySet();
    List<PsiCaseLabelElement> labelElements =
      ContainerUtil.filterIsInstance(JavaPsiSwitchUtil.getSwitchBranches(switchBlock), PsiCaseLabelElement.class);
    if (labelElements.isEmpty()) return Collections.emptySet();
    MultiMap<Object, PsiElement> duplicateCandidates = JavaPsiSwitchUtil.getValuesAndLabels(switchBlock);

    Set<PsiElement> result = new SmartHashSet<>();

    for (Map.Entry<Object, Collection<PsiElement>> entry : duplicateCandidates.entrySet()) {
      if (entry.getValue().size() <= 1) continue;
      result.addAll(entry.getValue());
    }

    // Find only one unconditional pattern, but not all, because if there are
    // multiple unconditional patterns, they will all be found as duplicates
    PsiCaseLabelElement unconditionalPattern = ContainerUtil.find(
      labelElements, element -> JavaPsiPatternUtil.isUnconditionalForType(element, selectorType));
    PsiElement defaultElement = JavaPsiSwitchUtil.findDefaultElement(switchBlock);
    if (unconditionalPattern != null && defaultElement != null) {
      result.add(unconditionalPattern);
      result.add(defaultElement);
    }

    return StreamEx.ofKeys(JavaPsiSwitchUtil.findDominatedLabels(switchBlock), value -> value instanceof PsiPattern)
      .into(result);
  }

  /**
   * {@link ProblemsHolder} wrapper to avoid reporting two problems on the same anchor
   */
  protected static class ProblemReporter {
    private final Set<PsiElement> myReportedAnchors = new HashSet<>();
    private final ProblemsHolder myHolder;
    private final PsiElement myScope;
    private final boolean myDebug;

    ProblemReporter(ProblemsHolder holder, PsiElement scope) {
      myHolder = holder;
      myScope = scope;
      Application application = ApplicationManager.getApplication();
      myDebug = (application.isInternal() && application.isInternal()) || application.isUnitTestMode();
    }

    public void registerProblem(PsiElement element, String message, LocalQuickFix ... fixes) {
      if (register(element)) {
        myHolder.registerProblem(element, message, fixes);
      }
    }

    private boolean register(PsiElement element) {
      if (!element.isPhysical()) {
        String message = "Non-physical element in DataFlow problem reporting: " + element;
        LOG.info(message);
        if (myDebug) {
          throw new IllegalStateException(message);
        }
        return false;
      }
      // Suppress reporting for inlined simple methods
      if (!PsiTreeUtil.isAncestor(myScope, element, false)) return false;
      if (myScope instanceof PsiClass) {
        PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
        if (member instanceof PsiMethod && !((PsiMethod)member).isConstructor()) return false;
      }
      if (!myReportedAnchors.add(element)) return false;
      if (element instanceof PsiParenthesizedExpression) {
        PsiExpression deparenthesized = PsiUtil.skipParenthesizedExprDown((PsiExpression)element);
        if (deparenthesized != null) {
          myReportedAnchors.add(deparenthesized);
        }
      }
      return true;
    }
  }
}
