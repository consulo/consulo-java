/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.testIntegration.createTest;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.testIntegration.TestFramework;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ContentFolder;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.undoRedo.CommandProcessor;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.CreateTestAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class CreateTestAction extends PsiElementBaseIntentionAction {

  private static final String CREATE_TEST_IN_THE_SAME_ROOT = "create.test.in.the.same.root";

  @Nonnull
  public String getText() {
    return CodeInsightBundle.message("intention.create.test");
  }

  @Nonnull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    if (!isAvailableForElement(element)) return false;

    PsiClass psiClass = getContainingClass(element);

    assert psiClass != null;
    PsiElement leftBrace = psiClass.getLBrace();
    if (leftBrace == null) return false;
    if (element.getTextOffset() >= leftBrace.getTextOffset()) return false;

    //TextRange declarationRange = HighlightNamesUtil.getClassDeclarationTextRange(psiClass);
    //if (!declarationRange.contains(element.getTextRange())) return false;

    return true;
  }

  @RequiredReadAction
  public static boolean isAvailableForElement(PsiElement element) {
    if (!TestFramework.EXTENSION_NAME.hasAnyExtensions()) return false;

    if (element == null) return false;

    PsiClass psiClass = getContainingClass(element);

    if (psiClass == null) return false;

    Module srcModule = psiClass.getModule();
    if (srcModule == null) return false;

    if (psiClass.isAnnotationType() ||
        psiClass instanceof PsiAnonymousClass ||
        PsiTreeUtil.getParentOfType(psiClass, PsiClass.class) != null || // inner
        isUnderTestSources(psiClass)) {
      return false;
    }
    return true;
  }

  private static boolean isUnderTestSources(PsiClass c) {
    ProjectRootManager rm = ProjectRootManager.getInstance(c.getProject());
    VirtualFile f = c.getContainingFile().getVirtualFile();
    if (f == null) return false;
    return rm.getFileIndex().isInTestSourceContent(f);
  }

  @Override
  public void invoke(final @jakarta.annotation.Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
    final Module srcModule = ModuleUtilCore.findModuleForPsiElement(element);
    final PsiClass srcClass = getContainingClass(element);

    if (srcClass == null) return;

    PsiDirectory srcDir = element.getContainingFile().getContainingDirectory();
    PsiJavaPackage srcPackage = JavaDirectoryService.getInstance().getPackage(srcDir);

    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    final HashSet<VirtualFile> testFolders = new HashSet<VirtualFile>();
    checkForTestRoots(srcModule, testFolders);
    if (testFolders.isEmpty() && !propertiesComponent.getBoolean(CREATE_TEST_IN_THE_SAME_ROOT, false)) {
      if (Messages.showOkCancelDialog(project, "Create test in the same source root?", "No Test Roots Found", Messages.getQuestionIcon()) !=
          DialogWrapper.OK_EXIT_CODE) {
        return;
      }

      propertiesComponent.setValue(CREATE_TEST_IN_THE_SAME_ROOT, String.valueOf(true));
    }

    final CreateTestDialog d = new CreateTestDialog(project, getText(), srcClass, srcPackage, srcModule);
    d.show();
    if (!d.isOK()) return;

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        TestFramework framework = d.getSelectedTestFrameworkDescriptor();
        TestGenerator generator = TestGenerator.forLanguage(framework.getLanguage());
        generator.generateTest(project, d);
      }
    }, CodeInsightBundle.message("intention.create.test"), this);
  }

  protected static void checkForTestRoots(Module srcModule, Set<VirtualFile> testFolders) {
    checkForTestRoots(srcModule, testFolders, new HashSet<Module>());
  }

  private static void checkForTestRoots(final Module srcModule, final Set<VirtualFile> testFolders, final Set<Module> processed) {
    final boolean isFirst = processed.isEmpty();
    if (!processed.add(srcModule)) return;

    final ContentEntry[] entries = ModuleRootManager.getInstance(srcModule).getContentEntries();
    for (ContentEntry entry : entries) {
      for (ContentFolder sourceFolder : entry.getFolders(LanguageContentFolderScopes.of(TestContentFolderTypeProvider.getInstance()))) {
        final VirtualFile sourceFolderFile = sourceFolder.getFile();
        if (sourceFolderFile != null) {
          testFolders.add(sourceFolderFile);
        }
      }
    }
    if (isFirst && !testFolders.isEmpty()) return;

    final HashSet<Module> modules = new HashSet<Module>();
    ModuleUtilCore.collectModulesDependsOn(srcModule, modules);
    for (Module module : modules) {

      checkForTestRoots(module, testFolders, processed);
    }
  }

  @Nullable
  private static PsiClass getContainingClass(PsiElement element) {
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
    if (psiClass == null) {
      final PsiFile containingFile = element.getContainingFile();
      if (containingFile instanceof PsiClassOwner) {
        final PsiClass[] classes = ((PsiClassOwner)containingFile).getClasses();
        if (classes.length == 1) {
          return classes[0];
        }
      }
    }
    return psiClass;
  }

  public boolean startInWriteAction() {
    return false;
  }
}