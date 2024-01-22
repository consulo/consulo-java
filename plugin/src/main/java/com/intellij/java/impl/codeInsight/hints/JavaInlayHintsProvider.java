package com.intellij.java.impl.codeInsight.hints;

import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiNewExpressionImpl;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.ide.impl.idea.codeInsight.dataflow.SetUtil;
import consulo.ide.impl.idea.codeInsight.hints.settings.ParameterNameHintsSettings;
import consulo.language.ast.IElementType;
import consulo.language.editor.inlay.InlayInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.ResolveResult;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.JBIterable;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * from kotlin
 */
public class JavaInlayHintsProvider {
  @jakarta.annotation.Nonnull
  @RequiredReadAction
  public static Set<InlayInfo> createHints(PsiCallExpression callExpression) {
    JavaResolveResult resolveResult = callExpression.resolveMethodGenerics();
    Set<InlayInfo> hints = createHintsForResolvedMethod(callExpression, resolveResult);

    if (!hints.isEmpty()) {
      return hints;
    }

    if (callExpression instanceof PsiMethodCallExpression) {
      createMergedHints(callExpression, ((PsiMethodCallExpression) callExpression).getMethodExpression().multiResolve(false));
    }

    if (callExpression instanceof PsiNewExpressionImpl) {
      return createMergedHints(callExpression, ((PsiNewExpressionImpl) callExpression).getConstructorFakeReference().multiResolve(false));
    }
    return Collections.emptySet();
  }

  @RequiredReadAction
  private static Set<InlayInfo> createMergedHints(PsiCallExpression callExpression, ResolveResult[] results) {
    List<Set<InlayInfo>> resultSet = Arrays.stream(results).map(it -> it.getElement() == null ? null : createHintsForResolvedMethod(callExpression, it)).filter(Objects::nonNull).collect
        (Collectors.toList());

    if (resultSet.isEmpty()) {
      return Collections.emptySet();
    }
    for (Set<InlayInfo> inlayInfos : resultSet) {
      if (inlayInfos.isEmpty()) {
        return Collections.emptySet();
      }
    }

    return JBIterable.from(resultSet).reduce(null, (left, right) -> SetUtil.intersect(left, right));
  }

  @RequiredReadAction
  @jakarta.annotation.Nonnull
  private static Set<InlayInfo> createHintsForResolvedMethod(PsiCallExpression callExpression, ResolveResult resolveResult) {
    PsiElement element = resolveResult.getElement();
    PsiSubstitutor substitutor = resolveResult instanceof JavaResolveResult ? ((JavaResolveResult) resolveResult).getSubstitutor() : PsiSubstitutor.EMPTY;

    if (element instanceof PsiMethod && isMethodToShow((PsiMethod) element, callExpression)) {
      CallInfo info = getCallInfo(callExpression, (PsiMethod) element);
      return createHintSet(info, substitutor);
    }

    return Collections.emptySet();
  }

  @RequiredReadAction
  @Nonnull
  private static Set<InlayInfo> createHintSet(CallInfo info, PsiSubstitutor substitutor) {
    List<CallArgumentInfo> args = info.getRegularArgs().stream().filter(it -> isAssignable(it, substitutor)).collect(Collectors.toList());

    Set<InlayInfo> resultSet = new HashSet<>();

    ContainerUtil.addIfNotNull(resultSet, getVarArgInlay(info));
    if (ParameterNameHintsSettings.getInstance().isShowForParamsWithSameType()) {
      resultSet.addAll(createSameTypeInlays(args));
    }

    resultSet.addAll(createUnclearInlays(args));

    return resultSet;
  }

  @RequiredReadAction
  private static List<InlayInfo> createUnclearInlays(List<CallArgumentInfo> args) {
    return ContainerUtil.mapNotNull(ContainerUtil.filter(args, it -> isUnclearExpression(it.getArgument())), it -> createInlayInfo(it.getArgument(), it.getParameter()));
  }

  @Nullable
  @RequiredReadAction
  private static InlayInfo getVarArgInlay(CallInfo info) {
    if (info.getVarArg() == null || info.getVarArgExpressions().isEmpty()) {
      return null;
    }
    boolean hasUnclearExpressions = ContainerUtil.find(info.getVarArgExpressions(), JavaInlayHintsProvider::isUnclearExpression) != null;
    if (hasUnclearExpressions) {
      return createInlayInfo(ContainerUtil.getFirstItem(info.getVarArgExpressions()), info.getVarArg());
    }
    return null;
  }

  private static boolean isAssignable(CallArgumentInfo callArgumentInfo, PsiSubstitutor substitutor) {
    PsiType substitutedType = substitutor.substitute(callArgumentInfo.getParameter().getType());
    if (PsiPolyExpressionUtil.isPolyExpression(callArgumentInfo.getArgument())) {
      return true;
    }
    PsiType type = callArgumentInfo.getArgument().getType();
    if (type == null) {
      return false;
    }
    return isAssignableTo(type, substitutedType);
  }

  private static boolean isAssignableTo(PsiType thisType, PsiType parameterType) {
    return TypeConversionUtil.isAssignable(parameterType, thisType);
  }

  @RequiredReadAction
  private static boolean isMethodToShow(PsiMethod method, PsiCallExpression callExpression) {
    PsiParameter[] params = method.getParameterList().getParameters();
    if (params.length == 0) {
      return false;
    }
    if (params.length == 1) {
      if (isBuilderLike(callExpression, method) || isSetterNamed(method)) {
        return false;
      }
      if (ParameterNameHintsSettings.getInstance().isDoNotShowIfMethodNameContainsParameterName() && isParamNameContainedInMethodName(params[0], method)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isBuilderLike(PsiCallExpression expression, PsiMethod method) {
    if (expression instanceof PsiNewExpression) {
      return false;
    }

    PsiType returnType = TypeConversionUtil.erasure(method.getReturnType());
    if (returnType == null) {
      return false;
    }
    String calledMethodClassFqn = method.getContainingClass() == null ? null : method.getContainingClass().getQualifiedName();
    if (calledMethodClassFqn == null) {
      return false;
    }

    return returnType.equalsToText(calledMethodClassFqn);
  }

  private static List<InlayInfo> createSameTypeInlays(List<CallArgumentInfo> args) {
    List<String> all = ContainerUtil.map(args, it -> typeText(it.getParameter()));
    List<String> duplicated = new ArrayList<>(all);

    new HashSet<>(all).forEach(duplicated::remove);

    return ContainerUtil.mapNotNull(ContainerUtil.filter(args, it -> duplicated.contains(typeText(it.getParameter()))), it -> createInlayInfo(it.getArgument(), it.getParameter()));
  }

  private static boolean isSetterNamed(PsiMethod method) {
    String methodName = method.getName();
    if (methodName.startsWith("set") && (methodName.length() == 3 || methodName.length() > 3 && Character.isUpperCase(methodName.charAt(3)))) {
      return true;
    }
    return false;
  }

  private static String typeText(@jakarta.annotation.Nonnull PsiParameter psiParameter) {
    return psiParameter.getType().getCanonicalText();
  }

  @RequiredReadAction
  private static boolean isParamNameContainedInMethodName(PsiParameter parameter, PsiMethod method) {
    String parameterName = parameter.getName();
    if (parameterName == null) {
      return false;
    }
    if (parameterName.length() > 1) {
      return StringUtil.containsIgnoreCase(method.getName(), parameterName);
    }
    return false;
  }

  @Nullable
  @RequiredReadAction
  private static InlayInfo createInlayInfo(PsiExpression callArgument, PsiParameter methodParam) {
    String paramName = methodParam.getName();
    if (paramName == null) {
      return null;
    }

    String paramToShow = (methodParam.getType() instanceof PsiEllipsisType ? "..." : "") + paramName;
    return new InlayInfo(paramToShow, callArgument.getTextRange().getStartOffset());
  }

  private static CallInfo getCallInfo(PsiCallExpression callExpression, PsiMethod method) {
    PsiParameter[] params = method.getParameterList().getParameters();
    boolean hasVarArg = ArrayUtil.getLastElement(params) != null && ArrayUtil.getLastElement(params).isVarArgs();
    int regularParamsCount = hasVarArg ? params.length - 1 : params.length;

    PsiExpression[] arguments = callExpression.getArgumentList() == null ? PsiExpression.EMPTY_ARRAY : callExpression.getArgumentList().getExpressions();

    List<PsiParameter> collect = Arrays.stream(params).limit(regularParamsCount).collect(Collectors.toList());

    List<CallArgumentInfo> regularArgInfos = new ArrayList<>(regularParamsCount);
    ContainerUtil.zip(collect, Arrays.asList(arguments)).forEach(pair ->
    {
      regularArgInfos.add(new CallArgumentInfo(pair.getFirst(), pair.getSecond()));
    });

    PsiParameter varargParam = hasVarArg ? ArrayUtil.getLastElement(params) : null;
    List<PsiExpression> varargExpressions = Arrays.stream(arguments).skip(regularParamsCount).collect(Collectors.toList());
    return new CallInfo(regularArgInfos, varargParam, varargExpressions);
  }

  private static boolean isUnclearExpression(PsiElement callArgument) {
    if (callArgument instanceof PsiLiteralExpression || callArgument instanceof PsiThisExpression || callArgument instanceof PsiBinaryExpression || callArgument instanceof PsiPolyadicExpression) {
      return true;
    }
    if (callArgument instanceof PsiPrefixExpression) {
      IElementType tokenType = ((PsiPrefixExpression) callArgument).getOperationTokenType();
      boolean isLiteral = ((PsiPrefixExpression) callArgument).getOperand() instanceof PsiLiteralExpression;
      return isLiteral && (JavaTokenType.MINUS == tokenType || JavaTokenType.PLUS == tokenType);
    }
    return false;
  }

}
