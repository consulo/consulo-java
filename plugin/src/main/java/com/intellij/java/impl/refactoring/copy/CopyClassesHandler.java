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
package com.intellij.java.impl.refactoring.copy;

import com.intellij.java.impl.refactoring.MoveDestination;
import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.ide.impl.idea.codeInsight.actions.OptimizeImportsProcessor;
import consulo.java.impl.util.JavaProjectRootsUtil;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.copy.CopyFilesOrDirectoriesDialog;
import consulo.language.editor.refactoring.copy.CopyFilesOrDirectoriesHandler;
import consulo.language.editor.refactoring.copy.CopyHandler;
import consulo.language.editor.refactoring.copy.CopyHandlerDelegateBase;
import consulo.language.editor.util.EditorHelper;
import consulo.language.psi.*;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.ex.awt.Messages;
import consulo.undoRedo.CommandProcessor;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.util.*;

@ExtensionImpl(order = "before copyFilesOrDirectories")
public class CopyClassesHandler extends CopyHandlerDelegateBase {
  private static final Logger LOG = Logger.getInstance(CopyClassesHandler.class);

  @Override
  public boolean forbidToClone(PsiElement[] elements, boolean fromUpdate) {
    final Map<PsiFile, PsiClass[]> fileMap = convertToTopLevelClasses(elements, fromUpdate, null, null);
    if (fileMap != null && fileMap.size() == 1) {
      final PsiClass[] psiClasses = fileMap.values().iterator().next();
      return psiClasses != null && psiClasses.length > 1;
    }
    return true;
  }

  @Override
  public boolean canCopy(PsiElement[] elements, boolean fromUpdate) {
    return canCopyClass(fromUpdate, elements);
  }

  public static boolean canCopyClass(PsiElement... elements) {
    return canCopyClass(false, elements);
  }

  public static boolean canCopyClass(boolean fromUpdate, PsiElement... elements) {
    if (fromUpdate && elements.length > 0 && elements[0] instanceof PsiDirectory) return true;
    return convertToTopLevelClasses(elements, fromUpdate, null, null) != null;
  }

  @Nullable
  private static Map<PsiFile, PsiClass[]> convertToTopLevelClasses(final PsiElement[] elements,
                                                                   final boolean fromUpdate,
                                                                   String relativePath,
                                                                   Map<PsiFile, String> relativeMap) {
    final Map<PsiFile, PsiClass[]> result = new HashMap<PsiFile, PsiClass[]>();
    for (PsiElement element : elements) {
      final PsiElement navigationElement = element.getNavigationElement();
      LOG.assertTrue(navigationElement != null, element);
      final PsiFile containingFile = navigationElement.getContainingFile();
      if (!(containingFile instanceof PsiClassOwner &&
          JavaProjectRootsUtil.isOutsideSourceRoot(containingFile))) {
        PsiClass[] topLevelClasses = getTopLevelClasses(element);
        if (topLevelClasses == null) {
          if (element instanceof PsiDirectory) {
            if (!fromUpdate) {
              final String name = ((PsiDirectory) element).getName();
              final String path = relativePath != null ? (relativePath.length() > 0 ? (relativePath + "/") : "") + name : null;
              final Map<PsiFile, PsiClass[]> map = convertToTopLevelClasses(element.getChildren(), fromUpdate, path, relativeMap);
              if (map == null) return null;
              for (Map.Entry<PsiFile, PsiClass[]> entry : map.entrySet()) {
                fillResultsMap(result, entry.getKey(), entry.getValue());
              }
            }
            continue;
          }
          if (!(element instanceof PsiFileSystemItem)) return null;
        }
        fillResultsMap(result, containingFile, topLevelClasses);
        if (relativeMap != null) {
          relativeMap.put(containingFile, relativePath);
        }
      }
    }
    if (result.isEmpty()) {
      return null;
    } else {
      boolean hasClasses = false;
      for (PsiClass[] classes : result.values()) {
        if (classes != null) {
          hasClasses = true;
          break;
        }
      }
      return hasClasses ? result : null;
    }
  }

  @Nullable
  private static String normalizeRelativeMap(Map<PsiFile, String> relativeMap) {
    String vector = null;
    for (String relativePath : relativeMap.values()) {
      if (vector == null) {
        vector = relativePath;
      } else if (vector.startsWith(relativePath + "/")) {
        vector = relativePath;
      } else if (!relativePath.startsWith(vector + "/") && !relativePath.equals(vector)) {
        return null;
      }
    }
    if (vector != null) {
      for (PsiFile psiFile : relativeMap.keySet()) {
        final String path = relativeMap.get(psiFile);
        relativeMap.put(psiFile, path.equals(vector) ? "" : path.substring(vector.length() + 1));
      }
    }
    return vector;
  }

  private static void fillResultsMap(Map<PsiFile, PsiClass[]> result, PsiFile containingFile, PsiClass[] topLevelClasses) {
    PsiClass[] classes = result.get(containingFile);
    if (topLevelClasses != null) {
      if (classes != null) {
        topLevelClasses = ArrayUtil.mergeArrays(classes, topLevelClasses, PsiClass.ARRAY_FACTORY);
      }
      result.put(containingFile, topLevelClasses);
    } else {
      result.put(containingFile, classes);
    }
  }

  public void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.copyClass");
    final HashMap<PsiFile, String> relativePathsMap = new HashMap<PsiFile, String>();
    final Map<PsiFile, PsiClass[]> classes = convertToTopLevelClasses(elements, false, "", relativePathsMap);
    assert classes != null;
    if (defaultTargetDirectory == null) {
      final PsiFile psiFile = classes.keySet().iterator().next();
      defaultTargetDirectory = psiFile.getContainingDirectory();
      LOG.assertTrue(defaultTargetDirectory != null, psiFile);
    } else {
      Project project = defaultTargetDirectory.getProject();
      VirtualFile sourceRootForFile = ProjectRootManager.getInstance(project).getFileIndex()
          .getSourceRootForFile(defaultTargetDirectory.getVirtualFile());
      if (sourceRootForFile == null) {
        final List<PsiElement> files = new ArrayList<PsiElement>();
        for (int i = 0, elementsLength = elements.length; i < elementsLength; i++) {
          PsiFile containingFile = elements[i].getContainingFile();
          if (containingFile != null) {
            files.add(containingFile);
          } else if (elements[i] instanceof PsiDirectory) {
            files.add(elements[i]);
          }
        }
        CopyFilesOrDirectoriesHandler.copyAsFiles(files.toArray(new PsiElement[files.size()]), defaultTargetDirectory, project);
        return;
      }
    }
    Project project = defaultTargetDirectory.getProject();
    Object targetDirectory = null;
    String className = null;
    if (copyOneClass(classes)) {
      final String commonPath = ArrayUtil.find(elements, classes.values().iterator().next()) == -1 ? normalizeRelativeMap(relativePathsMap) : null;
      CopyClassDialog dialog = new CopyClassDialog(classes.values().iterator().next()[0], defaultTargetDirectory, project, false) {
        @Override
        protected String getQualifiedName() {
          if (commonPath != null && !commonPath.isEmpty()) {
            return StringUtil.getQualifiedName(super.getQualifiedName(), commonPath.replaceAll("/", "."));
          }
          return super.getQualifiedName();
        }
      };
      dialog.setTitle(RefactoringBundle.message("copy.handler.copy.class"));
      dialog.show();
      if (dialog.isOK()) {
        targetDirectory = dialog.getTargetDirectory();
        className = dialog.getClassName();
        if (className == null || className.length() == 0) return;
      }
    } else {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        targetDirectory = defaultTargetDirectory;
      } else {
        defaultTargetDirectory = CopyFilesOrDirectoriesHandler.resolveDirectory(defaultTargetDirectory);
        if (defaultTargetDirectory == null) return;
        PsiElement[] files = PsiUtilCore.toPsiFileArray(classes.keySet());
        if (classes.keySet().size() == 1) {
          //do not choose a new name for a file when multiple classes exist in one file
          final PsiClass[] psiClasses = classes.values().iterator().next();
          if (psiClasses != null) {
            files = psiClasses;
          }
        }
        final CopyFilesOrDirectoriesDialog dialog = new CopyFilesOrDirectoriesDialog(files, defaultTargetDirectory, project, false);
        dialog.show();
        if (dialog.isOK()) {
          targetDirectory = dialog.getTargetDirectory();
          className = dialog.getNewName();
        }
      }
    }
    if (targetDirectory != null) {
      copyClassesImpl(className, project, classes, relativePathsMap, targetDirectory, defaultTargetDirectory, RefactoringBundle.message(
          "copy.handler.copy.class"), false);
    }
  }

  private static boolean copyOneClass(Map<PsiFile, PsiClass[]> classes) {
    if (classes.size() == 1) {
      final PsiClass[] psiClasses = classes.values().iterator().next();
      return psiClasses != null && psiClasses.length == 1;
    }
    return false;
  }

  public void doClone(PsiElement element) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.copyClass");
    PsiClass[] classes = getTopLevelClasses(element);
    if (classes == null) {
      CopyFilesOrDirectoriesHandler.doCloneFile(element);
      return;
    }
    Project project = element.getProject();

    CopyClassDialog dialog = new CopyClassDialog(classes[0], null, project, true);
    dialog.setTitle(RefactoringBundle.message("copy.handler.clone.class"));
    dialog.show();
    if (dialog.isOK()) {
      String className = dialog.getClassName();
      PsiDirectory targetDirectory = element.getContainingFile().getContainingDirectory();
      copyClassesImpl(className, project, Collections.singletonMap(classes[0].getContainingFile(), classes), null, targetDirectory,
          targetDirectory, RefactoringBundle.message("copy.handler.clone.class"), true);
    }
  }

  private static void copyClassesImpl(final String copyClassName,
                                      final Project project,
                                      final Map<PsiFile, PsiClass[]> classes,
                                      final HashMap<PsiFile, String> map,
                                      final Object targetDirectory,
                                      final PsiDirectory defaultTargetDirectory,
                                      final String commandName,
                                      final boolean selectInActivePanel) {
    final boolean[] result = new boolean[]{false};
    Runnable command = new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            try {
              PsiDirectory target;
              if (targetDirectory instanceof PsiDirectory) {
                target = (PsiDirectory) targetDirectory;
              } else {
                target = ((MoveDestination) targetDirectory).getTargetDirectory(defaultTargetDirectory);
              }
              PsiElement newElement = doCopyClasses(classes, map, copyClassName, target, project);
              if (newElement != null) {
                CopyHandler.updateSelectionInActiveProjectView(newElement, project, selectInActivePanel);
                EditorHelper.openInEditor(newElement);

                result[0] = true;
              }
            } catch (final IncorrectOperationException ex) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showMessageDialog(project, ex.getMessage(), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
                }
              });
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    };
    CommandProcessor processor = CommandProcessor.getInstance();
    processor.executeCommand(project, command, commandName, null);

    if (result[0]) {
      ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
        public void run() {
          ToolWindowManager.getInstance(project).activateEditorComponent();
        }
      });
    }
  }

  @Nullable
  public static PsiElement doCopyClasses(final Map<PsiFile, PsiClass[]> fileToClasses,
                                         final String copyClassName,
                                         final PsiDirectory targetDirectory,
                                         final Project project) throws IncorrectOperationException {
    return doCopyClasses(fileToClasses, null, copyClassName, targetDirectory, project);
  }

  @Nullable
  public static PsiElement doCopyClasses(final Map<PsiFile, PsiClass[]> fileToClasses,
                                         @Nullable HashMap<PsiFile, String> map, final String copyClassName,
                                         final PsiDirectory targetDirectory,
                                         final Project project) throws IncorrectOperationException {
    PsiElement newElement = null;
    final Map<PsiClass, PsiElement> oldToNewMap = new HashMap<PsiClass, PsiElement>();
    for (final PsiClass[] psiClasses : fileToClasses.values()) {
      if (psiClasses != null) {
        for (PsiClass aClass : psiClasses) {
          if (aClass instanceof SyntheticElement) {
            continue;
          }
          oldToNewMap.put(aClass, null);
        }
      }
    }
    final List<PsiFile> createdFiles = new ArrayList<PsiFile>(fileToClasses.size());
    int[] choice = fileToClasses.size() > 1 ? new int[]{-1} : null;
    List<PsiFile> files = new ArrayList<PsiFile>();
    for (final Map.Entry<PsiFile, PsiClass[]> entry : fileToClasses.entrySet()) {
      final PsiFile psiFile = entry.getKey();
      final PsiClass[] sources = entry.getValue();
      if (psiFile instanceof PsiClassOwner && sources != null) {
        final PsiFile createdFile = copy(psiFile, targetDirectory, copyClassName, map == null ? null : map.get(psiFile), choice);
        if (createdFile == null) return null;
        for (final PsiClass destination : ((PsiClassOwner) createdFile).getClasses()) {
          if (destination instanceof SyntheticElement) {
            continue;
          }
          PsiClass source = findByName(sources, destination.getName());
          if (source != null) {
            final PsiClass copy = copy(source, copyClassName);
            newElement = destination.replace(copy);
            oldToNewMap.put(source, newElement);
          } else {
            destination.delete();
          }
        }
        createdFiles.add(createdFile);
      } else {
        files.add(psiFile);
      }
    }


    for (PsiFile file : files) {
      try {
        PsiDirectory finalTarget = targetDirectory;
        final String relativePath = map != null ? map.get(file) : null;
        if (relativePath != null && !relativePath.isEmpty()) {
          finalTarget = buildRelativeDir(targetDirectory, relativePath).findOrCreateTargetDirectory();
        }
        final PsiFile fileCopy = CopyFilesOrDirectoriesHandler.copyToDirectory(file, getNewFileName(file, copyClassName), finalTarget, choice, null);
        if (fileCopy != null) {
          createdFiles.add(fileCopy);
        }
      } catch (IOException e) {
        throw new IncorrectOperationException(e.getMessage());
      }
    }

    final Set<PsiElement> rebindExpressions = new HashSet<PsiElement>();
    for (PsiElement element : oldToNewMap.values()) {
      if (element == null) {
        LOG.error(oldToNewMap.keySet());
        continue;
      }
      decodeRefs(element, oldToNewMap, rebindExpressions);
    }

    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    for (PsiFile psiFile : createdFiles) {
      if (psiFile instanceof PsiJavaFile) {
        codeStyleManager.removeRedundantImports((PsiJavaFile) psiFile);
      }
    }
    for (PsiElement expression : rebindExpressions) {
      codeStyleManager.shortenClassReferences(expression);
    }
    new OptimizeImportsProcessor(project, createdFiles.toArray(new PsiFile[createdFiles.size()]), null).run();
    return newElement != null ? newElement : createdFiles.size() > 0 ? createdFiles.get(0) : null;
  }

  private static PsiFile copy(@Nonnull PsiFile file, PsiDirectory directory, String name, String relativePath, int[] choice) {
    final String fileName = getNewFileName(file, name);
    if (relativePath != null && !relativePath.isEmpty()) {
      return buildRelativeDir(directory, relativePath).findOrCreateTargetDirectory().copyFileFrom(fileName, file);
    }
    if (CopyFilesOrDirectoriesHandler.checkFileExist(directory, choice, file, fileName, "Copy")) return null;
    return directory.copyFileFrom(fileName, file);
  }

  private static String getNewFileName(PsiFile file, String name) {
    if (name != null) {
      if (file instanceof PsiClassOwner) {
        final PsiClass[] classes = ((PsiClassOwner) file).getClasses();
        if (classes.length > 0 && !(classes[0] instanceof SyntheticElement)) {
          return name + "." + file.getViewProvider().getVirtualFile().getExtension();
        }
      }
      return name;
    }
    return file.getName();
  }

  @Nonnull
  private static MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper buildRelativeDir(final @Nonnull PsiDirectory directory,
                                                                                           final @Nonnull String relativePath) {
    MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper current = null;
    for (String pathElement : relativePath.split("/")) {
      if (current == null) {
        current = new MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper(directory, pathElement);
      } else {
        current = new MoveDirectoryWithClassesProcessor.TargetDirectoryWrapper(current, pathElement);
      }
    }
    LOG.assertTrue(current != null);
    return current;
  }

  private static PsiClass copy(PsiClass aClass, String name) {
    final PsiClass classNavigationElement = (PsiClass) aClass.getNavigationElement();
    final PsiClass classCopy = (PsiClass) classNavigationElement.copy();
    if (name != null) {
      classCopy.setName(name);
    }
    return classCopy;
  }

  @Nullable
  private static PsiClass findByName(PsiClass[] classes, String name) {
    if (name != null) {
      for (PsiClass aClass : classes) {
        if (name.equals(aClass.getName())) {
          return aClass;
        }
      }
    }
    return null;
  }

  private static void rebindExternalReferences(PsiElement element,
                                               Map<PsiClass, PsiElement> oldToNewMap,
                                               Set<PsiElement> rebindExpressions) {
    final LocalSearchScope searchScope = new LocalSearchScope(element);
    for (PsiClass aClass : oldToNewMap.keySet()) {
      final PsiElement newClass = oldToNewMap.get(aClass);
      for (PsiReference reference : ReferencesSearch.search(aClass, searchScope)) {
        rebindExpressions.add(reference.bindToElement(newClass));
      }
    }
  }


  private static void decodeRefs(@Nonnull PsiElement element, final Map<PsiClass, PsiElement> oldToNewMap, final Set<PsiElement> rebindExpressions) {
    element.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        decodeRef(expression, oldToNewMap, rebindExpressions);
        super.visitReferenceExpression(expression);
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        final PsiJavaCodeReferenceElement referenceElement = expression.getClassReference();
        if (referenceElement != null) {
          decodeRef(referenceElement, oldToNewMap, rebindExpressions);
        }
        super.visitNewExpression(expression);
      }

      @Override
      public void visitTypeElement(PsiTypeElement type) {
        final PsiJavaCodeReferenceElement referenceElement = type.getInnermostComponentReferenceElement();
        if (referenceElement != null) {
          decodeRef(referenceElement, oldToNewMap, rebindExpressions);
        }
        super.visitTypeElement(type);
      }
    });
    rebindExternalReferences(element, oldToNewMap, rebindExpressions);
  }

  private static void decodeRef(final PsiJavaCodeReferenceElement expression,
                                final Map<PsiClass, PsiElement> oldToNewMap,
                                Set<PsiElement> rebindExpressions) {
    final PsiElement resolved = expression.resolve();
    if (resolved instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass) resolved;
      if (oldToNewMap.containsKey(psiClass)) {
        rebindExpressions.add(expression.bindToElement(oldToNewMap.get(psiClass)));
      }
    }
  }

  @Nullable
  private static PsiClass[] getTopLevelClasses(PsiElement element) {
    while (true) {
      if (element == null || element instanceof PsiFile) break;
      if (element instanceof PsiClass && element.getParent() != null && ((PsiClass) element).getContainingClass() == null && !(element instanceof PsiAnonymousClass))
        break;
      element = element.getParent();
    }
    if (element instanceof PsiCompiledElement) return null;
    if (element instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner) element).getClasses();
      if (classes.length > 0) {
        for (final PsiClass aClass : classes) {
          if (aClass instanceof SyntheticElement) {
            return null;
          }
        }

        return classes;
      }
    }
    return element instanceof PsiClass ? new PsiClass[]{(PsiClass) element} : null;
  }
}
