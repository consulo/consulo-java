// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInsight;

import com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.*;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.inference.JavaSourceInference;
import com.intellij.java.language.codeInsight.InferredAnnotationProvider;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.impl.psi.impl.source.PsiMethodImpl;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import jakarta.inject.Inject;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT;
import static com.intellij.java.language.codeInsight.AnnotationUtil.*;

@ExtensionImpl
public class DefaultInferredAnnotationProvider implements InferredAnnotationProvider {
    private static final Set<String> JB_INFERRED_ANNOTATIONS = Set.of(
        ORG_JETBRAINS_ANNOTATIONS_CONTRACT,
        Mutability.UNMODIFIABLE_ANNOTATION,
        Mutability.UNMODIFIABLE_VIEW_ANNOTATION
    );
    private static final Set<String> EXPERIMENTAL_INFERRED_ANNOTATIONS =
        Set.of(Mutability.UNMODIFIABLE_ANNOTATION, Mutability.UNMODIFIABLE_VIEW_ANNOTATION);
    private final Project myProject;

    // Could be added via external annotations, but there are many signatures to handle
    // and we have troubles supporting external annotations for JDK 9+
    private static final CallMatcher IMMUTABLE_FACTORY = CallMatcher.anyOf(
        CallMatcher.staticCall(JavaClassNames.JAVA_UTIL_LIST, "of", "copyOf"),
        CallMatcher.staticCall(JavaClassNames.JAVA_UTIL_SET, "of", "copyOf"),
        CallMatcher.staticCall(JavaClassNames.JAVA_UTIL_MAP, "of", "ofEntries", "copyOf", "entry")
    );
    private final NullableNotNullManager myNullabilityManager;

    @Inject
    public DefaultInferredAnnotationProvider(Project project, NullableNotNullManager nullabilityManager) {
        myProject = project;
        myNullabilityManager = nullabilityManager;
    }

    @Nullable
    @Override
    public PsiAnnotation findInferredAnnotation(@Nonnull PsiModifierListOwner listOwner, @Nonnull String annotationFQN) {
        if (!JB_INFERRED_ANNOTATIONS.contains(annotationFQN) && !isDefaultNullabilityAnnotation(annotationFQN)) {
            return null;
        }

        listOwner = PsiUtil.preferCompiledElement(listOwner);

        if (ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotationFQN) && listOwner instanceof PsiMethod method) {
            PsiAnnotation anno = getHardcodedContractAnnotation(method);
            if (anno != null) {
                return anno;
            }
        }

        if (ignoreInference(listOwner, annotationFQN)) {
            return null;
        }

        PsiAnnotation fromBytecode = ProjectBytecodeAnalysis.getInstance(myProject).findInferredAnnotation(listOwner, annotationFQN);
        if (fromBytecode != null) {
            return fromBytecode;
        }

        if (isDefaultNullabilityAnnotation(annotationFQN)) {
            PsiAnnotation anno = null;
            if (listOwner instanceof PsiMethodImpl method) {
                anno = getInferredNullabilityAnnotation(method);
            }
            if (listOwner instanceof PsiParameter parameter) {
                anno = getInferredNullabilityAnnotation(parameter);
            }
            return anno == null ? null : annotationFQN.equals(anno.getQualifiedName()) ? anno : null;
        }

        if (Mutability.UNMODIFIABLE_ANNOTATION.equals(annotationFQN) || Mutability.UNMODIFIABLE_VIEW_ANNOTATION.equals(annotationFQN)) {
            return getInferredMutabilityAnnotation(listOwner);
        }

        if (listOwner instanceof PsiMethodImpl method && ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotationFQN)) {
            return getInferredContractAnnotation(method);
        }

        return null;
    }

    private boolean isDefaultNullabilityAnnotation(String annotationFQN) {
        return annotationFQN.equals(myNullabilityManager.getDefaultNullable()) || annotationFQN.equals(myNullabilityManager.getDefaultNotNull());
    }

    @Nullable
    private PsiAnnotation getHardcodedContractAnnotation(PsiMethod method) {
        PsiClass aClass = method.getContainingClass();
        if (aClass != null && aClass.getQualifiedName() != null && aClass.getQualifiedName().startsWith("org.assertj.core.api.")) {
            return createContractAnnotation(Collections.emptyList(), MutationSignature.pure());
        }
        List<MethodContract> contracts = HardcodedContracts.getHardcodedContracts(method, null);
        return contracts.isEmpty() ? null : createContractAnnotation(contracts, HardcodedContracts.getHardcodedMutation(method));
    }

    @Nullable
    private PsiAnnotation createContractAnnotation(List<? extends MethodContract> contracts, MutationSignature signature) {
        return createContractAnnotation(myProject, signature.isPure(),
            StreamEx.of(contracts).select(StandardMethodContract.class).joining("; "),
            signature.isPure() || signature == MutationSignature.unknown() ? "" : signature.toString()
        );
    }

    /**
     * There is a number of well-known methods where automatic inference fails (for example, {@link Objects#requireNonNull(Object)}.
     * For such methods, contracts are hardcoded, and for their parameters inferred @NotNull are suppressed.<p/>
     * <p>
     * {@link Contract} and {@link Nonnull} annotations on methods are not necessarily applicable to the overridden implementations, so they're ignored, too.<p/>
     *
     * @return whether inference is to be suppressed the given annotation on the given method or parameter
     */
    private boolean ignoreInference(@Nonnull PsiModifierListOwner owner, @Nullable String annotationFQN) {
        if (annotationFQN == null) {
            return true;
        }
        if (owner instanceof PsiMethod method && PsiUtil.canBeOverridden(method)) {
            return true;
        }
        if (ORG_JETBRAINS_ANNOTATIONS_CONTRACT.equals(annotationFQN) && HardcodedContracts.hasHardcodedContracts(owner)) {
            return true;
        }
        if (annotationFQN.equals(myNullabilityManager.getDefaultNotNull()) && owner instanceof PsiParameter && owner.getParent() != null) {
            List<String> annotations = NullableNotNullManager.getInstance(owner.getProject()).getNullables();
            if (isAnnotated(owner, annotations, CHECK_EXTERNAL | CHECK_TYPE)) {
                return true;
            }
            if (HardcodedContracts.hasHardcodedContracts(owner)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private PsiAnnotation getInferredMutabilityAnnotation(@Nonnull PsiModifierListOwner owner) {
        if (owner instanceof PsiMethod method && IMMUTABLE_FACTORY.methodMatches(method)) {
            return Mutability.UNMODIFIABLE.asAnnotation(myProject);
        }
        if (!(owner instanceof PsiMethodImpl method)) {
            return null;
        }
        PsiModifierList modifiers = method.getModifierList();
        if (modifiers.hasAnnotation(Mutability.UNMODIFIABLE_ANNOTATION)
            || modifiers.hasAnnotation(Mutability.UNMODIFIABLE_VIEW_ANNOTATION)) {
            return null;
        }
        return JavaSourceInference.inferMutability(method).asAnnotation(myProject);
    }

    @Nullable
    private PsiAnnotation getInferredContractAnnotation(PsiMethodImpl method) {
        if (method.getModifierList().hasAnnotation(ORG_JETBRAINS_ANNOTATIONS_CONTRACT)) {
            return null;
        }

        return createContractAnnotation(JavaSourceInference.inferContracts(method), JavaSourceInference.inferPurity(method));
    }

    @Nullable
    private PsiAnnotation getInferredNullabilityAnnotation(PsiMethodImpl method) {
        if (hasExplicitNullability(method)) {
            return null;
        }
        Nullability nullability = JavaSourceInference.inferNullability(method);
        if (nullability == Nullability.NOT_NULL) {
            return ProjectBytecodeAnalysis.getInstance(myProject).getNotNullAnnotation();
        }
        if (nullability == Nullability.NULLABLE) {
            return ProjectBytecodeAnalysis.getInstance(myProject).getNullableAnnotation();
        }
        return null;
    }

    private boolean hasExplicitNullability(PsiModifierListOwner owner) {
        return NullableNotNullManager.getInstance(myProject).findExplicitNullability(owner) != null;
    }

    @Nullable
    private PsiAnnotation getInferredNullabilityAnnotation(PsiParameter parameter) {
        if (hasExplicitNullability(parameter)) {
            return null;
        }
        PsiElement parent = parameter.getParent();
        if (!(parent instanceof PsiParameterList)) {
            return null;
        }
        PsiElement scope = parent.getParent();
        if (scope instanceof PsiMethod method && method.getName().equals("of")) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                String className = containingClass.getQualifiedName();
                if (JavaClassNames.JAVA_UTIL_LIST.equals(className)
                    || JavaClassNames.JAVA_UTIL_SET.equals(className)
                    || JavaClassNames.JAVA_UTIL_MAP.equals(className)
                    || JavaClassNames.JAVA_UTIL_ENUM_SET.equals(className)) {
                    return ProjectBytecodeAnalysis.getInstance(myProject).getNotNullAnnotation();
                }
            }
        }
        Nullability nullability = JavaSourceInference.inferNullability(parameter);
        return nullability == Nullability.NOT_NULL ? ProjectBytecodeAnalysis.getInstance(myProject).getNotNullAnnotation() : null;
    }

    @Nullable
    private PsiAnnotation createContractAnnotation(List<? extends MethodContract> contracts, boolean pure) {
        return createContractAnnotation(myProject, pure, StreamEx.of(contracts).select(StandardMethodContract.class).joining("; "), "");
    }

    @Nullable
    public static PsiAnnotation createContractAnnotation(Project project, boolean pure, String contracts, String mutates) {
        Map<String, String> attrMap = new LinkedHashMap<>();
        if (!contracts.isEmpty()) {
            attrMap.put("value", StringUtil.wrapWithDoubleQuote(contracts));
        }
        if (pure) {
            attrMap.put("pure", "true");
        }
        else if (!mutates.trim().isEmpty()) {
            attrMap.put("mutates", StringUtil.wrapWithDoubleQuote(mutates));
        }
        if (attrMap.isEmpty()) {
            return null;
        }
        String attrs = attrMap.keySet().equals(Collections.singleton("value")) ?
            attrMap.get("value") : EntryStream.of(attrMap).join(" = ").joining(", ");
        return ProjectBytecodeAnalysis.getInstance(project).createContractAnnotation(attrs);
    }

    @Nonnull
    @Override
    public List<PsiAnnotation> findInferredAnnotations(@Nonnull PsiModifierListOwner listOwner) {
        listOwner = PsiUtil.preferCompiledElement(listOwner);
        List<PsiAnnotation> result = new ArrayList<>();
        PsiAnnotation[] fromBytecode = ProjectBytecodeAnalysis.getInstance(myProject).findInferredAnnotations(listOwner);
        for (PsiAnnotation annotation : fromBytecode) {
            if (!ignoreInference(listOwner, annotation.getQualifiedName())) {
                result.add(annotation);
            }
        }

        if (listOwner instanceof PsiMethod method) {
            PsiAnnotation hardcoded = getHardcodedContractAnnotation(method);
            ContainerUtil.addIfNotNull(result, hardcoded);
            if (listOwner instanceof PsiMethodImpl methodImpl) {
                if (hardcoded == null && !ignoreInference(listOwner, ORG_JETBRAINS_ANNOTATIONS_CONTRACT)) {
                    ContainerUtil.addIfNotNull(result, getInferredContractAnnotation(methodImpl));
                }

                if (!ignoreInference(listOwner, myNullabilityManager.getDefaultNotNull()) ||
                    !ignoreInference(listOwner, myNullabilityManager.getDefaultNullable())) {
                    PsiAnnotation annotation = getInferredNullabilityAnnotation(methodImpl);
                    if (annotation != null && !ignoreInference(listOwner, annotation.getQualifiedName())) {
                        result.add(annotation);
                    }
                }
            }
        }

        if (listOwner instanceof PsiParameter parameter && !ignoreInference(listOwner, myNullabilityManager.getDefaultNotNull())) {
            ContainerUtil.addIfNotNull(result, getInferredNullabilityAnnotation(parameter));
        }

        ContainerUtil.addIfNotNull(result, getInferredMutabilityAnnotation(listOwner));

        return result;
    }

    public static boolean isExperimentalInferredAnnotation(@Nonnull PsiAnnotation annotation) {
        return EXPERIMENTAL_INFERRED_ANNOTATIONS.contains(annotation.getQualifiedName());
    }
}
