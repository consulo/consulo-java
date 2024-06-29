/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.java.impl.openapi.roots.JavaProjectModelModificationService;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.compiler.util.ModuleCompilerUtil;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.orderEntry.DependencyScope;
import consulo.module.ui.awt.ModuleListCellRenderer;
import consulo.project.Project;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.popup.AWTPopupFactory;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author anna
 * @since 20.11.2012
 */
class AddModuleDependencyFix extends AddOrderEntryFix {
  private static final Logger LOG = Logger.getInstance(AddModuleDependencyFix.class);

  private final Module myCurrentModule;
  private final VirtualFile myRefVFile;
  private final List<PsiClass> myClasses;
  private final Set<Module> myModules;

  @RequiredReadAction
  public AddModuleDependencyFix(Module currentModule, VirtualFile refVFile, List<PsiClass> classes, PsiReference reference) {
    super(reference);
    myCurrentModule = currentModule;
    myRefVFile = refVFile;
    myClasses = classes;
    myModules = new LinkedHashSet<>();

    final PsiElement psiElement = reference.getElement();
    final Project project = psiElement.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(currentModule);
    for (PsiClass aClass : classes) {
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
      Module classModule = fileIndex.getModuleForFile(virtualFile);
      if (classModule != null && classModule != currentModule && !rootManager.isDependsOn(classModule)) {
        myModules.add(classModule);
      }
    }
  }

  public AddModuleDependencyFix(Module currentModule, VirtualFile refVFile, Set<Module> modules, PsiReference reference) {
    super(reference);
    myCurrentModule = currentModule;
    myRefVFile = refVFile;
    myClasses = Collections.emptyList();
    myModules = modules;
  }

  @Override
  @Nonnull
  public String getText() {
    if (myModules.size() == 1) {
      final Module module = ContainerUtil.getFirstItem(myModules);
      LOG.assertTrue(module != null);
      return JavaQuickFixBundle.message("orderEntry.fix.add.dependency.on.module", module.getName());
    } else {
      return JavaQuickFixBundle.message("orderEntry.fix.add.dependency.on.module.choose");
    }
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("orderEntry.fix.family.add.module.dependency");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return !project.isDisposed() && !myCurrentModule.isDisposed() && !myModules.isEmpty() && myModules.stream().noneMatch(Module::isDisposed);
  }

  @Override
  public void invoke(@Nonnull Project project, @Nullable Editor editor, PsiFile file) {
    if (myModules.size() == 1) {
      addDependencyOnModule(project, editor, ContainerUtil.getFirstItem(myModules));
    } else {
      JBList<Module> list = new JBList<>(myModules);
      list.setCellRenderer(new ModuleListCellRenderer());
      JBPopup popup = ((AWTPopupFactory) JBPopupFactory.getInstance()).createListPopupBuilder(list).setItemChoosenCallback(() -> addDependencyOnModule(project, editor, list.getSelectedValue())).setTitle(JavaQuickFixBundle.message("orderEntry.fix.choose.module.to.add.dependency.on")).setMovable(false)
          .setResizable(false).setRequestFocus(true).createPopup();
      if (editor != null) {
        EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
      } else {
        popup.showCenteredInCurrentWindow(project);
      }
    }
  }

  private void addDependencyOnModule(Project project, Editor editor, @Nullable Module module) {
    if (module == null) {
      return;
    }
    Pair<Module, Module> circularModules = ModuleCompilerUtil.addingDependencyFormsCircularity(myCurrentModule, module);
    if (circularModules == null || showCircularWarning(project, circularModules, module)) {
      boolean test = ModuleRootManager.getInstance(myCurrentModule).getFileIndex().isInTestSourceContent(myRefVFile);
      DependencyScope scope = test ? DependencyScope.TEST : DependencyScope.COMPILE;
      JavaProjectModelModificationService.getInstance(project).addDependency(myCurrentModule, module, scope);

      if (editor != null && !myClasses.isEmpty()) {
        PsiClass[] targetClasses = myClasses.stream().filter(c -> ModuleUtilCore.findModuleForPsiElement(c) == module).toArray(PsiClass[]::new);
        if (targetClasses.length > 0) {
          new AddImportAction(project, myReference, editor, targetClasses).execute();
        }
      }
    }
  }

  private static boolean showCircularWarning(Project project, Pair<Module, Module> circle, Module classModule) {
    String message = JavaQuickFixBundle.message("orderEntry.fix.circular.dependency.warning", classModule.getName(), circle.getFirst().getName(), circle.getSecond().getName());
    String title = JavaQuickFixBundle.message("orderEntry.fix.title.circular.dependency.warning");
    return Messages.showOkCancelDialog(project, message, title, UIUtil.getWarningIcon()) == Messages.OK;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}