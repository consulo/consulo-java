package com.intellij.java.impl.codeInsight.hints;

import com.intellij.java.impl.codeInsight.completion.CompletionMemory;
import com.intellij.java.impl.codeInsight.completion.JavaMethodCallElement;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiEmptyExpressionImpl;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import consulo.application.util.registry.Registry;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.editor.inlay.HintWidthAdjustment;
import consulo.language.editor.inlay.InlayInfo;
import consulo.language.editor.inlay.InlayParameterHintsProvider;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.ResolveResult;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class JavaHintUtils {
    private static final CallMatcher OPTIONAL_EMPTY = CallMatcher.staticCall(
        CommonClassNames.JAVA_UTIL_OPTIONAL, "empty").parameterCount(0);

    private JavaHintUtils() {
    }

    public static List<InlayInfo> hints(PsiCall callExpression) {
        if (JavaMethodCallElement.isCompletionMode(callExpression)) {
            PsiExpressionList argumentList = callExpression.getArgumentList();
            if (argumentList == null) {
                return List.of();
            }
            String text = argumentList.getText();
            if (text == null || !text.startsWith("(") || !text.endsWith(")")) {
                return List.of();
            }

            PsiMethod method = CompletionMemory.getChosenMethod(callExpression);
            if (method == null) {
                return List.of();
            }

            PsiParameter[] params = method.getParameterList().getParameters();
            PsiExpression[] arguments = argumentList.getExpressions();
            int limit = JavaMethodCallElement.getCompletionHintsLimit();
            int trailingOffset = argumentList.getTextRange().getEndOffset() - 1;

            List<InlayInfo> infos = new ArrayList<>();
            int lastIndex = 0;
            List<Integer> offsets = arguments.length == 0
                ? Collections.singletonList(trailingOffset)
                : Arrays.stream(arguments).map(JavaHintUtils::inlayOffset).collect(Collectors.toList());
            for (int i = 0; i < offsets.size() && i < params.length; i++) {
                String name = params[i].getName();
                infos.add(new InlayInfo(name, offsets.get(i), false, params.length == 1, false));
                lastIndex = i;
            }
            if (Registry.is("editor.completion.hints.virtual.comma", true)) {
                for (int i = lastIndex + 1; i < Math.min(params.length, limit); i++) {
                    String name = params[i].getName();
                    infos.add(createHintWithComma(name, trailingOffset));
                    lastIndex = i;
                }
            }
            if (method.isVarArgs() && ((arguments.length == 0 && params.length == 2)
                || (arguments.length > 0 && arguments.length == params.length - 1))) {
                String name = params[params.length - 1].getName();
                infos.add(createHintWithComma(name, trailingOffset));
            }
            else if ((Registry.is("editor.completion.hints.virtual.comma", true) && lastIndex < params.length - 1)
                || (limit == 1 && arguments.length == 0 && params.length > 1)
                || (limit <= arguments.length && arguments.length < params.length)) {
                infos.add(new InlayInfo("...more", trailingOffset, false, false, true));
            }
            return new ArrayList<>(new HashSet<>(infos));
        }

        if (!InlayParameterHintsProvider.forLanguage(JavaLanguage.INSTANCE).canShowHintsWhenDisabled()) {
            return List.of();
        }

        ResolveResult[] results = callExpression.multiResolve(false);

        Set<InlayInfo> hints = methodHints(callExpression, results.length > 0 ? results[0] : null);
        if (!hints.isEmpty()) {
            return new ArrayList<>(hints);
        }
        if (callExpression instanceof PsiMethodCallExpression || callExpression instanceof PsiNewExpression) {
            return new ArrayList<>(mergedHints((PsiCallExpression) callExpression, results));
        }
        return List.of();
    }

    private static InlayInfo createHintWithComma(String parameterName, int offset) {
        return new InlayInfo(
            "," + parameterName,
            offset,
            false,
            false,
            true,
            new HintWidthAdjustment(", ", parameterName, 1)
        );
    }

    private static Set<InlayInfo> mergedHints(
        PsiCallExpression callExpression,
        ResolveResult[] results
    ) {
        List<Set<InlayInfo>> resultList = Arrays.stream(results)
            .filter(r -> r.getElement() != null)
            .map(r -> methodHints(callExpression, r))
            .collect(Collectors.toList());
        if (resultList.isEmpty()) {
            return Collections.emptySet();
        }
        if (resultList.size() == 1) {
            return resultList.get(0);
        }

        PsiMethod chosen = CompletionMemory.getChosenMethod(callExpression);
        if (chosen != null) {
            CallInfo info = callInfo(callExpression, chosen);
            return hintSet(info, PsiSubstitutor.EMPTY);
        }

        return resultList.stream()
            .reduce((l, r) -> {
                Set<InlayInfo> inter = new HashSet<>(l);
                inter.retainAll(r);
                return inter;
            })
            .orElse(Collections.emptySet())
            .stream()
            .map(i -> new InlayInfo(i.getText(), i.getOffset(), true))
            .collect(Collectors.toSet());
    }

    private static Set<InlayInfo> methodHints(
        PsiCall callExpression,
        ResolveResult resolveResult
    ) {
        if (!(resolveResult instanceof JavaResolveResult)) {
            return Collections.emptySet();
        }
        PsiElement element = resolveResult.getElement();
        PsiSubstitutor substitutor = ((JavaResolveResult) resolveResult).getSubstitutor();
        if (element instanceof PsiMethod && isMethodToShow((PsiMethod) element)) {
            CallInfo info = callInfo(callExpression, (PsiMethod) element);
            if (isCallInfoToShow(info)) {
                return hintSet(info, substitutor);
            }
        }
        return Collections.emptySet();
    }

    private static boolean isCallInfoToShow(CallInfo info) {
        JavaInlayParameterHintsProvider provider =
            (JavaInlayParameterHintsProvider) InlayParameterHintsProvider.forLanguage(JavaLanguage.INSTANCE);

        return provider.showIfMethodNameContainsParameterName.get() || !info.allParamsSequential();
    }

    private static Pair<String, Integer> decomposeOrderedParams(String text) {
        int firstDigit = -1;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                firstDigit = i;
                break;
            }
        }
        if (firstDigit < 0) {
            return null;
        }
        String prefix = text.substring(0, firstDigit);
        try {
            int number = Integer.parseInt(text.substring(firstDigit));
            return Pair.create(prefix, number);
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    private static Set<InlayInfo> hintSet(CallInfo info, PsiSubstitutor substitutor) {
        Set<InlayInfo> result = new LinkedHashSet<>();
        InlayInfo vararg = info.varargsInlay(substitutor);
        if (vararg != null) {
            result.add(vararg);
        }
        JavaInlayParameterHintsProvider provider =
            (JavaInlayParameterHintsProvider) InlayParameterHintsProvider.forLanguage(JavaLanguage.INSTANCE);
        if (provider.showForParamsWithSameType.get()) {
            result.addAll(info.sameTypeInlays());
        }
        result.addAll(info.unclearInlays(substitutor));
        return result;
    }

    private static boolean isMethodToShow(PsiMethod method) {
        PsiParameter[] params = method.getParameterList().getParameters();
        if (params.length == 0) {
            return false;
        }
        if (params.length == 1) {
            JavaInlayParameterHintsProvider provider =
                (JavaInlayParameterHintsProvider) InlayParameterHintsProvider.forLanguage(JavaLanguage.INSTANCE);

            if (!provider.showIfMethodNameContainsParameterName.get()
                && isParamNameContainedInMethodName(params[0], method)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isParamNameContainedInMethodName(PsiParameter parameter, PsiMethod method) {
        String paramName = parameter.getName();
        if (paramName.length() > 1) {
            return method.getName().toLowerCase(Locale.getDefault()).contains(paramName.toLowerCase(Locale.getDefault()));
        }
        return false;
    }

    private static CallInfo callInfo(PsiCall callExpression, PsiMethod method) {
        PsiParameter[] params = method.getParameterList().getParameters();
        boolean hasVarArg = params.length > 0 && params[params.length - 1].isVarArgs();
        int regularCount = hasVarArg ? params.length - 1 : params.length;
        PsiExpression[] expressions = callExpression.getArgumentList() != null
            ? callExpression.getArgumentList().getExpressions()
            : PsiExpression.EMPTY_ARRAY;

        List<CallArgumentInfo> regular = new ArrayList<>();
        for (int i = 0; i < regularCount && i < expressions.length; i++) {
            regular.add(new CallArgumentInfo(params[i], expressions[i]));
        }
        List<PsiExpression> varargs = hasVarArg && expressions.length > regularCount
            ? Arrays.asList(Arrays.copyOfRange(expressions, regularCount, expressions.length))
            : Collections.emptyList();
        PsiParameter varParam = hasVarArg ? params[params.length - 1] : null;
        return new CallInfo(regular, varParam, varargs);
    }

    private static int inlayOffset(PsiExpression expr) {
        return inlayOffset(expr, false);
    }

    private static int inlayOffset(PsiExpression expr, boolean atEnd) {
        TextRange range = expr.getTextRange();
        if (range.isEmpty()) {
            PsiElement next = expr.getNextSibling();
            if (next instanceof PsiWhiteSpace) {
                return next.getTextRange().getEndOffset();
            }
        }
        return atEnd ? expr.getTextRange().getEndOffset() : expr.getTextRange().getStartOffset();
    }

    private static boolean areSequential(List<Integer> list) {
        if (list.isEmpty()) {
            throw new IncorrectOperationException("List is empty");
        }
        int first = list.get(0);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) != first + i) {
                return false;
            }
        }
        return true;
    }

    private static InlayInfo inlayInfo(CallArgumentInfo info) {
        return inlayInfo(info.argument, info.parameter, false);
    }

    private static InlayInfo inlayInfo(
        PsiExpression argument,
        PsiParameter parameter,
        boolean showOnlyIfExistedBefore
    ) {
        String name = parameter.getName();
        String prefix = parameter.getType() instanceof PsiEllipsisType ? "..." : "";
        return new InlayInfo(prefix + name, inlayOffset(argument), showOnlyIfExistedBefore);
    }

    private static boolean shouldShowHintsForExpression(PsiElement e) {
        JavaInlayParameterHintsProvider provider =
            (JavaInlayParameterHintsProvider) InlayParameterHintsProvider.forLanguage(JavaLanguage.INSTANCE);

        if (provider.isShowHintWhenExpressionTypeIsClear.get()) {
            return true;
        }
        
        if (e instanceof PsiLiteralExpression) {
            return true;
        }
        if (e instanceof PsiThisExpression) {
            return true;
        }
        if (e instanceof PsiBinaryExpression || e instanceof PsiPolyadicExpression) {
            return true;
        }
        if (e instanceof PsiPrefixExpression) {
            PsiExpression operand = ((PsiPrefixExpression) e).getOperand();
            boolean isLiteral = operand instanceof PsiLiteralExpression;
            IElementType token = ((PsiPrefixExpression) e).getOperationTokenType();
            return isLiteral && (token == JavaTokenType.MINUS || token == JavaTokenType.PLUS);
        }
        if (e instanceof PsiMethodCallExpression && OPTIONAL_EMPTY.matches((PsiMethodCallExpression) e)) {
            return true;
        }
        return false;
    }

    private static class CallInfo {
        final List<CallArgumentInfo> regularArgs;
        final PsiParameter varArg;
        final List<PsiExpression> varArgExpressions;

        CallInfo(List<CallArgumentInfo> regularArgs, PsiParameter varArg, List<PsiExpression> varArgExpressions) {
            this.regularArgs = regularArgs;
            this.varArg = varArg;
            this.varArgExpressions = varArgExpressions;
        }

        List<InlayInfo> unclearInlays(PsiSubstitutor substitutor) {
            List<InlayInfo> list = new ArrayList<>();
            for (CallArgumentInfo arg : regularArgs) {
                InlayInfo inlay = null;
                if (!shouldHideArgument(arg)) {
                    if (shouldShowHintsForExpression(arg.argument)) {
                        inlay = inlayInfo(arg);
                    }
                    else if (!arg.isAssignable(substitutor)) {
                        inlay = new InlayInfo(arg.parameter.getName(), inlayOffset(arg.argument), true);
                    }
                }
                if (inlay != null) {
                    list.add(inlay);
                }
            }
            return list;
        }

        List<InlayInfo> sameTypeInlays() {
            List<String> types = regularArgs.stream().map(a -> a.parameter.getType().getCanonicalText()).collect(Collectors.toList());
            List<String> duplicated = new ArrayList<>(types);
            types.stream().distinct().forEach(duplicated::remove);
            return regularArgs.stream()
                .filter(a -> !shouldHideArgument(a))
                .filter(a -> duplicated.contains(a.parameter.getType().getCanonicalText())
                    && !a.argument.getText().equals(a.parameter.getName()))
                .map(CallArgumentInfo::toInlay)
                .collect(Collectors.toList());
        }

        private boolean shouldHideArgument(CallArgumentInfo arg) {
            return arg.argument instanceof PsiEmptyExpressionImpl
                || arg.argument.getPrevSibling() instanceof PsiEmptyExpressionImpl
                || hasComment(arg.argument, PsiElement::getNextSibling)
                || hasComment(arg.argument, PsiElement::getPrevSibling)
                || isNamedArgSame(arg);
        }

        private boolean isNamedArgSame(CallArgumentInfo arg) {
            String argName = null;
            PsiExpression expr = arg.argument;
            if (expr instanceof PsiReferenceExpression) {
                argName = ((PsiReferenceExpression) expr).getReferenceName();
            }
            else if (expr instanceof PsiMethodCallExpression) {
                argName = ((PsiMethodCallExpression) expr).getMethodExpression().getReferenceName();
            }
            if (argName == null) {
                return false;
            }
            argName = argName.toLowerCase(Locale.getDefault());
            String param = arg.parameter.getName().toLowerCase(Locale.getDefault());
            if (param.length() < 3 || argName.length() < 3) {
                return false;
            }
            return argName.contains(param) || param.contains(argName);
        }

        private boolean hasComment(PsiElement e, Function<PsiElement, PsiElement> next) {
            PsiElement cur = next.apply(e);
            while (cur != null) {
                if (cur instanceof PsiComment) {
                    return true;
                }
                if (!(cur instanceof PsiWhiteSpace)) {
                    break;
                }
                cur = next.apply(cur);
            }
            return false;
        }

        InlayInfo varargsInlay(PsiSubstitutor substitutor) {
            if (varArg == null) {
                return null;
            }
            boolean nonAssignable = false;
            for (PsiExpression expr : varArgExpressions) {
                if (shouldShowHintsForExpression(expr)) {
                    return inlayInfo(expr, varArg, false);
                }

                if (!isAssignable(varArg, expr, substitutor)) {
                    nonAssignable = true;
                }
            }
            return nonAssignable ? inlayInfo(varArgExpressions.get(0), varArg, true) : null;
        }

        boolean allParamsSequential() {
            List<Integer> nums = regularArgs.stream()
                .map(a -> a.parameter.getName())
                .map(JavaHintUtils::decomposeOrderedParams)
                .filter(Objects::nonNull)
                .map(p -> p.getSecond())
                .toList();

            return nums.size() > 1 && nums.size() == regularArgs.size() && areSequential(nums);
        }
    }

    private static class CallArgumentInfo {
        final PsiParameter parameter;
        final PsiExpression argument;

        CallArgumentInfo(PsiParameter parameter, PsiExpression argument) {
            this.parameter = parameter;
            this.argument = argument;
        }

        boolean isAssignable(PsiSubstitutor substitutor) {
            return JavaHintUtils.isAssignable(parameter, argument, substitutor);
        }

        InlayInfo toInlay() {
            return inlayInfo(argument, parameter, false);
        }
    }

    static boolean isAssignable(@Nonnull PsiParameter parameter,
                                @Nonnull PsiExpression argument,
                                @Nonnull PsiSubstitutor substitutor) {
        // Substitute the parameter’s declared type
        PsiType substitutedType = substitutor.substitute(parameter.getType());
        if (substitutedType == null) {
            return false;
        }
        // Treat poly expressions (e.g. lambdas) as assignable
        if (PsiPolyExpressionUtil.isPolyExpression(argument)) {
            return true;
        }
        // Otherwise compare the actual argument type against the substituted parameter type
        PsiType argType = argument.getType();
        return argType != null && TypeConversionUtil.isAssignable(substitutedType, argType);
    }
}
