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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.java.impl.codeInsight.daemon.quickFix.ExternalLibraryResolver;
import com.intellij.java.impl.codeInsight.daemon.quickFix.ExternalLibraryResolver.ExternalClassResolveResult;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.source.PsiJavaModuleReferenceImpl;
import com.intellij.java.language.projectRoots.roots.ExternalLibraryDescriptor;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiImportStatement;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.Library;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.QuickFixActionRegistrar;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.packageDependency.DependencyValidationManager;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.module.Module;
import consulo.module.content.ModuleFileIndex;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.orderEntry.DependencyScope;
import consulo.module.content.layer.orderEntry.ExportableOrderEntry;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.module.content.util.ModuleRootModificationUtil;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ThreeState;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author cdr
 */
public abstract class OrderEntryFix implements SyntheticIntentionAction, LocalQuickFix {
  protected OrderEntryFix() {
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  @Nonnull
  public String getName() {
    return getText();
  }

  @Override
  public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
    invoke(project, null, descriptor.getPsiElement().getContainingFile());
  }

  @Nullable
  public static List<LocalQuickFix> registerFixes(@Nonnull QuickFixActionRegistrar registrar, @Nonnull PsiReference reference) {
    PsiElement psiElement = reference.getElement();
    String shortReferenceName = reference.getRangeInElement().substring(psiElement.getText());

    Project project = psiElement.getProject();
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) {
      return null;
    }
    VirtualFile refVFile = containingFile.getVirtualFile();
    if (refVFile == null) {
      return null;
    }

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Module currentModule = fileIndex.getModuleForFile(refVFile);
    if (currentModule == null) {
      return null;
    }

    if (reference instanceof PsiJavaModuleReferenceImpl) {
      List<LocalQuickFix> result = new ArrayList<>();
      createModuleFixes((PsiJavaModuleReferenceImpl) reference, currentModule, refVFile, result);
      result.forEach(fix -> registrar.register((IntentionAction) fix));
      return result;
    }

    List<LocalQuickFix> result = new ArrayList<>();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(psiElement.getProject());

    registerExternalFixes(registrar, reference, psiElement, shortReferenceName, facade, currentModule, result);
    if (!result.isEmpty()) {
      return result;
    }

    PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(shortReferenceName, GlobalSearchScope.allScope(project));
    List<PsiClass> allowedDependencies = filterAllowedDependencies(psiElement, classes);
    if (allowedDependencies.isEmpty()) {
      return result;
    }

    OrderEntryFix moduleDependencyFix = new AddModuleDependencyFix(currentModule, refVFile, allowedDependencies, reference);
    registrar.register(moduleDependencyFix);
    result.add(moduleDependencyFix);

    Set<Object> librariesToAdd = new HashSet<>();
    ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(currentModule).getFileIndex();
    for (PsiClass aClass : allowedDependencies) {
      if (!facade.getResolveHelper().isAccessible(aClass, psiElement, aClass)) {
        continue;
      }
      PsiFile psiFile = aClass.getContainingFile();
      if (psiFile == null) {
        continue;
      }
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) {
        continue;
      }
      for (OrderEntry orderEntry : fileIndex.getOrderEntriesForFile(virtualFile)) {
        if (orderEntry instanceof LibraryOrderEntry) {
          final LibraryOrderEntry libraryEntry = (LibraryOrderEntry) orderEntry;
          final Library library = libraryEntry.getLibrary();
          if (library == null) {
            continue;
          }
          VirtualFile[] files = library.getFiles(BinariesOrderRootType.getInstance());
          if (files.length == 0) {
            continue;
          }
          final VirtualFile jar = files[0];

          if (jar == null || libraryEntry.isModuleLevel() && !librariesToAdd.add(jar) || !librariesToAdd.add(library)) {
            continue;
          }
          OrderEntry entryForFile = moduleFileIndex.getOrderEntryForFile(virtualFile);
          if (entryForFile != null && !(entryForFile instanceof ExportableOrderEntry && ((ExportableOrderEntry) entryForFile).getScope() == DependencyScope.TEST && !moduleFileIndex
              .isInTestSourceContent(refVFile))) {
            continue;
          }

          OrderEntryFix platformFix = new AddLibraryToDependenciesFix(currentModule, library, reference, aClass.getQualifiedName());
          registrar.register(platformFix);
          result.add(platformFix);
        }
      }
    }

    return result;
  }

  private static void createModuleFixes(PsiJavaModuleReferenceImpl reference, Module currentModule, VirtualFile refVFile, List<LocalQuickFix> result) {
    ProjectFileIndex index = ProjectRootManager.getInstance(currentModule.getProject()).getFileIndex();
    List<PsiElement> targets = Stream.of(reference.multiResolve(true)).map(ResolveResult::getElement).filter(Objects::nonNull).collect(Collectors.toList());

    Set<Module> modules = targets.stream().map(e -> !(e instanceof PsiCompiledElement) ? e.getContainingFile() : null).map(f -> f != null ? f.getVirtualFile() : null).filter(vf -> vf != null &&
        index.isInSource(vf)).map(vf -> index.getModuleForFile(vf)).filter(m -> m != null && m != currentModule).collect(Collectors.toSet());
    if (!modules.isEmpty()) {
      result.add(0, new AddModuleDependencyFix(currentModule, refVFile, modules, reference));
    }

    targets.stream().map(e -> e instanceof PsiCompiledElement ? e.getContainingFile() : null).map(f -> f != null ? f.getVirtualFile() : null).flatMap(vf -> vf != null ? index
        .getOrderEntriesForFile(vf).stream() : Stream.empty()).map(e -> e instanceof LibraryOrderEntry ? ((LibraryOrderEntry) e).getLibrary() : null).filter(Objects::nonNull).distinct()
        .forEach(l -> result.add(new AddLibraryToDependenciesFix(currentModule, l, reference, null)));
  }

  private static void registerExternalFixes(@Nonnull QuickFixActionRegistrar registrar,
                                            @Nonnull PsiReference reference,
                                            PsiElement psiElement,
                                            String shortReferenceName,
                                            JavaPsiFacade facade,
                                            Module currentModule,
                                            List<LocalQuickFix> result) {
    String fullReferenceText = reference.getCanonicalText();
    for (ExternalLibraryResolver resolver : ExternalLibraryResolver.EP_NAME.getExtensionList()) {
      ExternalClassResolveResult resolveResult = resolver.resolveClass(shortReferenceName, isReferenceToAnnotation(psiElement), currentModule);
      OrderEntryFix fix = null;
      if (resolveResult != null && facade.findClass(resolveResult.getQualifiedClassName(), GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(currentModule, true)) == null) {
        fix = new AddExternalLibraryToDependenciesQuickFix(currentModule, resolveResult.getLibrary(), reference, resolveResult.getQualifiedClassName());
      } else if (!fullReferenceText.equals(shortReferenceName)) {
        ExternalLibraryDescriptor descriptor = resolver.resolvePackage(fullReferenceText);
        if (descriptor != null) {
          fix = new AddExternalLibraryToDependenciesQuickFix(currentModule, descriptor, reference, null);
        }
      }
      if (fix != null) {
        registrar.register(fix);
        result.add(fix);
      }
    }
  }

  private static List<PsiClass> filterAllowedDependencies(PsiElement element, PsiClass[] classes) {
    DependencyValidationManager dependencyValidationManager = DependencyValidationManager.getInstance(element.getProject());
    PsiFile fromFile = element.getContainingFile();
    List<PsiClass> result = new ArrayList<>();
    for (PsiClass psiClass : classes) {
      PsiFile containingFile = psiClass.getContainingFile();
      if (containingFile != null && dependencyValidationManager.getViolatorDependencyRule(fromFile, containingFile) == null) {
        result.add(psiClass);
      }
    }
    return result;
  }

  private static ThreeState isReferenceToAnnotation(final PsiElement psiElement) {
    if (psiElement.getLanguage() == JavaLanguage.INSTANCE && !PsiUtil.isLanguageLevel5OrHigher(psiElement)) {
      return ThreeState.NO;
    }
    if (PsiTreeUtil.getParentOfType(psiElement, PsiAnnotation.class) != null) {
      return ThreeState.YES;
    }
    if (PsiTreeUtil.getParentOfType(psiElement, PsiImportStatement.class) != null) {
      return ThreeState.UNSURE;
    }
    return ThreeState.NO;
  }

  public static void importClass(@Nonnull Module currentModule, @Nullable Editor editor, @Nullable PsiReference reference, @Nullable String className) {
    Project project = currentModule.getProject();
    if (editor != null && reference != null && className != null) {
      DumbService.getInstance(project).withAlternativeResolveEnabled(() ->
      {
        GlobalSearchScope scope = GlobalSearchScope.moduleWithLibrariesScope(currentModule);
        PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(className, scope);
        if (aClass != null) {
          new AddImportAction(project, reference, editor, aClass).execute();
        }
      });
    }
  }

  public static void addJarToRoots(@Nonnull String jarPath, final @Nonnull Module module, @Nullable PsiElement location) {
    addJarsToRoots(Collections.singletonList(jarPath), null, module, location);
  }

  public static void addJarsToRoots(@Nonnull List<String> jarPaths, @Nullable String libraryName, @Nonnull Module module, @Nullable PsiElement location) {
    List<String> urls = refreshAndConvertToUrls(jarPaths);
    DependencyScope scope = suggestScopeByLocation(module, location);
    ModuleRootModificationUtil.addModuleLibrary(module, libraryName, urls, Collections.emptyList(), scope);
  }

  @Nonnull
  public static List<String> refreshAndConvertToUrls(@Nonnull List<String> jarPaths) {
    return ContainerUtil.map(jarPaths, OrderEntryFix::refreshAndConvertToUrl);
  }

  @Nonnull
  public static DependencyScope suggestScopeByLocation(@Nonnull Module module, @Nullable PsiElement location) {
    if (location != null) {
      final VirtualFile vFile = location.getContainingFile().getVirtualFile();
      if (vFile != null && ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(vFile)) {
        return DependencyScope.TEST;
      }
    }
    return DependencyScope.COMPILE;
  }

  @Nonnull
  private static String refreshAndConvertToUrl(String jarPath) {
    final File libraryRoot = new File(jarPath);
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libraryRoot);
    return VirtualFileUtil.getUrlForLibraryRoot(libraryRoot);
  }
}