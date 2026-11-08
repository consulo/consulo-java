// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.codeInsight;

import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.util.CachedValueProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.util.lang.StringUtil;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility which helps to detect annotation in `Dumb mode`.
 */
public final class DumbAwareAnnotationUtil {
  private static final String JAVA_LANG_PACKAGE = "java.lang";

  /**
   * Represents a list of fully qualified names for annotations that are treated as a type annotations.
   */
  private static final Set<String> KNOWN_ANNOTATIONS = Set.of(
    AnnotationUtil.NOT_NULL,
    AnnotationUtil.NULLABLE,
    AnnotationUtil.NON_NLS,
    AnnotationUtil.J_SPECIFY_NON_NULL,
    AnnotationUtil.J_SPECIFY_NULLABLE
  );

  /**
   * Represents a mapping from a fully qualified name of a module to a set of fully qualified names of annotations
   * that are treated as type annotations and located in this module
   */
  private static final Map<String, Set<String>> KNOWN_MODULE_TO_ANNOTATIONS_MAP = Map.of(
    "org.jetbrains.annotations", Set.of(AnnotationUtil.NOT_NULL, AnnotationUtil.NULLABLE, AnnotationUtil.NON_NLS),
    "org.jspecify", Set.of(AnnotationUtil.J_SPECIFY_NON_NULL, AnnotationUtil.J_SPECIFY_NULLABLE)
  );

  private DumbAwareAnnotationUtil() {
  }

  /**
   * Checks if the given `PsiModifierListOwner` has an annotation with the specified fully qualified name. If the annotation has not
   * fqn (which is more likely), it will find it if and only if it is present in {@link #KNOWN_ANNOTATIONS}.
   *
   * @param owner The `PsiModifierListOwner` instance to check for annotations.
   * @param fqn   The fully qualified name of the annotation to look for.
   * @return `true` if an annotation with the given fully qualified name is present, `false` otherwise.
   */
  public static boolean hasAnnotation(PsiModifierListOwner owner, String fqn) {
    for (PsiAnnotation annotation : owner.getAnnotations()) {
      if (isAnnotationMatchesFqn(annotation, fqn)) return true;
    }
    return false;
  }

  /**
   * Checks if the given annotation matches with the specified fully qualified name.
   *
   * @see #hasAnnotation
   */
  public static boolean isAnnotationMatchesFqn(PsiAnnotation annotation, String annotationFqn) {
    if (!(annotation.getContainingFile() instanceof PsiJavaFile file)) return false;
    PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
    if (referenceElement == null) return false;
    if (referenceElement.isQualified() && getCanonicalTextOfTheReference(referenceElement).equals(annotationFqn)) return true;
    PsiElement nameElement = referenceElement.getReferenceNameElement();
    if (nameElement == null) return false;
    String packageName = StringUtil.getPackageName(annotationFqn);
    String className = StringUtil.getShortName(annotationFqn);
    if (packageName.equals(JAVA_LANG_PACKAGE) && className.equals(nameElement.getText())) return true;

    return getImportedKnownAnnotations(file).contains(nameElement.getText()) && className.equals(nameElement.getText());
  }

  /**
   * Formats the given fully qualified name (FQN) by trimming whitespace around each segment.
   */
  public static String getFormattedReferenceFqn(String referenceText) {
    return Arrays.stream(referenceText.split("\\.", -1)).map(String::trim).collect(Collectors.joining("."));
  }

  private static Set<String> getImportedKnownAnnotations(PsiJavaFile file) {
    return LanguageCachedValueUtil.getCachedValue(file, () -> {
      PsiImportList importList = file.getImportList();
      if (importList == null) {
        return CachedValueProvider.Result.create(Set.of(), PsiModificationTracker.MODIFICATION_COUNT);
      }
      Set<String> filteredAnnotations = KNOWN_ANNOTATIONS.stream()
        .filter(it -> isAnnotationInImportList(it, importList) || isAnnotationInModuleImportList(it, importList))
        .map(fqn -> {
          String[] parts = fqn.split("\\.", -1);
          return parts[parts.length - 1];
        })
        .collect(Collectors.toSet());
      return CachedValueProvider.Result.create(filteredAnnotations, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  private static boolean isAnnotationInImportList(String annotationFqn, PsiImportList importList) {
    String packageName = StringUtil.getPackageName(annotationFqn);
    for (PsiImportStatement statement : importList.getImportStatements()) {
      PsiJavaCodeReferenceElement referenceElement = statement.getImportReference();
      if (referenceElement == null) continue;
      String referenceElementText = getCanonicalTextOfTheReference(referenceElement);
      if (referenceElementText.equals(annotationFqn) || statement.isOnDemand() && referenceElementText.startsWith(packageName)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isAnnotationInModuleImportList(String annotationFqn, PsiImportList moduleList) {
    if (!PsiUtil.isAvailable(JavaFeature.MODULE_IMPORT_DECLARATIONS, moduleList)) return false;
    for (PsiImportModuleStatement statement : moduleList.getImportModuleStatements()) {
      String referenceName = statement.getReferenceName();
      if (referenceName == null) continue;
      Set<String> annotations = KNOWN_MODULE_TO_ANNOTATIONS_MAP.get(getFormattedReferenceFqn(referenceName));
      if (annotations != null && annotations.contains(annotationFqn)) return true;
    }
    return false;
  }

  private static String getCanonicalTextOfTheReference(PsiJavaCodeReferenceElement reference) {
    return getFormattedReferenceFqn(reference.getText());
  }
}
