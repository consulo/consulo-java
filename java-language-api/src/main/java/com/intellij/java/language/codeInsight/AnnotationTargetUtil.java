/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.language.codeInsight;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.PsiAnnotation.TargetType;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.dumb.IndexNotReadyException;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.logging.Logger;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;

import java.util.*;

/**
 * @author peter
 */
public class AnnotationTargetUtil {
    private static final Logger LOG = Logger.getInstance(AnnotationTargetUtil.class);

    public static final Set<TargetType> DEFAULT_TARGETS = Set.of(
        TargetType.PACKAGE,
        TargetType.TYPE,
        TargetType.ANNOTATION_TYPE,
        TargetType.FIELD,
        TargetType.METHOD,
        TargetType.CONSTRUCTOR,
        TargetType.PARAMETER,
        TargetType.LOCAL_VARIABLE,
        TargetType.MODULE,
        TargetType.RECORD_COMPONENT
    );

    private static final TargetType[] PACKAGE_TARGETS = {TargetType.PACKAGE};
    private static final TargetType[] TYPE_USE_TARGETS = {TargetType.TYPE_USE};
    private static final TargetType[] ANNOTATION_TARGETS = {
        TargetType.ANNOTATION_TYPE,
        TargetType.TYPE,
        TargetType.TYPE_USE
    };
    private static final TargetType[] TYPE_TARGETS = {
        TargetType.TYPE,
        TargetType.TYPE_USE
    };
    private static final TargetType[] TYPE_PARAMETER_TARGETS = {
        TargetType.TYPE_PARAMETER,
        TargetType.TYPE_USE
    };
    private static final TargetType[] CONSTRUCTOR_TARGETS = {
        TargetType.CONSTRUCTOR,
        TargetType.TYPE_USE
    };
    private static final TargetType[] METHOD_TARGETS = {
        TargetType.METHOD,
        TargetType.TYPE_USE
    };
    private static final TargetType[] FIELD_TARGETS = {
        TargetType.FIELD,
        TargetType.TYPE_USE
    };
    private static final TargetType[] RECORD_COMPONENT_TARGETS = {
        TargetType.RECORD_COMPONENT,
        TargetType.FIELD,
        TargetType.METHOD,
        TargetType.PARAMETER,
        TargetType.TYPE_USE
    };
    private static final TargetType[] PARAMETER_TARGETS = {
        TargetType.PARAMETER,
        TargetType.TYPE_USE
    };
    private static final TargetType[] LOCAL_VARIABLE_TARGETS = {
        TargetType.LOCAL_VARIABLE,
        TargetType.TYPE_USE
    };
    private static final TargetType[] MODULE_TARGETS = {TargetType.MODULE};

    @Nonnull
    public static TargetType[] getTargetsForLocation(@Nullable PsiAnnotationOwner owner) {
        if (owner == null) {
            return TargetType.EMPTY_ARRAY;
        }

        if (owner instanceof PsiType || owner instanceof PsiTypeElement) {
            return TYPE_USE_TARGETS;
        }

        if (owner instanceof PsiTypeParameter) {
            return TYPE_PARAMETER_TARGETS;
        }

        if (owner instanceof PsiModifierList modifierList) {
            PsiElement element = modifierList.getParent();
            if (element instanceof PsiPackageStatement) {
                return PACKAGE_TARGETS;
            }
            if (element instanceof PsiClass psiClass) {
                if (psiClass.getModifierList() != owner) {
                    return TargetType.EMPTY_ARRAY;
                }
                if (psiClass.isAnnotationType()) {
                    return ANNOTATION_TARGETS;
                }
                else {
                    return TYPE_TARGETS;
                }
            }
            if (element instanceof PsiRecordComponent) {
                return RECORD_COMPONENT_TARGETS;
            }
            if (element instanceof PsiMethod method) {
                return method.isConstructor() ? CONSTRUCTOR_TARGETS : METHOD_TARGETS;
            }
            if (element instanceof PsiField) {
                return FIELD_TARGETS;
            }
            if (element instanceof PsiParameter parameter) {
                // PARAMETER applies only to formal parameters (methods & lambdas) and catch parameters
                // see https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.6.4.1
                PsiElement scope = parameter.getParent();
                if (scope instanceof PsiForeachStatement || parameter instanceof PsiPatternVariable) {
                    return LOCAL_VARIABLE_TARGETS;
                }
                if (scope instanceof PsiParameterList && scope.getParent() instanceof PsiLambdaExpression
                    && parameter.getTypeElement() == null) {
                    return TargetType.EMPTY_ARRAY;
                }

                return PARAMETER_TARGETS;
            }
            if (element instanceof PsiLocalVariable) {
                return LOCAL_VARIABLE_TARGETS;
            }
            if (element instanceof PsiReceiverParameter) {
                return TYPE_USE_TARGETS;
            }
            if (element instanceof PsiJavaModule) {
                return MODULE_TARGETS;
            }
        }

        return TargetType.EMPTY_ARRAY;
    }

    @Nullable
    @RequiredReadAction
    public static Set<TargetType> extractRequiredAnnotationTargets(@Nullable PsiAnnotationMemberValue value) {
        if (value instanceof PsiReference ref) {
            TargetType targetType = translateTargetRef(ref);
            if (targetType != null) {
                return Collections.singleton(targetType);
            }
        }
        else if (value instanceof PsiArrayInitializerMemberValue arrayInitializerMemberValue) {
            Set<TargetType> targets = new HashSet<>();
            for (PsiAnnotationMemberValue initializer : arrayInitializerMemberValue.getInitializers()) {
                if (initializer instanceof PsiReference ref) {
                    TargetType targetType = translateTargetRef(ref);
                    if (targetType != null) {
                        targets.add(targetType);
                    }
                }
            }
            return targets;
        }

        return null;
    }

    @Nullable
    @RequiredReadAction
    private static TargetType translateTargetRef(@Nonnull PsiReference reference) {
        PsiElement field = reference.resolve();
        if (field instanceof PsiEnumConstant enumConst) {
            String name = enumConst.getName();
            try {
                return TargetType.valueOf(name);
            }
            catch (IllegalArgumentException e) {
                LOG.warn("Unknown target: " + name);
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if the annotation resolves to a class having {@link TargetType#TYPE_USE} in it's targets.
     */
    @RequiredReadAction
    public static boolean isTypeAnnotation(@Nonnull PsiAnnotation element) {
        return findAnnotationTarget(element, TargetType.TYPE_USE) == TargetType.TYPE_USE;
    }

    /**
     * From given targets, returns first where the annotation may be applied. Returns {@code null} when the annotation is not applicable
     * at any of the targets, or {@linkplain TargetType#UNKNOWN} if the annotation does not resolve to a valid annotation type.
     */
    @Nullable
    @RequiredReadAction
    public static TargetType findAnnotationTarget(@Nonnull PsiAnnotation annotation, @Nonnull TargetType... types) {
        if (types.length != 0) {
            PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
            if (ref != null && ref.resolve() instanceof PsiClass annotationType) {
                return findAnnotationTarget(annotationType, types);
            }
        }

        return TargetType.UNKNOWN;
    }

    /**
     * From given targets, returns first where the annotation may be applied. Returns {@code null} when the annotation is not applicable
     * at any of the targets, or {@linkplain TargetType#UNKNOWN} if the type is not a valid annotation (e.g. cannot be resolved).
     */
    @Nullable
    @RequiredReadAction
    public static TargetType findAnnotationTarget(@Nonnull PsiClass annotationType, @Nonnull TargetType... types) {
        if (types.length != 0) {
            Set<TargetType> targets = getAnnotationTargets(annotationType);
            if (targets != null) {
                for (TargetType type : types) {
                    if (type != TargetType.UNKNOWN && targets.contains(type)) {
                        return type;
                    }
                }
                return null;
            }
        }

        return TargetType.UNKNOWN;
    }

    /**
     * Returns a set of targets where the given annotation may be applied, or {@code null} when the type is not a valid annotation.
     */
    @Nullable
    @RequiredReadAction
    public static Set<TargetType> getAnnotationTargets(@Nonnull PsiClass annotationType) {
        if (!annotationType.isAnnotationType()) {
            return null;
        }
        PsiModifierList modifierList = annotationType.getModifierList();
        if (modifierList == null) {
            return null;
        }
        PsiAnnotation target = modifierList.findAnnotation(CommonClassNames.JAVA_LANG_ANNOTATION_TARGET);
        if (target == null) {
            return DEFAULT_TARGETS;  // if omitted it is applicable to all but Java 8 TYPE_USE/TYPE_PARAMETERS targets
        }

        return extractRequiredAnnotationTargets(target.findAttributeValue(null));
    }

    /**
     * @param modifierListOwner modifier list owner
     * @param annotation        the qualified name of the annotation to add
     * @return a target annotation owner to add the annotation (either modifier list or type element depending on the annotation target)
     * Returns null if {@code modifierListOwner.getModifierList()} is null.
     */
    @Nullable
    @RequiredReadAction
    public static PsiAnnotationOwner getTarget(@Nonnull PsiModifierListOwner modifierListOwner, @Nonnull String annotation) {
        PsiModifierList list = modifierListOwner.getModifierList();
        if (list == null) {
            return null;
        }
        PsiClass annotationClass = JavaPsiFacade.getInstance(modifierListOwner.getProject())
            .findClass(annotation, modifierListOwner.getResolveScope());
        return getTarget(modifierListOwner, annotationClass != null
            && findAnnotationTarget(annotationClass, TargetType.TYPE_USE) != null);
    }

    /**
     * @param modifierListOwner   modifier list owner
     * @param existsTypeUseTarget true, if annotation contains a type use target
     * @return a target annotation owner to add the annotation (either modifier list or type element depending on the annotation target)
     * Returns null if {@code modifierListOwner.getModifierList()} is null.
     * <p>The method should be called under read action
     * and the caller should be prepared for {@link IndexNotReadyException}.
     */
    @Contract(pure = true)
    public static @Nullable PsiAnnotationOwner getTarget(@Nonnull PsiModifierListOwner modifierListOwner, boolean existsTypeUseTarget) {
        PsiModifierList list = modifierListOwner.getModifierList();
        if (list == null) {
            return null;
        }
        if (existsTypeUseTarget && !(modifierListOwner instanceof PsiCompiledElement)) {
            PsiElement parent = list.getParent();
            PsiTypeElement type = null;
            if (parent instanceof PsiMethod) {
                type = ((PsiMethod)parent).getReturnTypeElement();
            }
            else if (parent instanceof PsiVariable) {
                type = ((PsiVariable)parent).getTypeElement();
            }
            if (type != null && type.acceptsAnnotations()) {
                return type;
            }
        }
        return list;
    }

    /**
     * Remove type_use annotations when at the usage place "normal" target works as well
     *
     * @param modifierList the place where type appears
     */
    @RequiredWriteAction
    public static PsiType keepStrictlyTypeUseAnnotations(@Nullable PsiModifierList modifierList, @Nonnull PsiType type) {
        if (modifierList == null) {
            return type;
        }
        List<PsiAnnotation> annotations = new ArrayList<>();
        PsiAnnotation[] originalAnnotations = type.getAnnotations();
        for (PsiAnnotation annotation : originalAnnotations) {
            if (isStrictlyTypeUseAnnotation(modifierList, annotation)) {
                annotations.add(annotation);
            }
        }

        if (originalAnnotations.length == annotations.size()) {
            return type;
        }

        return annotations.isEmpty()
            ? type.annotate(TypeAnnotationProvider.EMPTY)
            : type.annotate(TypeAnnotationProvider.Static.create(annotations.toArray(PsiAnnotation.EMPTY_ARRAY)));
    }

    /**
     * Prefers "normal" target when type use annotation appears at the place where it's also applicable.
     * Treat nullability annotations as TYPE_USE: we need to copy these annotations otherwise, and then the place of the annotation in modifier list may differ
     *
     * @param modifierList the place where annotation appears
     * @return true iff annotation is type_use and
     * appears at the "type_use place" (e.g. {@code List<@Nullable String>}) or
     * none of it normal target types are acceptable (e.g. {@code void f(@Nullable String p)} if {@code @Nullable annotation is not applicable to parameters})
     */
    @RequiredReadAction
    public static boolean isStrictlyTypeUseAnnotation(PsiModifierList modifierList, PsiAnnotation annotation) {
        PsiElement parent = annotation.getParent();
        if (parent instanceof PsiJavaCodeReferenceElement || parent instanceof PsiTypeElement) {
            return true;
        }
        PsiClass annotationClass = annotation.resolveAnnotationType();
        if (annotationClass != null) {
            Set<PsiAnnotation.TargetType> targets = getAnnotationTargets(annotationClass);
            if (targets != null && targets.contains(PsiAnnotation.TargetType.TYPE_USE)
                && (targets.size() == 1
                    || NullableNotNullManager.isNullabilityAnnotation(annotation)
                    || !ContainerUtil.exists(
                        getTargetsForLocation(modifierList),
                        target -> target != PsiAnnotation.TargetType.TYPE_USE && targets.contains(target)
                    ))) {
                return true;
            }
        }
        return false;
    }
}