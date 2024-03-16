// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.java.language.impl.annoPackages;

import com.intellij.java.language.annoPackages.AnnotationPackageSupport;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullabilityAnnotationInfo;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@ExtensionImpl
public final class CheckerFrameworkSupport implements AnnotationPackageSupport {
  private static final String DEFAULT_QUALIFIER = "org.checkerframework.framework.qual.DefaultQualifier";
  private static final String DEFAULT_QUALIFIERS = "org.checkerframework.framework.qual.DefaultQualifiers";

  @RequiredReadAction
  @Nullable
  @Override
  public NullabilityAnnotationInfo getNullabilityByContainerAnnotation(@Nonnull PsiAnnotation anno,
                                                                       @Nonnull PsiElement context,
                                                                       @Nonnull PsiAnnotation.TargetType[] types,
                                                                       boolean superPackage) {
    if (context instanceof PsiTypeParameter) {
      // DefaultQualifier is not applicable to type parameter declarations
      return null;
    }
    if (anno.hasQualifiedName(DEFAULT_QUALIFIER)) {
      PsiAnnotationMemberValue value = anno.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (value instanceof PsiClassObjectAccessExpression &&
        hasAppropriateTarget(types, anno.findAttributeValue("locations"))) {
        PsiType type = PsiUtil.getTypeByPsiElement(context);
        if (type instanceof PsiClassType && ((PsiClassType)type).resolve() instanceof PsiTypeParameter) {
          // DefaultQualifier is not applicable to type parameter uses
          return null;
        }
        PsiClass valueClass = PsiUtil.resolveClassInClassTypeOnly(((PsiClassObjectAccessExpression)value).getOperand().getType());
        if (valueClass != null) {
          NullableNotNullManager instance = NullableNotNullManager.getInstance(value.getProject());
          if (instance.getNullables().contains(valueClass.getQualifiedName())) {
            return new NullabilityAnnotationInfo(anno, Nullability.NULLABLE, true);
          }
          if (instance.getNotNulls().contains(valueClass.getQualifiedName())) {
            return new NullabilityAnnotationInfo(anno, Nullability.NOT_NULL, true);
          }
        }
      }
      return null;
    }

    if (anno.hasQualifiedName(DEFAULT_QUALIFIERS)) {
      PsiAnnotationMemberValue value = anno.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      for (PsiAnnotationMemberValue initializer : AnnotationUtil.arrayAttributeValues(value)) {
        if (initializer instanceof PsiAnnotation) {
          NullabilityAnnotationInfo result = getNullabilityByContainerAnnotation((PsiAnnotation)initializer, context, types, superPackage);
          if (result != null) {
            return result;
          }
        }
      }
    }
    return null;
  }

  private static boolean hasAppropriateTarget(PsiAnnotation.TargetType[] types, PsiAnnotationMemberValue locations) {
    Set<String> locationNames = ContainerUtil.map2SetNotNull(AnnotationUtil.arrayAttributeValues(locations),
                                                             l -> l instanceof PsiReferenceExpression ? ((PsiReferenceExpression)l)
                                                               .getReferenceName() : null);
    if (locationNames.contains("ALL")) return true;
    for (PsiAnnotation.TargetType type : types) {
      if (type == PsiAnnotation.TargetType.FIELD) return locationNames.contains("FIELD");
      if (type == PsiAnnotation.TargetType.METHOD) return locationNames.contains("RETURN");
      if (type == PsiAnnotation.TargetType.PARAMETER) return locationNames.contains("PARAMETER");
      if (type == PsiAnnotation.TargetType.LOCAL_VARIABLE) return locationNames.contains("LOCAL_VARIABLE");
    }
    return false;
  }

  @Nonnull
  @Override
  public List<String> getNullabilityAnnotations(@Nonnull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> Arrays.asList("org.checkerframework.checker.nullness.qual.NonNull",
                                     "org.checkerframework.checker.nullness.compatqual.NonNullDecl",
                                     "org.checkerframework.checker.nullness.compatqual.NonNullType");
      case NULLABLE -> Arrays.asList("org.checkerframework.checker.nullness.qual.Nullable",
                                     "org.checkerframework.checker.nullness.compatqual.NullableDecl",
                                     "org.checkerframework.checker.nullness.compatqual.NullableType");
      default -> Collections.singletonList("org.checkerframework.checker.nullness.qual.MonotonicNonNull");
    };
  }
}
