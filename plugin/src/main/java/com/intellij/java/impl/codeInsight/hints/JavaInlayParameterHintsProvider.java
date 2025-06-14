package com.intellij.java.impl.codeInsight.hints;

import com.intellij.java.impl.codeInsight.completion.CompletionMemory;
import com.intellij.java.impl.codeInsight.completion.JavaMethodCallElement;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.impl.JavaBundle;
import consulo.java.localize.JavaLocalize;
import consulo.language.Language;
import consulo.language.editor.inlay.HintInfo;
import consulo.language.editor.inlay.InlayInfo;
import consulo.language.editor.inlay.InlayParameterHintsProvider;
import consulo.language.editor.inlay.Option;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Java-specific implementation of InlayParameterHintsProvider.
 */
@ExtensionImpl
public class JavaInlayParameterHintsProvider implements InlayParameterHintsProvider {
    private final Set<String> defaultBlackList = Set.of(
        "(begin*, end*)",
        "(start*, end*)",
        "(first*, last*)",
        "(first*, second*)",
        "(from*, to*)",
        "(min*, max*)",
        "(key, value)",
        "(format, arg*)",
        "(message)",
        "(message, error)",
        "*Exception",
        "*.set*(*)",
        "*.add(*)",
        "*.set(*,*)",
        "*.get(*)",
        "*.create(*)",
        "*.getProperty(*)",
        "*.setProperty(*,*)",
        "*.print(*)",
        "*.println(*)",
        "*.append(*)",
        "*.charAt(*)",
        "*.indexOf(*)",
        "*.contains(*)",
        "*.startsWith(*)",
        "*.endsWith(*)",
        "*.equals(*)",
        "*.equal(*)",
        "*.compareTo(*)",
        "*.compare(*,*)",
        "java.lang.Math.*",
        "org.slf4j.Logger.*",
        "*.singleton(*)",
        "*.singletonList(*)",
        "*.Set.of",
        "*.ImmutableList.of",
        "*.ImmutableMultiset.of",
        "*.ImmutableSortedMultiset.of",
        "*.ImmutableSortedSet.of",
        "*.Arrays.asList"
    );

    public final Option showIfMethodNameContainsParameterName = new Option(
        "java.method.name.contains.parameter.name",
        JavaLocalize.settingsInlayJavaParametersWithNamesThatAreContainedInTheMethodName(),
        false
    );

    public final Option showForParamsWithSameType = new Option(
        "java.multiple.params.same.type",
        JavaLocalize.settingsInlayJavaNonLiteralsInCaseOfMultipleParametersWithTheSameType(),
        false
    );

    public final Option showForBuilderLikeMethods = new Option(
        "java.build.like.method",
        JavaLocalize.settingsInlayJavaBuilderLikeMethods(),
        false
    );

    private final Option ignoreOneCharOneDigitHints = new Option(
        "java.simple.sequentially.numbered",
        JavaLocalize.settingsInlayJavaMethodsWithSameNamedNumberedParameters(),
        false
    );

    public final Option isShowHintWhenExpressionTypeIsClear = new Option(
        "java.clear.expression.type",
        JavaLocalize.settingsInlayJavaComplexExpressionsBinaryFunctionalArrayAccessAndOther(),
        false,
        JavaLocalize.settingsInlayJavaShowParameterHintsWhenExpressionTypeIsClearDescription()
    );

    private final Option isShowHintsForEnumConstants = new Option(
        "java.enums",
        JavaLocalize.settingsInlayJavaEnumConstants(),
        true
    );

    private final Option isShowHintsForNewExpressions = new Option(
        "java.new.expr",
        JavaLocalize.settingsInlayJavaNewExpressions(),
        true
    );

    @Override
    public HintInfo.MethodInfo getHintInfo(@Nonnull PsiElement element) {
        if (element instanceof PsiCallExpression callExpression && !(element instanceof PsiEnumConstant)) {
            PsiMethod resolved = null;

            if (JavaMethodCallElement.isCompletionMode(callExpression)) {
                resolved = CompletionMemory.getChosenMethod(callExpression);
            }

            if (resolved == null) {
                resolved = (PsiMethod) callExpression.resolveMethodGenerics().getElement();
            }

            if (resolved != null) {
                String className = resolved.getContainingClass() != null
                    ? resolved.getContainingClass().getQualifiedName()
                    : null;
                if (className != null) {
                    String fullName = StringUtil.getQualifiedName(className, resolved.getName());
                    List<String> names = List.of(resolved.getParameterList().getParameters()).stream()
                        .map(PsiParameter::getName)
                        .collect(Collectors.toList());
                    return new HintInfo.MethodInfo(fullName, names);
                }
            }
        }
        return null;
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }

    @Nonnull
    @Override
    public List<InlayInfo> getParameterHints(@Nonnull PsiElement element) {
        if (element instanceof PsiCall) {
            if (element instanceof PsiEnumConstant && !isShowHintsForEnumConstants.get()) {
                return Collections.emptyList();
            }
            if (element instanceof PsiNewExpression && !isShowHintsForNewExpressions.get()) {
                return Collections.emptyList();
            }
            return JavaHintUtils.hints((PsiCall) element);
        }
        return Collections.emptyList();
    }

    @Override
    public boolean canShowHintsWhenDisabled() {
        return true;
    }

    @Nonnull
    @Override
    public Set<String> getDefaultBlackList() {
        return defaultBlackList;
    }

    @Nonnull
    @Override
    public List<Option> getSupportedOptions() {
        return List.of(
            showIfMethodNameContainsParameterName,
            showForParamsWithSameType,
            ignoreOneCharOneDigitHints,
            isShowHintsForEnumConstants,
            isShowHintsForNewExpressions,
            isShowHintWhenExpressionTypeIsClear
        );
    }

    @Nonnull
    @Override
    public LocalizeValue getPreviewFileText() {
        return LocalizeValue.localizeTODO("class A {\n  native void foo(String name, boolean isChanged);\n  \n  void bar() {\n    foo(\"\", false);\n  }\n}");
    }

    @Override
    public boolean isExhaustive() {
        return true;
    }

    @Override
    public String getProperty(String key) {
        return JavaBundle.message(key);
    }
}
