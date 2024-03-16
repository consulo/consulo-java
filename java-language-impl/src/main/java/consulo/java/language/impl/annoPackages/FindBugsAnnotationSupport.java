// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.java.language.impl.annoPackages;

import com.intellij.java.language.annoPackages.AnnotationPackageSupport;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullabilityAnnotationInfo;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;

@ExtensionImpl
public final class FindBugsAnnotationSupport implements AnnotationPackageSupport {
  private static final String NON_NULL = "edu.umd.cs.findbugs.annotations.NonNull";
  private static final String NULLABLE = "edu.umd.cs.findbugs.annotations.Nullable";
  private static final String DEFAULT_ANNOTATION_FOR_PARAMETERS = "edu.umd.cs.findbugs.annotations.DefaultAnnotationForParameters";

  @Nonnull
  @Override
  public List<String> getNullabilityAnnotations(@Nonnull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> Collections.singletonList(NON_NULL);
      case NULLABLE -> Collections.singletonList(NULLABLE);
      default -> Collections.emptyList();
    };
  }

  @RequiredReadAction
  @Override
  @Nullable
  public NullabilityAnnotationInfo getNullabilityByContainerAnnotation(@Nonnull PsiAnnotation anno,
                                                                                 @Nonnull PsiElement context,
                                                                       @Nonnull PsiAnnotation.TargetType  [] types,
                                                                                 boolean superPackage) {
    if (!superPackage &&
      anno.hasQualifiedName(DEFAULT_ANNOTATION_FOR_PARAMETERS) &&
      ArrayUtil.contains(PsiAnnotation.TargetType.PARAMETER, types)) {
      PsiAnnotationMemberValue value = anno.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (value instanceof PsiClassObjectAccessExpression cls &&
        cls.getOperand().getType() instanceof PsiClassType clsType &&
        ("NonNull".equals(clsType.getClassName()) || "Nullable".equals(clsType.getClassName()))) {
        PsiClass resolved = clsType.resolve();
        if (resolved != null) {
          String qualifiedName = resolved.getQualifiedName();
          if (NON_NULL.equals(qualifiedName)) {
            return new NullabilityAnnotationInfo(anno, Nullability.NOT_NULL, true);
          }
          if (NULLABLE.equals(qualifiedName)) {
            return new NullabilityAnnotationInfo(anno, Nullability.NULLABLE, true);
          }
        }
      }
    }
    return null;
  }
}
