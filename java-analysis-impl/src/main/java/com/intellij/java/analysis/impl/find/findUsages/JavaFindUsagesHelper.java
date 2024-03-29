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
package com.intellij.java.analysis.impl.find.findUsages;

import com.intellij.java.analysis.impl.psi.impl.search.ThrowSearchUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.FunctionalExpressionSearch;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.targets.AliasingPsiTarget;
import com.intellij.java.language.psi.targets.AliasingPsiTargetMapper;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.util.ReadActionProcessor;
import consulo.application.util.function.Processor;
import consulo.component.extension.Extensions;
import consulo.content.scope.SearchScope;
import consulo.document.util.TextRange;
import consulo.find.FindBundle;
import consulo.find.FindUsagesHelper;
import consulo.find.FindUsagesOptions;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.pom.PomService;
import consulo.language.pom.PomTarget;
import consulo.language.psi.*;
import consulo.language.psi.meta.PsiMetaData;
import consulo.language.psi.meta.PsiMetaOwner;
import consulo.language.psi.resolve.PsiElementProcessorAdapter;
import consulo.language.psi.resolve.PsiReferenceProcessorAdapter;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.logging.Logger;
import consulo.usage.UsageInfo;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.xml.psi.xml.XmlAttributeValue;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;

public class JavaFindUsagesHelper {
  private static final Logger LOG = Logger.getInstance(JavaFindUsagesHelper.class);

  @Nonnull
  public static Set<String> getElementNames(@Nonnull final PsiElement element) {
    if (element instanceof PsiDirectory) {  // normalize a directory to a corresponding package
      PsiPackage aPackage = ReadAction.compute(() -> JavaDirectoryService.getInstance().getPackage((PsiDirectory) element));
      return aPackage == null ? Collections.emptySet() : getElementNames(aPackage);
    }

    final Set<String> result = new HashSet<>();

    ApplicationManager.getApplication().runReadAction(() ->
    {
      if (element instanceof PsiPackage) {
        ContainerUtil.addIfNotNull(result, ((PsiPackage) element).getQualifiedName());
      } else if (element instanceof PsiClass) {
        final String qname = ((PsiClass) element).getQualifiedName();
        if (qname != null) {
          result.add(qname);
          PsiClass topLevelClass = PsiUtil.getTopLevelClass(element);
          if (topLevelClass != null && !(topLevelClass instanceof PsiSyntheticClass)) {
            String topName = topLevelClass.getQualifiedName();
            assert topName != null : "topLevelClass : " + topLevelClass + "; element: " + element + " (" + qname + ") top level file: " + InjectedLanguageManager.getInstance(element
                .getProject()).getTopLevelFile(element);
            if (qname.length() > topName.length()) {
              result.add(topName + qname.substring(topName.length()).replace('.', '$'));
            }
          }
        }
      } else if (element instanceof PsiMethod) {
        ContainerUtil.addIfNotNull(result, ((PsiMethod) element).getName());
      } else if (element instanceof PsiVariable) {
        ContainerUtil.addIfNotNull(result, ((PsiVariable) element).getName());
      } else if (element instanceof PsiMetaOwner) {
        final PsiMetaData metaData = ((PsiMetaOwner) element).getMetaData();
        if (metaData != null) {
          ContainerUtil.addIfNotNull(result, metaData.getName());
        }
      } else if (element instanceof PsiNamedElement) {
        ContainerUtil.addIfNotNull(result, ((PsiNamedElement) element).getName());
      } else if (element instanceof XmlAttributeValue) {
        ContainerUtil.addIfNotNull(result, ((XmlAttributeValue) element).getValue());
      } else {
        LOG.error("Unknown element type: " + element);
      }
    });

    return result;
  }

  public static boolean processElementUsages(@Nonnull final PsiElement element, @Nonnull final FindUsagesOptions options, @Nonnull final Processor<UsageInfo> processor) {
    if (options instanceof JavaVariableFindUsagesOptions) {
      final JavaVariableFindUsagesOptions varOptions = (JavaVariableFindUsagesOptions) options;
      if (varOptions.isReadAccess || varOptions.isWriteAccess) {
        if (varOptions.isReadAccess && varOptions.isWriteAccess) {
          if (!addElementUsages(element, options, processor)) {
            return false;
          }
        } else {
          if (!addElementUsages(element, varOptions, info ->
          {
            final PsiElement element1 = info.getElement();
            boolean isWrite = element1 instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression) element1);
            if (isWrite == varOptions.isWriteAccess) {
              if (!processor.process(info)) {
                return false;
              }
            }
            return true;
          })) {
            return false;
          }
        }
      }
    } else if (options.isUsages) {
      if (!addElementUsages(element, options, processor)) {
        return false;
      }
    }

    boolean success = ReadAction.compute(() ->
    {
      if (ThrowSearchUtil.isSearchable(element) && options instanceof JavaThrowFindUsagesOptions && options.isUsages) {
        ThrowSearchUtil.Root root = ((JavaThrowFindUsagesOptions) options).getRoot();
        if (root == null) {
          final ThrowSearchUtil.Root[] roots = ThrowSearchUtil.getSearchRoots(element);
          if (roots != null && roots.length > 0) {
            root = roots[0];
          }
        }
        if (root != null) {
          return ThrowSearchUtil.addThrowUsages(processor, root, options);
        }
      }
      return true;
    });
    if (!success) {
      return false;
    }

    if (options instanceof JavaPackageFindUsagesOptions && ((JavaPackageFindUsagesOptions) options).isClassesUsages) {
      if (!addClassesUsages((PsiPackage) element, (JavaPackageFindUsagesOptions) options, processor)) {
        return false;
      }
    }

    if (options instanceof JavaClassFindUsagesOptions) {
      final JavaClassFindUsagesOptions classOptions = (JavaClassFindUsagesOptions) options;
      final PsiClass psiClass = (PsiClass) element;
      PsiManager manager = ReadAction.compute(psiClass::getManager);
      if (classOptions.isMethodsUsages) {
        if (!addMethodsUsages(psiClass, manager, classOptions, processor)) {
          return false;
        }
      }
      if (classOptions.isFieldsUsages) {
        if (!addFieldsUsages(psiClass, manager, classOptions, processor)) {
          return false;
        }
      }
      if (ReadAction.compute(psiClass::isInterface)) {
        if (classOptions.isDerivedInterfaces) {
          if (classOptions.isImplementingClasses) {
            if (!addInheritors(psiClass, classOptions, processor)) {
              return false;
            }
          } else {
            if (!addDerivedInterfaces(psiClass, classOptions, processor)) {
              return false;
            }
          }
        } else if (classOptions.isImplementingClasses) {
          if (!addImplementingClasses(psiClass, classOptions, processor)) {
            return false;
          }
        }

        if (classOptions.isImplementingClasses) {
          FunctionalExpressionSearch.search(psiClass, classOptions.searchScope).forEach(new PsiElementProcessorAdapter<>(expression -> addResult(expression, options, processor)));
        }
      } else if (classOptions.isDerivedClasses) {
        if (!addInheritors(psiClass, classOptions, processor)) {
          return false;
        }
      }
    }

    if (options instanceof JavaMethodFindUsagesOptions) {
      final PsiMethod psiMethod = (PsiMethod) element;
      boolean isAbstract = ReadAction.compute(() -> psiMethod.hasModifierProperty(PsiModifier.ABSTRACT));
      final JavaMethodFindUsagesOptions methodOptions = (JavaMethodFindUsagesOptions) options;
      if (isAbstract && methodOptions.isImplementingMethods || methodOptions.isOverridingMethods) {
        if (!processOverridingMethods(psiMethod, processor, methodOptions)) {
          return false;
        }
        FunctionalExpressionSearch.search(psiMethod, methodOptions.searchScope).forEach(new PsiElementProcessorAdapter<>(expression -> addResult(expression, options, processor)));
      }
    }

    if (element instanceof PomTarget) {
      if (!addAliasingUsages((PomTarget) element, options, processor)) {
        return false;
      }
    }
    final Boolean isSearchable = ReadAction.compute(() -> ThrowSearchUtil.isSearchable(element));
    if (!isSearchable && options.isSearchForTextOccurrences && options.searchScope instanceof GlobalSearchScope) {
      Collection<String> stringsToSearch = ApplicationManager.getApplication().runReadAction((Supplier<Collection<String>>) () -> getElementNames(element));
      // todo add to fastTrack
      if (!FindUsagesHelper.processUsagesInText(element, stringsToSearch, (GlobalSearchScope) options.searchScope, processor)) {
        return false;
      }
    }
    return true;
  }

  private static boolean addAliasingUsages(@Nonnull PomTarget pomTarget, @Nonnull final FindUsagesOptions options, @Nonnull final Processor<UsageInfo> processor) {
    for (AliasingPsiTargetMapper aliasingPsiTargetMapper : Extensions.getExtensions(AliasingPsiTargetMapper.EP_NAME)) {
      for (final AliasingPsiTarget psiTarget : aliasingPsiTargetMapper.getTargets(pomTarget)) {
        boolean success = ReferencesSearch.search(new ReferencesSearch.SearchParameters(ReadAction.compute(() -> PomService.convertToPsi(psiTarget)), options.searchScope, false, options
            .fastTrack)).forEach(new ReadActionProcessor<PsiReference>() {
          @Override
          public boolean processInReadAction(final PsiReference reference) {
            return addResult(reference, options, processor);
          }
        });
        if (!success) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean processOverridingMethods(@Nonnull PsiMethod psiMethod, @Nonnull final Processor<UsageInfo> processor, @Nonnull final JavaMethodFindUsagesOptions options) {
    return OverridingMethodsSearch.search(psiMethod, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<>(element -> addResult(element
        .getNavigationElement(), options, processor)));
  }

  private static boolean addClassesUsages(@Nonnull PsiPackage aPackage, @Nonnull final JavaPackageFindUsagesOptions options, @Nonnull final Processor<UsageInfo> processor) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.pushState();
    }

    try {
      List<PsiClass> classes = new ArrayList<>();
      addClassesInPackage(aPackage, options.isIncludeSubpackages, classes);
      for (final PsiClass aClass : classes) {
        if (progress != null) {
          String name = ReadAction.compute(aClass::getName);
          progress.setText(FindBundle.message("find.searching.for.references.to.class.progress", name));
        }
        ProgressManager.checkCanceled();
        boolean success = ReferencesSearch.search(new ReferencesSearch.SearchParameters(aClass, options.searchScope, false, options.fastTrack)).forEach(new ReadActionProcessor<PsiReference>() {
          @Override
          public boolean processInReadAction(final PsiReference psiReference) {
            return addResult(psiReference, options, processor);
          }
        });
        if (!success) {
          return false;
        }
      }
    } finally {
      if (progress != null) {
        progress.popState();
      }
    }

    return true;
  }

  private static void addClassesInPackage(@Nonnull final PsiPackage aPackage, boolean includeSubpackages, @Nonnull List<PsiClass> array) {
    PsiDirectory[] dirs = ReadAction.compute(aPackage::getDirectories);
    for (PsiDirectory dir : dirs) {
      addClassesInDirectory(dir, includeSubpackages, array);
    }
  }

  private static void addClassesInDirectory(@Nonnull final PsiDirectory dir, final boolean includeSubdirs, @Nonnull final List<PsiClass> array) {
    ApplicationManager.getApplication().runReadAction(() ->
    {
      PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(dir);
      ContainerUtil.addAll(array, classes);
      if (includeSubdirs) {
        PsiDirectory[] dirs = dir.getSubdirectories();
        for (PsiDirectory directory : dirs) {
          addClassesInDirectory(directory, true, array);
        }
      }
    });
  }

  private static boolean addMethodsUsages(@Nonnull final PsiClass aClass,
                                          @Nonnull final PsiManager manager,
                                          @Nonnull final JavaClassFindUsagesOptions options,
                                          @Nonnull final Processor<UsageInfo> processor) {
    if (options.isIncludeInherited) {
      final PsiMethod[] methods = ReadAction.compute(aClass::getAllMethods);
      for (int i = 0; i < methods.length; i++) {
        final PsiMethod method = methods[i];
        // filter overridden methods
        final int finalI = i;
        final PsiClass methodClass = ReadAction.compute(() ->
        {
          MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
          for (int j = 0; j < finalI; j++) {
            if (methodSignature.equals(methods[j].getSignature(PsiSubstitutor.EMPTY))) {
              return null;
            }
          }
          return method.getContainingClass();
        });
        if (methodClass == null) {
          continue;
        }
        boolean equivalent = ReadAction.compute(() -> manager.areElementsEquivalent(methodClass, aClass));
        if (equivalent) {
          if (!addElementUsages(method, options, processor)) {
            return false;
          }
        } else {
          MethodReferencesSearch.SearchParameters parameters = new MethodReferencesSearch.SearchParameters(method, options.searchScope, true, options.fastTrack);
          boolean success = MethodReferencesSearch.search(parameters).forEach(new PsiReferenceProcessorAdapter(reference ->
          {
            addResultFromReference(reference, methodClass, manager, aClass, options, processor);
            return true;
          }));
          if (!success) {
            return false;
          }
        }
      }
    } else {
      PsiMethod[] methods = ReadAction.compute(aClass::getMethods);
      for (PsiMethod method : methods) {
        if (!addElementUsages(method, options, processor)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean addFieldsUsages(@Nonnull final PsiClass aClass,
                                         @Nonnull final PsiManager manager,
                                         @Nonnull final JavaClassFindUsagesOptions options,
                                         @Nonnull final Processor<UsageInfo> processor) {
    if (options.isIncludeInherited) {
      final PsiField[] fields = ReadAction.compute(aClass::getAllFields);
      for (int i = 0; i < fields.length; i++) {
        final PsiField field = fields[i];
        // filter hidden fields
        final int finalI = i;
        final PsiClass fieldClass = ReadAction.compute(() ->
        {
          for (int j = 0; j < finalI; j++) {
            if (Comparing.strEqual(field.getName(), fields[j].getName())) {
              return null;
            }
          }
          return field.getContainingClass();
        });
        if (fieldClass == null) {
          continue;
        }
        boolean equivalent = ReadAction.compute(() -> manager.areElementsEquivalent(fieldClass, aClass));
        if (equivalent) {
          if (!addElementUsages(fields[i], options, processor)) {
            return false;
          }
        } else {
          boolean success = ReferencesSearch.search(new ReferencesSearch.SearchParameters(field, options.searchScope, false, options.fastTrack)).forEach(new
                                                                                                                                                             ReadActionProcessor<PsiReference>() {
                                                                                                                                                               @Override
                                                                                                                                                               public boolean processInReadAction(final PsiReference reference) {
                                                                                                                                                                 return addResultFromReference(reference, fieldClass, manager, aClass, options, processor);
                                                                                                                                                               }
                                                                                                                                                             });
          if (!success) {
            return false;
          }
        }
      }
    } else {
      PsiField[] fields = ReadAction.compute(aClass::getFields);
      for (PsiField field : fields) {
        if (!addElementUsages(field, options, processor)) {
          return false;
        }
      }
    }
    return true;
  }

  @Nullable
  private static PsiClass getFieldOrMethodAccessedClass(@Nonnull PsiReferenceExpression ref, @Nonnull PsiClass fieldOrMethodClass) {
    PsiElement[] children = ref.getChildren();
    if (children.length > 1 && children[0] instanceof PsiExpression) {
      PsiExpression expr = (PsiExpression) children[0];
      PsiType type = expr.getType();
      if (type != null) {
        if (!(type instanceof PsiClassType)) {
          return null;
        }
        return PsiUtil.resolveClassInType(type);
      } else {
        if (expr instanceof PsiReferenceExpression) {
          PsiElement refElement = ((PsiReferenceExpression) expr).resolve();
          if (refElement instanceof PsiClass) {
            return (PsiClass) refElement;
          }
        }
        return null;
      }
    }
    PsiManager manager = ref.getManager();
    for (PsiElement parent = ref; parent != null; parent = parent.getParent()) {
      if (parent instanceof PsiClass && (manager.areElementsEquivalent(parent, fieldOrMethodClass) || ((PsiClass) parent).isInheritor(fieldOrMethodClass, true))) {
        return (PsiClass) parent;
      }
    }
    return null;
  }

  private static boolean addInheritors(@Nonnull PsiClass aClass, @Nonnull final JavaClassFindUsagesOptions options, @Nonnull final Processor<UsageInfo> processor) {
    return ClassInheritorsSearch.search(aClass, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<>(element -> addResult(element, options, processor)));
  }

  private static boolean addDerivedInterfaces(@Nonnull PsiClass anInterface, @Nonnull final JavaClassFindUsagesOptions options, @Nonnull final Processor<UsageInfo> processor) {
    return ClassInheritorsSearch.search(anInterface, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<>(inheritor -> !inheritor.isInterface() ||
        addResult(inheritor, options, processor)));
  }

  private static boolean addImplementingClasses(@Nonnull PsiClass anInterface, @Nonnull final JavaClassFindUsagesOptions options, @Nonnull final Processor<UsageInfo> processor) {
    return ClassInheritorsSearch.search(anInterface, options.searchScope, options.isCheckDeepInheritance).forEach(new PsiElementProcessorAdapter<>(inheritor -> inheritor.isInterface() ||
        addResult(inheritor, options, processor)));
  }

  private static boolean addResultFromReference(@Nonnull PsiReference reference,
                                                @Nonnull PsiClass methodClass,
                                                @Nonnull PsiManager manager,
                                                @Nonnull PsiClass aClass,
                                                @Nonnull FindUsagesOptions options,
                                                @Nonnull Processor<UsageInfo> processor) {
    PsiElement refElement = reference.getElement();
    if (refElement instanceof PsiReferenceExpression) {
      PsiClass usedClass = getFieldOrMethodAccessedClass((PsiReferenceExpression) refElement, methodClass);
      if (usedClass != null) {
        if (manager.areElementsEquivalent(usedClass, aClass) || usedClass.isInheritor(aClass, true)) {
          if (!addResult(refElement, options, processor)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean addElementUsages(@Nonnull final PsiElement element, @Nonnull final FindUsagesOptions options, @Nonnull final Processor<UsageInfo> processor) {
    final SearchScope searchScope = options.searchScope;
    final PsiClass[] parentClass = new PsiClass[1];
    if (element instanceof PsiMethod && ReadAction.compute(() ->
    {
      PsiMethod method = (PsiMethod) element;
      parentClass[0] = method.getContainingClass();
      return method.isConstructor();
    })) {
      PsiMethod method = (PsiMethod) element;

      if (parentClass[0] != null) {
        boolean strictSignatureSearch = !(options instanceof JavaMethodFindUsagesOptions) || !((JavaMethodFindUsagesOptions) options).isIncludeOverloadUsages;
        return MethodReferencesSearch.search(new MethodReferencesSearch.SearchParameters(method, searchScope, strictSignatureSearch, options.fastTrack)).forEach(new
                                                                                                                                                                     ReadActionProcessor<PsiReference>() {
                                                                                                                                                                       @Override
                                                                                                                                                                       public boolean processInReadAction(final PsiReference ref) {
                                                                                                                                                                         return addResult(ref, options, processor);
                                                                                                                                                                       }
                                                                                                                                                                     });
      }
      return true;
    }

    final ReadActionProcessor<PsiReference> consumer = new ReadActionProcessor<PsiReference>() {
      @Override
      public boolean processInReadAction(final PsiReference ref) {
        return addResult(ref, options, processor);
      }
    };

    if (element instanceof PsiMethod) {
      final boolean strictSignatureSearch = !(options instanceof JavaMethodFindUsagesOptions) || // field with getter
          !((JavaMethodFindUsagesOptions) options).isIncludeOverloadUsages;
      return MethodReferencesSearch.search(new MethodReferencesSearch.SearchParameters((PsiMethod) element, searchScope, strictSignatureSearch, options.fastTrack)).forEach(consumer);
    }
    return ReferencesSearch.search(new ReferencesSearch.SearchParameters(element, searchScope, false, options.fastTrack)).forEach(consumer);
  }

  private static boolean addResult(@Nonnull PsiElement element, @Nonnull FindUsagesOptions options, @Nonnull Processor<UsageInfo> processor) {
    return !filterUsage(element, options) || processor.process(new UsageInfo(element));
  }

  private static boolean addResult(@Nonnull PsiReference ref, @Nonnull FindUsagesOptions options, @Nonnull Processor<UsageInfo> processor) {
    if (filterUsage(ref.getElement(), options)) {
      TextRange rangeInElement = ref.getRangeInElement();
      return processor.process(new UsageInfo(ref.getElement(), rangeInElement.getStartOffset(), rangeInElement.getEndOffset(), false));
    }
    return true;
  }

  private static boolean filterUsage(PsiElement usage, @Nonnull FindUsagesOptions options) {
    if (!(usage instanceof PsiJavaCodeReferenceElement)) {
      return true;
    }
    if (options instanceof JavaPackageFindUsagesOptions && !((JavaPackageFindUsagesOptions) options).isIncludeSubpackages && ((PsiReference) usage).resolve() instanceof PsiPackage) {
      PsiElement parent = usage.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement) parent).resolve() instanceof PsiPackage) {
        return false;
      }
    }

    if (!(usage instanceof PsiReferenceExpression)) {
      if (options instanceof JavaFindUsagesOptions && ((JavaFindUsagesOptions) options).isSkipImportStatements) {
        PsiElement parent = usage.getParent();
        while (parent instanceof PsiJavaCodeReferenceElement) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiImportStatement) {
          return false;
        }
      }

      if (options instanceof JavaPackageFindUsagesOptions && ((JavaPackageFindUsagesOptions) options).isSkipPackageStatements) {
        PsiElement parent = usage.getParent();
        while (parent instanceof PsiJavaCodeReferenceElement) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiPackageStatement) {
          return false;
        }
      }
    }
    return true;
  }
}
