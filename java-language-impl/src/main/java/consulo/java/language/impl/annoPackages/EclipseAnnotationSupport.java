// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.java.language.impl.annoPackages;

import com.intellij.java.language.annoPackages.AnnotationPackageSupport;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullabilityAnnotationInfo;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@ExtensionImpl
public final class EclipseAnnotationSupport implements AnnotationPackageSupport {
  private static final String DEFAULT_NOT_NULL = "org.eclipse.jdt.annotation.NonNullByDefault";

  private static final Map<String, PsiAnnotation.TargetType> TARGET_MAP =
    EntryStream.of("FIELD", PsiAnnotation.TargetType.FIELD,
                   "PARAMETER", PsiAnnotation.TargetType.PARAMETER,
                   "RETURN_TYPE", PsiAnnotation.TargetType.METHOD,
                   "TYPE_PARAMETER", PsiAnnotation.TargetType.TYPE_PARAMETER,
                   "TYPE_BOUND", PsiAnnotation.TargetType.TYPE_USE,
                   "TYPE_ARGUMENT", PsiAnnotation.TargetType.TYPE_USE).toImmutableMap();
  private static final String[] DEFAULT_LOCATIONS = {"PARAMETER", "RETURN_TYPE", "FIELD", "TYPE_BOUND", "TYPE_ARGUMENT"};

  @RequiredReadAction
  @Override
  @Nullable
  public NullabilityAnnotationInfo getNullabilityByContainerAnnotation(@Nonnull PsiAnnotation anno,
                                                                                 @Nonnull PsiElement context,
                                                                       @Nonnull PsiAnnotation.TargetType [] types,
                                                                                 boolean superPackage) {
    if (superPackage) return null;
    if (anno.hasQualifiedName(DEFAULT_NOT_NULL)) {
      PsiAnnotationMemberValue value = anno.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      String[] targets = DEFAULT_LOCATIONS;
      if (value instanceof PsiArrayInitializerMemberValue) {
        targets = StreamEx.of(((PsiArrayInitializerMemberValue)value).getInitializers())
                          .select(PsiReferenceExpression.class)
                          .map(PsiReferenceExpression::getReferenceName)
                          .nonNull()
                          .toArray(String[]::new);
      }
      else if (value instanceof PsiLiteralExpression && Boolean.FALSE.equals(((PsiLiteralExpression)value).getValue())) {
        targets = ArrayUtil.EMPTY_STRING_ARRAY;
      }
      boolean targetApplies = StreamEx.of(targets).map(TARGET_MAP::get).nonNull()
                                      .anyMatch(loc -> loc == PsiAnnotation.TargetType.TYPE_USE ? types.length == 1 && types[0] == loc : ArrayUtil.contains(
                                        loc,
                                        types));
      return new NullabilityAnnotationInfo(anno, targetApplies ? Nullability.NOT_NULL : Nullability.UNKNOWN, true);
    }
    return null;
  }

  @Override
  @Nonnull
  public List<String> getNullabilityAnnotations(@Nonnull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> Collections.singletonList("org.eclipse.jdt.annotation.NonNull");
      case NULLABLE -> Collections.singletonList("org.eclipse.jdt.annotation.Nullable");
      default -> Collections.emptyList();
    };
  }
}
