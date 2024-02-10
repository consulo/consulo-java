/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight;

import com.intellij.java.indexing.search.searches.AnnotatedElementsSearch;
import com.intellij.java.indexing.search.searches.DirectClassInheritorsSearch;
import com.intellij.java.language.codeInsight.AnnotationTargetUtil;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.application.util.ConcurrentFactoryMap;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.HashingStrategy;
import consulo.util.collection.Sets;
import consulo.util.dataholder.Key;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * @since 2016.3
 */
public class MetaAnnotationUtil {
  private static final HashingStrategy<PsiClass> HASHING_STRATEGY = new HashingStrategy<PsiClass>() {
    public int hashCode(final PsiClass object) {
      final String qualifiedName = object.getQualifiedName();
      return qualifiedName == null ? 0 : qualifiedName.hashCode();
    }

    public boolean equals(final PsiClass o1, final PsiClass o2) {
      return Comparing.equal(o1.getQualifiedName(), o2.getQualifiedName());
    }
  };

  public static Collection<PsiClass> getAnnotationTypesWithChildren(@Nonnull final Module module, final String annotationName, final boolean includeTests) {
    final Project project = module.getProject();

    Map<Pair<String, Boolean>, Collection<PsiClass>> map = CachedValuesManager.getManager(project).getCachedValue(module, () ->
    {
      Map<Pair<String, Boolean>, Collection<PsiClass>> factoryMap = ConcurrentFactoryMap.createMap(key ->
      {
        GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, key.getSecond());

        PsiClass annotationClass = JavaPsiFacade.getInstance(project).findClass(key.getFirst(), moduleScope);
        if (annotationClass == null || !annotationClass.isAnnotationType()) {
          return Collections.emptyList();
        }

        // limit search to files containing annotations
        GlobalSearchScope effectiveSearchScope = getAllAnnotationFilesScope(project).intersectWith(moduleScope);
        return getAnnotationTypesWithChildren(annotationClass, effectiveSearchScope);
      });
      return CachedValueProvider.Result.create(factoryMap, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });

    return map.get(Pair.create(annotationName, includeTests));
  }

  public static Set<PsiClass> getChildren(final PsiClass psiClass, final GlobalSearchScope scope) {
    if (AnnotationTargetUtil.findAnnotationTarget(psiClass, PsiAnnotation.TargetType.ANNOTATION_TYPE, PsiAnnotation.TargetType.TYPE) == null) {
      return Collections.emptySet();
    }

    final String name = psiClass.getQualifiedName();
    if (name == null) {
      return Collections.emptySet();
    }

    final Set<PsiClass> result = Sets.newHashSet(HASHING_STRATEGY);

    AnnotatedElementsSearch.searchPsiClasses(psiClass, scope).forEach(processorResult ->
    {
      if (processorResult.isAnnotationType()) {
        result.add(processorResult);
      }
      return true;
    });

    return result;
  }

  public static Collection<PsiClass> getAnnotatedTypes(final Module module, final Key<CachedValue<Collection<PsiClass>>> key, final String annotationName) {
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, key, () ->
    {
      final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
      final PsiClass psiClass = JavaPsiFacade.getInstance(module.getProject()).findClass(annotationName, scope);

      final Collection<PsiClass> classes;
      if (psiClass == null || !psiClass.isAnnotationType()) {
        classes = Collections.emptyList();
      } else {
        classes = getChildren(psiClass, scope);
      }
      return new CachedValueProvider.Result<>(classes, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
    }, false);
  }

  @Nonnull
  private static Collection<PsiClass> getAnnotationTypesWithChildren(PsiClass annotationClass, GlobalSearchScope scope) {
    final Set<PsiClass> classes = Sets.newHashSet(HASHING_STRATEGY);

    collectClassWithChildren(annotationClass, classes, scope);

    return classes;
  }

  private static GlobalSearchScope getAllAnnotationFilesScope(Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () ->
    {
      GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      Set<VirtualFile> allAnnotationFiles = new HashSet<>();
      for (PsiClass javaLangAnnotation : JavaPsiFacade.getInstance(project).findClasses(JavaClassNames.JAVA_LANG_ANNOTATION_ANNOTATION, scope)) {
        DirectClassInheritorsSearch.search(javaLangAnnotation, scope, false).forEach(annotationClass ->
        {
          ContainerUtil.addIfNotNull(allAnnotationFiles, PsiUtilCore.getVirtualFile(annotationClass));
          return true;
        });
      }
      return CachedValueProvider.Result.createSingleDependency(GlobalSearchScope.filesWithLibrariesScope(project, allAnnotationFiles), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  private static void collectClassWithChildren(final PsiClass psiClass, final Set<PsiClass> classes, final GlobalSearchScope scope) {
    classes.add(psiClass);

    for (PsiClass aClass : getChildren(psiClass, scope)) {
      if (!classes.contains(aClass)) {
        collectClassWithChildren(aClass, classes, scope);
      }
    }
  }

  /**
   * Check if listOwner is annotated with annotations or listOwner's annotations contain given annotations
   */
  public static boolean isMetaAnnotated(@Nonnull PsiModifierListOwner listOwner, @Nonnull final Collection<String> annotations) {
    if (AnnotationUtil.isAnnotated(listOwner, annotations, false)) {
      return true;
    }

    final List<PsiClass> resolvedAnnotations = getResolvedClassesInAnnotationsList(listOwner);
    for (String annotationFQN : annotations) {
      for (PsiClass resolvedAnnotation : resolvedAnnotations) {
        if (metaAnnotationCached(resolvedAnnotation, annotationFQN) != null) {
          return true;
        }
      }
    }

    return false;
  }

  @Nullable
  private static PsiAnnotation metaAnnotationCached(PsiClass subjectAnnotation, String annotationToFind) {
    ConcurrentMap<String, PsiAnnotation> cachedValue = LanguageCachedValueUtil.getCachedValue(subjectAnnotation, () ->
    {
      ConcurrentMap<String, PsiAnnotation> metaAnnotationsMap = ConcurrentFactoryMap.createMap(anno -> findMetaAnnotation(subjectAnnotation, anno, new HashSet<>()));
      return new CachedValueProvider.Result<>(metaAnnotationsMap, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
    return cachedValue.get(annotationToFind);
  }

  @Nullable
  private static PsiAnnotation findMetaAnnotation(PsiClass aClass, final String annotation, final Set<PsiClass> visited) {
    PsiAnnotation directAnnotation = AnnotationUtil.findAnnotation(aClass, annotation);
    if (directAnnotation != null) {
      return directAnnotation;
    }
    List<PsiClass> resolvedAnnotations = getResolvedClassesInAnnotationsList(aClass);
    for (PsiClass resolvedAnnotation : resolvedAnnotations) {
      if (visited.add(resolvedAnnotation)) {
        PsiAnnotation annotated = findMetaAnnotation(resolvedAnnotation, annotation, visited);
        if (annotated != null) {
          return annotated;
        }
      }
    }

    return null;
  }


  @Nonnull
  public static Stream<PsiAnnotation> findMetaAnnotations(@Nonnull PsiModifierListOwner listOwner, @Nonnull final Collection<String> annotations) {
    Stream<PsiAnnotation> directAnnotations = Stream.of(AnnotationUtil.findAnnotations(listOwner, annotations));

    Stream<PsiClass> lazyResolvedAnnotations = Stream.generate(() -> getResolvedClassesInAnnotationsList(listOwner)).limit(1).flatMap(it -> it.stream());

    Stream<PsiAnnotation> metaAnnotations = lazyResolvedAnnotations.flatMap(psiClass -> annotations.stream().map(annotationFQN -> metaAnnotationCached(psiClass, annotationFQN))).filter
        (Objects::nonNull);

    return Stream.concat(directAnnotations, metaAnnotations);
  }


  private static List<PsiClass> getResolvedClassesInAnnotationsList(PsiModifierListOwner owner) {
    PsiModifierList modifierList = owner.getModifierList();
    if (modifierList != null) {
      return ContainerUtil.mapNotNull(modifierList.getApplicableAnnotations(), psiAnnotation ->
      {
        PsiJavaCodeReferenceElement nameReferenceElement = psiAnnotation.getNameReferenceElement();
        PsiElement resolve = nameReferenceElement != null ? nameReferenceElement.resolve() : null;
        return resolve instanceof PsiClass && ((PsiClass) resolve).isAnnotationType() ? (PsiClass) resolve : null;
      });
    }
    return Collections.emptyList();
  }
}
