// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.analysis.codeInsight.guess.GuessManager;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.LambdaHighlightingUtil;
import com.intellij.java.analysis.impl.codeInspection.java15api.Java15APIUsageInspection;
import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.java.impl.codeInsight.completion.scope.CompletionElement;
import com.intellij.java.impl.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.java.impl.codeInsight.lookup.*;
import com.intellij.java.impl.psi.util.proximity.ReferenceListWeigher;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.PsiTypeMapper;
import com.intellij.java.language.impl.psi.impl.light.LightVariableBuilder;
import com.intellij.java.language.impl.psi.impl.source.PsiImmediateClassType;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.impl.psi.scope.NameHint;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.patterns.PsiJavaPatterns;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.*;
import com.siyeh.ig.psiutils.SideEffectChecker;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.TabOutScopesTracker;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.RangeMarker;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import consulo.language.editor.AutoPopupController;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.*;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.impl.psi.FakePsiElement;
import consulo.language.psi.*;
import consulo.language.psi.filter.ElementFilter;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.style.StandardColors;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.JBIterable;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.NullableLazyKey;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Predicates;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static com.intellij.java.impl.codeInsight.completion.ReferenceExpressionCompletionContributor.findConstantsUsedInSwitch;
import static com.intellij.java.impl.psi.util.proximity.ReferenceListWeigher.ReferenceListApplicability.inapplicable;
import static consulo.language.pattern.PlatformPatterns.psiElement;

public class JavaCompletionUtil {
    public static final Key<Boolean> FORCE_SHOW_SIGNATURE_ATTR = Key.create("forceShowSignature");
    private static final Logger LOG = Logger.getInstance(JavaCompletionUtil.class);
    public static final Key<BiFunction<PsiExpression, CompletionParameters, PsiType>> DYNAMIC_TYPE_EVALUATOR =
        Key.create("DYNAMIC_TYPE_EVALUATOR");

    private static final Key<PsiType> QUALIFIER_TYPE_ATTR = Key.create("qualifierType"); // SmartPsiElementPointer to PsiType of "qualifier"
    static final NullableLazyKey<ExpectedTypeInfo[], CompletionLocation> EXPECTED_TYPES = NullableLazyKey.create(
        "expectedTypes",
        location -> {
            if (PsiJavaPatterns.psiElement().beforeLeaf(PsiJavaPatterns.psiElement().withText("."))
                .accepts(location.getCompletionParameters().getPosition())) {
                return ExpectedTypeInfo.EMPTY_ARRAY;
            }

            return JavaSmartCompletionContributor.getExpectedTypes(location.getCompletionParameters());
        }
    );

    public static final Key<Boolean> SUPER_METHOD_PARAMETERS = Key.create("SUPER_METHOD_PARAMETERS");

    @Nullable
    public static Set<PsiType> getExpectedTypes(CompletionParameters parameters) {
        PsiExpression expr = PsiTreeUtil.getContextOfType(parameters.getPosition(), PsiExpression.class, true);
        if (expr != null) {
            Set<PsiType> set = new HashSet<>();
            for (ExpectedTypeInfo expectedInfo : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
                set.add(expectedInfo.getType());
            }
            return set;
        }
        return null;
    }

    private static final Key<List<SmartPsiElementPointer<PsiMethod>>> ALL_METHODS_ATTRIBUTE = Key.create("allMethods");

    public static PsiType getQualifierType(LookupElement item) {
        return item.getUserData(QUALIFIER_TYPE_ATTR);
    }

    public static void completeVariableNameForRefactoring(
        Project project,
        Set<LookupElement> set,
        String prefix,
        PsiType varType,
        VariableKind varKind
    ) {
        CamelHumpMatcher camelHumpMatcher = new CamelHumpMatcher(prefix);
        JavaMemberNameCompletionContributor.completeVariableNameForRefactoring(
            project,
            set,
            camelHumpMatcher,
            varType,
            varKind,
            true,
            false
        );
    }

    public static void putAllMethods(LookupElement item, List<? extends PsiMethod> methods) {
        item.putUserData(
            ALL_METHODS_ATTRIBUTE,
            ContainerUtil.map(methods, method -> SmartPointerManager.getInstance(method.getProject()).createSmartPsiElementPointer(method))
        );
    }

    public static List<PsiMethod> getAllMethods(LookupElement item) {
        List<SmartPsiElementPointer<PsiMethod>> pointers = item.getUserData(ALL_METHODS_ATTRIBUTE);
        if (pointers == null) {
            return null;
        }

        return ContainerUtil.mapNotNull(pointers, pointer -> pointer.getElement());
    }

    public static String[] completeVariableNameForRefactoring(
        JavaCodeStyleManager codeStyleManager,
        @Nullable PsiType varType,
        VariableKind varKind,
        SuggestedNameInfo suggestedNameInfo
    ) {
        return JavaMemberNameCompletionContributor.completeVariableNameForRefactoring(
            codeStyleManager,
            new CamelHumpMatcher(""),
            varType,
            varKind,
            suggestedNameInfo,
            true,
            false
        );
    }

    public static boolean isInExcludedPackage(@Nonnull PsiMember member, boolean allowInstanceInnerClasses) {
        String name = PsiUtil.getMemberQualifiedName(member);
        if (name == null) {
            return false;
        }

        if (!member.isStatic()) {
            if (member instanceof PsiMethod || member instanceof PsiField) {
                return false;
            }
            if (allowInstanceInnerClasses && member instanceof PsiClass && member.getContainingClass() != null) {
                return false;
            }
        }

        return JavaProjectCodeInsightSettings.getSettings(member.getProject()).isExcluded(name);
    }

    @Nonnull
    public static <T extends PsiType> T originalize(@Nonnull T type) {
        if (!type.isValid()) {
            return type;
        }

        T result = new PsiTypeMapper() {
            private final Set<PsiClassType> myVisited = ContainerUtil.newIdentityTroveSet();

            @Override
            public PsiType visitClassType(PsiClassType classType) {
                if (!myVisited.add(classType)) {
                    return classType;
                }

                PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
                PsiClass psiClass = classResolveResult.getElement();
                PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
                if (psiClass == null) {
                    return classType;
                }

                return new PsiImmediateClassType(CompletionUtilCore.getOriginalOrSelf(psiClass), originalizeSubstitutor(substitutor));
            }

            private PsiSubstitutor originalizeSubstitutor(PsiSubstitutor substitutor) {
                PsiSubstitutor originalSubstitutor = PsiSubstitutor.EMPTY;
                for (Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
                    PsiType value = entry.getValue();
                    originalSubstitutor = originalSubstitutor.put(
                        CompletionUtilCore.getOriginalOrSelf(entry.getKey()),
                        value == null ? null : mapType(value)
                    );
                }
                return originalSubstitutor;
            }


            @Override
            public PsiType visitType(PsiType type) {
                return type;
            }
        }.mapType(type);
        if (result == null) {
            throw new AssertionError("Null result for type " + type + " of class " + type.getClass());
        }
        return result;
    }

    @Nullable
    public static List<? extends PsiElement> getAllPsiElements(LookupElement item) {
        List<PsiMethod> allMethods = getAllMethods(item);
        if (allMethods != null) {
            return allMethods;
        }
        return item.getObject() instanceof PsiElement element ? Collections.singletonList(element) : null;
    }

    @Nullable
    public static PsiType getLookupElementType(LookupElement element) {
        TypedLookupItem typed = element.as(TypedLookupItem.CLASS_CONDITION_KEY);
        return typed != null ? typed.getType() : null;
    }

    @Nullable
    public static PsiType getQualifiedMemberReferenceType(@Nullable PsiType qualifierType, @Nonnull final PsiMember member) {
        final SimpleReference<PsiSubstitutor> subst = SimpleReference.create(PsiSubstitutor.EMPTY);
        class MyProcessor implements PsiScopeProcessor, NameHint, ElementClassHint {
            @Override
            public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state) {
                if (element == member) {
                    subst.set(state.get(PsiSubstitutor.KEY));
                }
                return true;
            }

            @Override
            public void handleEvent(Event event, @Nullable Object associated) {

            }

            @Override
            public String getName(@Nonnull ResolveState state) {
                return member.getName();
            }

            @Override
            public boolean shouldProcess(@Nonnull DeclarationKind kind) {
                return member instanceof PsiEnumConstant ? kind == DeclarationKind.ENUM_CONST :
                    member instanceof PsiField ? kind == DeclarationKind.FIELD :
                        kind == DeclarationKind.METHOD;
            }

            @Override
            public <T> T getHint(@Nonnull Key<T> hintKey) {
                //noinspection unchecked
                return hintKey == NameHint.KEY || hintKey == ElementClassHint.KEY ? (T)this : null;
            }
        }

        PsiScopesUtil.processTypeDeclarations(qualifierType, member, new MyProcessor());

        PsiType rawType = member instanceof PsiField field ? field.getType() :
            member instanceof PsiMethod method ? method.getReturnType() :
                JavaPsiFacade.getElementFactory(member.getProject()).createType((PsiClass)member);
        return subst.get().substitute(rawType);
    }

    @RequiredReadAction
    public static Set<LookupElement> processJavaReference(
        PsiElement element,
        PsiJavaReference javaReference,
        ElementFilter elementFilter,
        JavaCompletionProcessor.Options options,
        PrefixMatcher matcher,
        CompletionParameters parameters
    ) {
        if (element.getContext() instanceof PsiReferenceExpression refExpr
            && refExpr.getQualifierExpression() instanceof PsiReferenceExpression qRefExpr
            && qRefExpr.resolve() instanceof PsiParameter parameter
            && parameter.getType() instanceof PsiLambdaParameterType) {
            PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)parameter.getDeclarationScope();
            if (PsiTypesUtil.getExpectedTypeByParent(lambdaExpression) == null) {
                int parameterIndex = lambdaExpression.getParameterList().getParameterIndex(parameter);
                Set<LookupElement> set = new LinkedHashSet<>();
                boolean overloadsFound = LambdaUtil.processParentOverloads(
                    lambdaExpression,
                    functionalInterfaceType -> {
                        PsiType qualifierType = LambdaUtil.getLambdaParameterFromType(functionalInterfaceType, parameterIndex);
                        if (qualifierType instanceof PsiWildcardType wildcardType) {
                            qualifierType = wildcardType.getBound();
                        }
                        if (qualifierType == null) {
                            return;
                        }

                        PsiReferenceExpression fakeRef =
                            createReference("xxx.xxx", createContextWithXxxVariable(element, qualifierType));
                        set.addAll(processJavaQualifiedReference(
                            fakeRef.getReferenceNameElement(),
                            fakeRef,
                            elementFilter,
                            options,
                            matcher,
                            parameters
                        ));
                    }
                );
                if (overloadsFound) {
                    return set;
                }
            }
        }
        return processJavaQualifiedReference(element, javaReference, elementFilter, options, matcher, parameters);
    }

    @RequiredReadAction
    private static Set<LookupElement> processJavaQualifiedReference(
        PsiElement element,
        PsiJavaReference javaReference,
        ElementFilter elementFilter,
        JavaCompletionProcessor.Options options,
        PrefixMatcher matcher,
        CompletionParameters parameters
    ) {
        Set<LookupElement> set = new LinkedHashSet<>();
        Predicate<String> nameCondition = matcher::prefixMatches;

        JavaCompletionProcessor processor = new JavaCompletionProcessor(element, elementFilter, options, nameCondition);
        PsiType plainQualifier = processor.getQualifierType();

        List<PsiType> runtimeQualifiers = getQualifierCastTypes(javaReference, parameters);
        if (!runtimeQualifiers.isEmpty()) {
            PsiType[] conjuncts = JBIterable.of(plainQualifier).append(runtimeQualifiers).toList().toArray(PsiType.EMPTY_ARRAY);
            PsiType composite = PsiIntersectionType.createIntersection(false, conjuncts);
            PsiElement ctx = createContextWithXxxVariable(element, composite);
            javaReference = createReference("xxx.xxx", ctx);
            processor.setQualifierType(composite);
        }

        javaReference.processVariants(processor);

        List<PsiTypeLookupItem> castItems = ContainerUtil.map(runtimeQualifiers, q -> PsiTypeLookupItem.createLookupItem(q, element));

        boolean pkgContext = inSomePackage(element);

        PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(plainQualifier);
        boolean honorExcludes = qualifierClass == null || !isInExcludedPackage(qualifierClass, false);

        Set<PsiType> expectedTypes = ObjectUtil.coalesce(getExpectedTypes(parameters), Collections.emptySet());

        Set<PsiMember> mentioned = new HashSet<>();
        for (CompletionElement completionElement : processor.getResults()) {
            for (LookupElement item : createLookupElements(completionElement, javaReference)) {
                item.putUserData(QUALIFIER_TYPE_ATTR, plainQualifier);
                Object o = item.getObject();
                if (o instanceof PsiClass psiClass && !isSourceLevelAccessible(element, psiClass, pkgContext)) {
                    continue;
                }
                if (o instanceof PsiMember member) {
                    if (honorExcludes && isInExcludedPackage(member, true)) {
                        continue;
                    }
                    mentioned.add(CompletionUtilCore.getOriginalOrSelf(member));
                }
                PsiTypeLookupItem qualifierCast = findQualifierCast(item, castItems, plainQualifier, processor, expectedTypes);
                if (qualifierCast != null) {
                    item = castQualifier(item, qualifierCast);
                }
                set.add(highlightIfNeeded(qualifierCast != null ? qualifierCast.getType() : plainQualifier, item, o, element));
            }
        }

        if (javaReference instanceof PsiJavaCodeReferenceElement javaCodeRef) {
            PsiElement refQualifier = javaCodeRef.getQualifier();
            if (refQualifier == null
                && PsiTreeUtil.getParentOfType(element, PsiPackageStatement.class, PsiImportStatementBase.class) == null) {
                StaticMemberProcessor memberProcessor = new JavaStaticMemberProcessor(parameters);
                memberProcessor.processMembersOfRegisteredClasses(matcher, (member, psiClass) -> {
                    if (!mentioned.contains(member) && processor.satisfies(member, ResolveState.initial())) {
                        ContainerUtil.addIfNotNull(set, memberProcessor.createLookupElement(member, psiClass, true));
                    }
                });
            }
            else if (refQualifier instanceof PsiSuperExpression superExpr && superExpr.getQualifier() == null) {
                set.addAll(SuperCalls.suggestQualifyingSuperCalls(element, javaReference, elementFilter, options, nameCondition));
            }
        }

        return set;
    }

    @Nonnull
    static PsiReferenceExpression createReference(@Nonnull String text, @Nonnull PsiElement context) {
        return (PsiReferenceExpression)JavaPsiFacade.getElementFactory(context.getProject()).createExpressionFromText(text, context);
    }

    @Nonnull
    private static List<PsiType> getQualifierCastTypes(PsiJavaReference javaReference, CompletionParameters parameters) {
        if (javaReference instanceof PsiReferenceExpression refExpr) {
            PsiExpression qualifier = refExpr.getQualifierExpression();
            if (qualifier != null) {
                Project project = qualifier.getProject();
                BiFunction<PsiExpression, CompletionParameters, PsiType> evaluator =
                    refExpr.getContainingFile().getCopyableUserData(DYNAMIC_TYPE_EVALUATOR);
                if (evaluator != null) {
                    PsiType type = evaluator.apply(qualifier, parameters);
                    if (type != null) {
                        return Collections.singletonList(type);
                    }
                }

                return GuessManager.getInstance(project)
                    .getControlFlowExpressionTypeConjuncts(qualifier, parameters.getInvocationCount() > 1);
            }
        }
        return Collections.emptyList();
    }

    private static boolean shouldCast(
        @Nonnull LookupElement item,
        @Nonnull PsiTypeLookupItem castTypeItem,
        @Nullable PsiType plainQualifier,
        @Nonnull JavaCompletionProcessor processor,
        @Nonnull Set<? extends PsiType> expectedTypes
    ) {
        PsiType castType = castTypeItem.getType();
        if (plainQualifier == null) {
            return false;
        }
        Object o = item.getObject();
        if (o instanceof PsiMethod method
            && plainQualifier instanceof PsiClassType qClassType
            && castType instanceof PsiClassType castClassType) {
            PsiClassType.ClassResolveResult plainResult = qClassType.resolveGenerics();
            PsiClass plainClass = plainResult.getElement();
            HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();
            PsiMethod plainMethod = plainClass == null
                ? null
                : StreamEx.ofTree(signature, s -> StreamEx.of(s.getSuperSignatures()))
                    .map(sig -> MethodSignatureUtil.findMethodBySignature(plainClass, sig, true))
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);
            if (plainMethod != null) {
                PsiClassType.ClassResolveResult castResult = castClassType.resolveGenerics();
                PsiClass castClass = castResult.getElement();

                if (castClass == null || !castClass.isInheritor(plainClass, true)) {
                    return false;
                }

                if (!processor.isAccessible(plainMethod)) {
                    return true;
                }

                PsiSubstitutor castSub = TypeConversionUtil.getSuperClassSubstitutor(plainClass, castClassType);
                PsiType typeAfterCast = toRaw(castSub.substitute(method.getReturnType()));
                PsiType typeDeclared = toRaw(plainResult.getSubstitutor().substitute(plainMethod.getReturnType()));
                return typeAfterCast != null && typeDeclared != null
                    && !typeAfterCast.equals(typeDeclared)
                    && expectedTypes.stream().anyMatch(et -> et.isAssignableFrom(typeAfterCast) && !et.isAssignableFrom(typeDeclared));
            }
        }

        return containsMember(castType, o, true) && !containsMember(plainQualifier, o, true);
    }

    @Nonnull
    private static LookupElement castQualifier(@Nonnull LookupElement item, @Nonnull PsiTypeLookupItem castTypeItem) {
        return new LookupElementDecorator<>(item) {
            @Override
            @RequiredReadAction
            public void handleInsert(@Nonnull InsertionContext context) {
                Document document = context.getEditor().getDocument();
                context.commitDocument();
                PsiFile file = context.getFile();
                PsiJavaCodeReferenceElement ref =
                    PsiTreeUtil.findElementOfClassAtOffset(file, context.getStartOffset(), PsiJavaCodeReferenceElement.class, false);
                if (ref != null) {
                    PsiElement qualifier = ref.getQualifier();
                    if (qualifier != null) {
                        CommonCodeStyleSettings settings = CompletionStyleUtil.getCodeStyleSettings(context);

                        String parenSpace = settings.SPACE_WITHIN_PARENTHESES ? " " : "";
                        document.insertString(qualifier.getTextRange().getEndOffset(), parenSpace + ")");

                        String spaceWithin = settings.SPACE_WITHIN_CAST_PARENTHESES ? " " : "";
                        String prefix = "(" + parenSpace + "(" + spaceWithin;
                        String spaceAfter = settings.SPACE_AFTER_TYPE_CAST ? " " : "";
                        int exprStart = qualifier.getTextRange().getStartOffset();
                        document.insertString(exprStart, prefix + spaceWithin + ")" + spaceAfter);

                        CompletionUtilCore.emulateInsertion(context, exprStart + prefix.length(), castTypeItem);
                        PsiDocumentManager.getInstance(file.getProject()).doPostponedOperationsAndUnblockDocument(document);
                        context.getEditor().getCaretModel().moveToOffset(context.getTailOffset());
                    }
                }

                super.handleInsert(context);
            }

            @Override
            public void renderElement(LookupElementPresentation presentation) {
                super.renderElement(presentation);

                presentation.appendTailText(" on " + castTypeItem.getType().getPresentableText(), true);
            }
        };
    }

    private static PsiTypeLookupItem findQualifierCast(
        @Nonnull LookupElement item,
        @Nonnull List<? extends PsiTypeLookupItem> castTypeItems,
        @Nullable PsiType plainQualifier,
        JavaCompletionProcessor processor,
        Set<? extends PsiType> expectedTypes
    ) {
        return ContainerUtil.find(castTypeItems, c -> shouldCast(item, c, plainQualifier, processor, expectedTypes));
    }

    @Nullable
    private static PsiType toRaw(@Nullable PsiType type) {
        return type instanceof PsiClassType classType ? classType.rawType() : type;
    }

    @Nonnull
    @RequiredReadAction
    public static LookupElement highlightIfNeeded(
        @Nullable PsiType qualifierType,
        @Nonnull LookupElement item,
        @Nonnull Object object,
        @Nonnull PsiElement place
    ) {
        if (shouldMarkRed(object, place)) {
            return PrioritizedLookupElement.withExplicitProximity(
                LookupElementDecorator.withRenderer(
                    item,
                    new LookupElementRenderer<>() {
                        @Override
                        public void renderElement(LookupElementDecorator<LookupElement> element, LookupElementPresentation presentation) {
                            element.getDelegate().renderElement(presentation);
                            presentation.setItemTextForeground(StandardColors.RED);
                        }
                    }
                ),
                -1
            );
        }
        if (containsMember(qualifierType, object, false) && !qualifierType.equalsToText(JavaClassNames.JAVA_LANG_OBJECT)) {
            LookupElementDecorator<LookupElement> bold = LookupElementDecorator.withRenderer(
                item,
                new LookupElementRenderer<>() {
                    @Override
                    public void renderElement(LookupElementDecorator<LookupElement> element, LookupElementPresentation presentation) {
                        element.getDelegate().renderElement(presentation);
                        presentation.setItemTextBold(true);
                    }
                }
            );
            return object instanceof PsiField ? bold : PrioritizedLookupElement.withExplicitProximity(bold, 1);
        }
        return item;
    }

    @RequiredReadAction
    private static boolean shouldMarkRed(@Nonnull Object object, @Nonnull PsiElement place) {
        if (!(object instanceof PsiMember member)) {
            return false;
        }
        if (Java15APIUsageInspection.getLastIncompatibleLanguageLevel(member, PsiUtil.getLanguageLevel(place)) != null) {
            return true;
        }

        if (object instanceof PsiEnumConstant enumConst) {
            return findConstantsUsedInSwitch(place).contains(CompletionUtilCore.getOriginalOrSelf(enumConst));
        }
        return object instanceof PsiClass psiClass && ReferenceListWeigher.INSTANCE.getApplicability(psiClass, place) == inapplicable;
    }

    @Contract("null, _, _ -> false")
    private static boolean containsMember(@Nullable PsiType qualifierType, @Nonnull Object object, boolean checkBases) {
        if (!(object instanceof PsiMember member)) {
            return false;
        }

        if (qualifierType instanceof PsiArrayType) { //length and clone()
            PsiFile file = member.getContainingFile();
            if (file == null || file.getVirtualFile() == null) { //yes, they're a bit dummy
                return true;
            }
        }
        else if (qualifierType instanceof PsiClassType classType) {
            PsiClass qualifierClass = classType.resolve();
            if (qualifierClass == null) {
                return false;
            }
            if (object instanceof PsiMethod method && qualifierClass.findMethodBySignature(method, checkBases) != null) {
                return true;
            }
            PsiClass memberClass = member.getContainingClass();
            return checkBases
                ? InheritanceUtil.isInheritorOrSelf(qualifierClass, memberClass, true)
                : qualifierClass.equals(memberClass);
        }
        return false;
    }

    @RequiredReadAction
    static Iterable<? extends LookupElement> createLookupElements(CompletionElement completionElement, PsiJavaReference reference) {
        Object completion = completionElement.getElement();
        assert !(completion instanceof LookupElement);

        if (reference instanceof PsiJavaCodeReferenceElement javaCodeRef) {
            if (completion instanceof PsiMethod method && javaCodeRef.getParent() instanceof PsiImportStaticStatement) {
                return Collections.singletonList(JavaLookupElementBuilder.forMethod(method, PsiSubstitutor.EMPTY));
            }

            if (completion instanceof PsiClass psiClass) {
                List<JavaPsiClassReferenceElement> classItems = JavaClassNameCompletionContributor.createClassLookupItems(
                    CompletionUtilCore.getOriginalOrSelf(psiClass),
                    JavaClassNameCompletionContributor.AFTER_NEW.accepts(reference),
                    JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER,
                    Predicates.alwaysTrue()
                );
                return JBIterable.from(classItems).flatMap(i -> JavaConstructorCallElement.wrap(i, reference.getElement()));
            }
        }

        if (reference instanceof PsiMethodReferenceExpression && completion instanceof PsiMethod method && method.isConstructor()) {
            return Collections.singletonList(JavaLookupElementBuilder.forMethod(method, "new", PsiSubstitutor.EMPTY, null));
        }

        PsiSubstitutor substitutor = completionElement.getSubstitutor();
        if (substitutor == null) {
            substitutor = PsiSubstitutor.EMPTY;
        }
        if (completion instanceof PsiClass psiClass) {
            JavaPsiClassReferenceElement classItem =
                JavaClassNameCompletionContributor.createClassLookupItem(psiClass, true).setSubstitutor(substitutor);
            return JavaConstructorCallElement.wrap(classItem, reference.getElement());
        }
        if (completion instanceof PsiMethod method) {
            if (reference instanceof PsiMethodReferenceExpression methodRefExpr) {
                return Collections.singleton(new JavaMethodReferenceElement(method, methodRefExpr));
            }

            JavaMethodCallElement item = new JavaMethodCallElement(method).setQualifierSubstitutor(substitutor);
            item.setForcedQualifier(completionElement.getQualifierText());
            return Collections.singletonList(item);
        }
        if (completion instanceof PsiVariable variable) {
            return Collections.singletonList(new VariableLookupItem(variable).setSubstitutor(substitutor));
        }
        if (completion instanceof PsiPackage psiPackage) {
            return Collections.singletonList(new PackageLookupItem(psiPackage, reference.getElement()));
        }

        return Collections.singletonList(LookupItemUtil.objectToLookupItem(completion));
    }

    public static boolean hasAccessibleConstructor(@Nonnull PsiType type, @Nonnull PsiElement place) {
        if (type instanceof PsiArrayType) {
            return true;
        }

        PsiClass psiClass = PsiUtil.resolveClassInType(type);
        if (psiClass == null || psiClass.isEnum() || psiClass.isAnnotationType()) {
            return false;
        }

        PsiMethod[] methods = psiClass.getConstructors();
        return methods.length == 0 || Arrays.stream(methods).anyMatch(constructor -> isConstructorCompletable(constructor, place));
    }

    private static boolean isConstructorCompletable(@Nonnull PsiMethod constructor, @Nonnull PsiElement place) {
        if (!(constructor instanceof PsiCompiledElement)) {
            return true; // it's possible to use a quick fix to make accessible after completion
        }
        //noinspection SimplifiableIfStatement
        if (constructor.isPrivate()) {
            return false;
        }
        return !constructor.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)
            || PsiUtil.isAccessible(constructor, place, null);
    }

    @RequiredReadAction
    public static LinkedHashSet<String> getAllLookupStrings(@Nonnull PsiMember member) {
        LinkedHashSet<String> allLookupStrings = new LinkedHashSet<>();
        String name = member.getName();
        allLookupStrings.add(name);
        PsiClass containingClass = member.getContainingClass();
        while (containingClass != null) {
            String className = containingClass.getName();
            if (className == null) {
                break;
            }
            name = className + "." + name;
            allLookupStrings.add(name);
            PsiElement parent = containingClass.getParent();
            if (!(parent instanceof PsiClass psiClass)) {
                break;
            }
            containingClass = psiClass;
        }
        return allLookupStrings;
    }

    public static boolean mayHaveSideEffects(@Nullable PsiElement element) {
        return element instanceof PsiExpression expression && SideEffectChecker.mayHaveSideEffects(expression);
    }

    @RequiredWriteAction
    public static void insertClassReference(@Nonnull PsiClass psiClass, @Nonnull PsiFile file, int offset) {
        insertClassReference(psiClass, file, offset, offset);
    }

    @RequiredWriteAction
    public static int insertClassReference(PsiClass psiClass, PsiFile file, int startOffset, int endOffset) {
        Project project = file.getProject();
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        documentManager.commitAllDocuments();

        PsiManager manager = file.getManager();

        Document document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());

        PsiReference reference = file.findReferenceAt(startOffset);
        if (reference != null && manager.areElementsEquivalent(psiClass, reference.resolve())) {
            return endOffset;
        }

        String name = psiClass.getName();
        if (name == null) {
            return endOffset;
        }

        if (reference != null && !psiClass.isStatic()) {
            PsiClass containingClass = psiClass.getContainingClass();
            if (containingClass != null && containingClass.hasTypeParameters()) {
                PsiModifierListOwner enclosingStaticElement = PsiUtil.getEnclosingStaticElement(reference.getElement(), null);
                if (enclosingStaticElement != null && !PsiTreeUtil.isAncestor(enclosingStaticElement, psiClass, false)) {
                    return endOffset;
                }
            }
        }

        assert document != null;
        document.replaceString(startOffset, endOffset, name);

        int newEndOffset = startOffset + name.length();
        RangeMarker toDelete = insertTemporary(newEndOffset, document, " ");

        documentManager.commitAllDocuments();

        PsiElement element = file.findElementAt(startOffset);
        if (element instanceof PsiIdentifier identifier
            && identifier.getParent() instanceof PsiJavaCodeReferenceElement ref
            && !ref.isQualified() && !(ref.getParent() instanceof PsiPackageStatement)
            && psiClass.isValid() && !psiClass.getManager().areElementsEquivalent(psiClass, resolveReference(ref))) {
            boolean staticImport = ref instanceof PsiImportStaticReferenceElement;
            PsiElement newElement;
            try {
                newElement = staticImport
                    ? ((PsiImportStaticReferenceElement)ref).bindToTargetClass(psiClass)
                    : ref.bindToElement(psiClass);
            }
            catch (IncorrectOperationException e) {
                return endOffset; // can happen if fqn contains reserved words, for example
            }

            RangeMarker rangeMarker = document.createRangeMarker(newElement.getTextRange());
            documentManager.doPostponedOperationsAndUnblockDocument(document);
            documentManager.commitDocument(document);

            newElement = CodeInsightUtilCore.findElementInRange(
                file,
                rangeMarker.getStartOffset(),
                rangeMarker.getEndOffset(),
                PsiJavaCodeReferenceElement.class,
                JavaLanguage.INSTANCE
            );
            rangeMarker.dispose();
            if (newElement != null) {
                newEndOffset = newElement.getTextRange().getEndOffset();
                if (!(newElement instanceof PsiReferenceExpression)) {
                    PsiReferenceParameterList parameterList = ((PsiJavaCodeReferenceElement)newElement).getParameterList();
                    if (parameterList != null) {
                        newEndOffset = parameterList.getTextRange().getStartOffset();
                    }
                }

                if (!staticImport
                    && !psiClass.getManager().areElementsEquivalent(psiClass, resolveReference((PsiReference)newElement))
                    && !PsiUtil.isInnerClass(psiClass)) {
                    String qName = psiClass.getQualifiedName();
                    if (qName != null) {
                        document.replaceString(newElement.getTextRange().getStartOffset(), newEndOffset, qName);
                        newEndOffset = newElement.getTextRange().getStartOffset() + qName.length();
                    }
                }
            }
        }

        if (toDelete != null && toDelete.isValid()) {
            document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
        }

        return newEndOffset;
    }

    @Nullable
    @RequiredReadAction
    static PsiElement resolveReference(PsiReference psiReference) {
        if (psiReference instanceof PsiPolyVariantReference polyVariantReference) {
            ResolveResult[] results = polyVariantReference.multiResolve(true);
            if (results.length == 1) {
                return results[0].getElement();
            }
        }
        return psiReference.resolve();
    }

    @Nullable
    public static RangeMarker insertTemporary(int endOffset, Document document, String temporary) {
        CharSequence chars = document.getCharsSequence();
        if (endOffset < chars.length() && Character.isJavaIdentifierPart(chars.charAt(endOffset))) {
            document.insertString(endOffset, temporary);
            RangeMarker toDelete = document.createRangeMarker(endOffset, endOffset + 1);
            toDelete.setGreedyToLeft(true);
            toDelete.setGreedyToRight(true);
            return toDelete;
        }
        return null;
    }

    @RequiredUIAccess
    public static void insertParentheses(
        @Nonnull InsertionContext context,
        @Nonnull LookupElement item,
        boolean overloadsMatter,
        boolean hasParams
    ) {
        insertParentheses(context, item, overloadsMatter, hasParams, false);
    }

    @RequiredUIAccess
    public static void insertParentheses(
        @Nonnull InsertionContext context,
        @Nonnull LookupElement item,
        boolean overloadsMatter,
        boolean hasParams,
        boolean forceClosingParenthesis
    ) {
        Editor editor = context.getEditor();
        char completionChar = context.getCompletionChar();
        PsiFile file = context.getFile();

        TailType tailType = completionChar == '(' ? TailType.NONE :
            completionChar == ':' ? TailType.COND_EXPR_COLON :
                LookupItem.handleCompletionChar(context.getEditor(), item, completionChar);
        boolean hasTail = tailType != TailType.NONE && tailType != TailType.UNKNOWN;
        boolean smart = completionChar == Lookup.COMPLETE_STATEMENT_SELECT_CHAR;

        if (completionChar == '(' || completionChar == '.' || completionChar == ','
            || completionChar == ';' || completionChar == ':' || completionChar == ' ') {
            context.setAddCompletionChar(false);
        }

        if (hasTail) {
            hasParams = false;
        }
        final boolean needRightParenth = forceClosingParenthesis
            || !smart && (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET || !hasParams && completionChar != '(');

        context.commitDocument();

        final CommonCodeStyleSettings styleSettings = CompletionStyleUtil.getCodeStyleSettings(context);
        PsiElement elementAt = file.findElementAt(context.getStartOffset());
        if (elementAt == null || !(elementAt.getParent() instanceof PsiMethodReferenceExpression)) {
            final boolean hasParameters = hasParams;
            final boolean spaceBetweenParentheses = styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES && hasParams;
            new ParenthesesInsertHandler<>(
                styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES,
                spaceBetweenParentheses,
                needRightParenth,
                styleSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE
            ) {
                @Override
                protected boolean placeCaretInsideParentheses(InsertionContext context1, LookupElement item1) {
                    return hasParameters;
                }

                @Override
                @RequiredReadAction
                protected PsiElement findExistingLeftParenthesis(@Nonnull InsertionContext context) {
                    PsiElement token = super.findExistingLeftParenthesis(context);
                    return isPartOfLambda(token) ? null : token;
                }

                @RequiredReadAction
                private boolean isPartOfLambda(PsiElement token) {
                    return token != null && token.getParent() instanceof PsiExpressionList expressionList
                        && PsiUtilCore.getElementType(PsiTreeUtil.nextVisibleLeaf(expressionList)) == JavaTokenType.ARROW;
                }
            }.handleInsert(context, item);
        }

        if (hasParams) {
            // Invoke parameters popup
            AutoPopupController.getInstance(file.getProject())
                .autoPopupParameterInfo(editor, overloadsMatter ? null : (PsiElement)item.getObject());
        }

        if (smart || !needRightParenth || !insertTail(context, item, tailType, hasTail)) {
            return;
        }

        if (completionChar == '.') {
            AutoPopupController.getInstance(file.getProject()).autoPopupMemberLookup(context.getEditor(), null);
        }
        else if (completionChar == ',') {
            AutoPopupController.getInstance(file.getProject()).autoPopupParameterInfo(context.getEditor(), null);
        }
    }

    @RequiredUIAccess
    public static boolean insertTail(InsertionContext context, LookupElement item, TailType tailType, boolean hasTail) {
        TailType toInsert = tailType;
        LookupItem<?> lookupItem = item.as(LookupItem.CLASS_CONDITION_KEY);
        if (lookupItem == null || lookupItem.getAttribute(LookupItem.TAIL_TYPE_ATTR) != TailType.UNKNOWN) {
            if (!hasTail && item.getObject() instanceof PsiMethod method && PsiType.VOID.equals(method.getReturnType())) {
                PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
                if (psiElement().beforeLeaf(psiElement().withText("."))
                    .accepts(context.getFile().findElementAt(context.getTailOffset() - 1))) {
                    return false;
                }

                boolean insertAdditionalSemicolon = true;
                PsiElement leaf = context.getFile().findElementAt(context.getStartOffset());
                PsiElement composite = leaf == null ? null : leaf.getParent();
                if (composite instanceof PsiMethodReferenceExpression && LambdaHighlightingUtil.insertSemicolon(composite.getParent())) {
                    insertAdditionalSemicolon = false;
                }
                else if (composite instanceof PsiReferenceExpression refExpr) {
                    PsiElement parent = refExpr.getParent();
                    if (parent instanceof PsiMethodCallExpression) {
                        parent = parent.getParent();
                    }
                    if (parent instanceof PsiLambdaExpression lambda && !LambdaHighlightingUtil.insertSemicolonAfter(lambda)) {
                        insertAdditionalSemicolon = false;
                    }
                    if (parent instanceof PsiMethodReferenceExpression && LambdaHighlightingUtil.insertSemicolon(parent.getParent())) {
                        insertAdditionalSemicolon = false;
                    }
                }
                if (insertAdditionalSemicolon) {
                    toInsert = TailType.SEMICOLON;
                }

            }
        }
        Editor editor = context.getEditor();
        int tailOffset = context.getTailOffset();
        int afterTailOffset = toInsert.processTail(editor, tailOffset);
        int caretOffset = editor.getCaretModel().getOffset();
        if (afterTailOffset > tailOffset && tailOffset > caretOffset &&
            TabOutScopesTracker.getInstance().removeScopeEndingAt(editor, caretOffset) > 0) {
            TabOutScopesTracker.getInstance().registerEmptyScope(editor, caretOffset, afterTailOffset);
        }
        return true;
    }

    //need to shorten references in type argument list
    @RequiredReadAction
    public static void shortenReference(PsiFile file, int offset) throws IncorrectOperationException {
        Project project = file.getProject();
        PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
        Document document = manager.getDocument(file);
        if (document == null) {
            PsiUtilCore.ensureValid(file);
            LOG.error("No document for " + file);
            return;
        }

        manager.commitDocument(document);
        PsiReference ref = file.findReferenceAt(offset);
        if (ref != null) {
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(ref.getElement());
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
        }
    }

    public static boolean inSomePackage(PsiElement context) {
        return context.getContainingFile() instanceof PsiClassOwner contextFile && StringUtil.isNotEmpty(contextFile.getPackageName());
    }

    public static boolean isSourceLevelAccessible(PsiElement context, PsiClass psiClass, boolean pkgContext) {
        if (!JavaPsiFacade.getInstance(psiClass.getProject())
            .getResolveHelper()
            .isAccessible(psiClass, context, psiClass.getContainingClass())) {
            return false;
        }

        if (pkgContext) {
            PsiClass topLevel = PsiUtil.getTopLevelClass(psiClass);
            if (topLevel != null) {
                String fqName = topLevel.getQualifiedName();
                if (fqName != null && StringUtil.isEmpty(StringUtil.getPackageName(fqName))) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean promptTypeArgs(InsertionContext context, int offset) {
        if (offset < 0) {
            return false;
        }

        OffsetKey key = context.trackOffset(offset, false);
        PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting();
        offset = context.getOffset(key);
        if (offset < 0) {
            return false;
        }

        String open = escapeXmlIfNeeded(context, "<");
        context.getDocument().insertString(offset, open);
        context.getEditor().getCaretModel().moveToOffset(offset + open.length());
        if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
            context.getDocument().insertString(offset + open.length(), escapeXmlIfNeeded(context, ">"));
        }
        if (context.getCompletionChar() != Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
            context.setAddCompletionChar(false);
        }
        return true;
    }

    public static FakePsiElement createContextWithXxxVariable(@Nonnull PsiElement place, @Nonnull PsiType varType) {
        return new FakePsiElement() {
            @Override
            public boolean processDeclarations(
                @Nonnull PsiScopeProcessor processor,
                @Nonnull ResolveState state,
                PsiElement lastParent,
                @Nonnull PsiElement place
            ) {
                return processor.execute(new LightVariableBuilder("xxx", varType, place), ResolveState.initial());
            }

            @Override
            public PsiElement getParent() {
                return place;
            }
        };
    }

    @Nonnull
    public static String escapeXmlIfNeeded(InsertionContext context, @Nonnull String generics) {
        //if (context.getFile().getViewProvider().getBaseLanguage() == StdLanguages.JSPX)
        //{
        //    return StringUtil.escapeXmlEntities(generics);
        //}
        return generics;
    }

    public static boolean isEffectivelyDeprecated(PsiDocCommentOwner member) {
        if (member.isDeprecated()) {
            return true;
        }

        PsiClass aClass = member.getContainingClass();
        while (aClass != null) {
            if (aClass.isDeprecated()) {
                return true;
            }
            aClass = aClass.getContainingClass();
        }
        return false;
    }

    public static int findQualifiedNameStart(@Nonnull InsertionContext context) {
        int start = context.getTailOffset() - 1;
        while (start >= 0) {
            char ch = context.getDocument().getCharsSequence().charAt(start);
            if (!Character.isJavaIdentifierPart(ch) && ch != '.') {
                break;
            }
            start--;
        }
        return start + 1;
    }
}