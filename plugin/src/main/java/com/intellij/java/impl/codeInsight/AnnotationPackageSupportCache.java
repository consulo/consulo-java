package com.intellij.java.impl.codeInsight;

import com.intellij.java.language.annoPackages.AnnotationPackageSupport;
import com.intellij.java.language.codeInsight.Nullability;
import consulo.application.Application;
import consulo.component.extension.ExtensionPointCacheKey;
import jakarta.annotation.Nonnull;
import one.util.streamex.StreamEx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2024-03-16
 */
class AnnotationPackageSupportCache {
  private static final ExtensionPointCacheKey<AnnotationPackageSupport, AnnotationPackageSupportCache> CACHE_KEY =
    ExtensionPointCacheKey.create("AnnotationPackageSupportCache", walker -> {
      List<AnnotationPackageSupport> packageSupports = new ArrayList<>();
      walker.walk(packageSupports::add);
      return new AnnotationPackageSupportCache(packageSupports);
    });

  @Nonnull
  static AnnotationPackageSupportCache get(Application application) {
    return application.getExtensionPoint(AnnotationPackageSupport.class).getOrBuildCache(CACHE_KEY);
  }

  public final Map<String, AnnotationPackageSupport> myDefaultNullables;
  public final Map<String, AnnotationPackageSupport> myDefaultNotNulls;
  public final Map<String, AnnotationPackageSupport> myDefaultUnknowns;
  public final List<String> myDefaultAll;

  public AnnotationPackageSupportCache(List<AnnotationPackageSupport> annotationSupports) {
    myDefaultNullables = StreamEx.of(annotationSupports)
                                 .cross(s -> s.getNullabilityAnnotations(Nullability.NULLABLE).stream()).invert().toMap();
    myDefaultNotNulls = StreamEx.of(annotationSupports)
                                .cross(s -> s.getNullabilityAnnotations(Nullability.NOT_NULL).stream()).invert().toMap();
    myDefaultUnknowns = StreamEx.of(annotationSupports)
                                .cross(s -> s.getNullabilityAnnotations(Nullability.UNKNOWN).stream()).invert().toMap();
    myDefaultAll = StreamEx.of(myDefaultNullables, myDefaultNotNulls, myDefaultUnknowns).toFlatList(Map::keySet);
  }
}
