/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.quickFix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.ide.util.DirectoryChooserUtil;
import consulo.application.ApplicationManager;
import consulo.application.Result;
import consulo.language.editor.WriteCommandAction;
import consulo.logging.Logger;
import consulo.codeEditor.Editor;
import consulo.module.Module;
import consulo.project.Project;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.java.language.psi.util.ClassKind;
import com.intellij.java.impl.psi.util.CreateClassUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.java.analysis.impl.JavaQuickFixBundle;

/**
 * @author peter
 */
public class CreateClassOrPackageFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(CreateClassOrPackageFix.class);
  private final List<PsiDirectory> myWritableDirectoryList;
  private final String myPresentation;

  @jakarta.annotation.Nullable
  private final ClassKind myClassKind;
  @jakarta.annotation.Nullable
  private final String mySuperClass;
  private final String myRedPart;
  @Nullable
  private final String myTemplateName;

  @Nullable
  public static CreateClassOrPackageFix createFix(@jakarta.annotation.Nonnull final String qualifiedName,
                                                  @jakarta.annotation.Nonnull final GlobalSearchScope scope,
                                                  @Nonnull final PsiElement context,
                                                  @jakarta.annotation.Nullable final PsiJavaPackage basePackage,
                                                  @jakarta.annotation.Nullable ClassKind kind,
                                                  @jakarta.annotation.Nullable String superClass,
                                                  @jakarta.annotation.Nullable String templateName) {
    final List<PsiDirectory> directories = getWritableDirectoryListDefault(basePackage, scope, context.getManager());
    if (directories.isEmpty()) {
      return null;
    }
    final String redPart = basePackage == null ? qualifiedName : qualifiedName.substring(basePackage.getQualifiedName().length() + 1);
    final int dot = redPart.indexOf('.');
    final boolean fixPath = dot >= 0;
    final String firstRedName = fixPath ? redPart.substring(0, dot) : redPart;
    for (Iterator<PsiDirectory> i = directories.iterator(); i.hasNext(); ) {
      if (!checkCreateClassOrPackage(kind != null && !fixPath, i.next(), firstRedName)) {
        i.remove();
      }
    }
    return directories.isEmpty() ? null : new CreateClassOrPackageFix(directories,
                                                                      context,
                                                                      fixPath ? qualifiedName : redPart,
                                                                      redPart,
                                                                      kind,
                                                                      superClass,
                                                                      templateName);
  }

  @jakarta.annotation.Nullable
  public static CreateClassOrPackageFix createFix(@jakarta.annotation.Nonnull final String qualifiedName,
                                                  @jakarta.annotation.Nonnull final PsiElement context,
                                                  @Nullable ClassKind kind, final String superClass) {
    return createFix(qualifiedName, context.getResolveScope(), context, null, kind, superClass, null);
  }

  private CreateClassOrPackageFix(@jakarta.annotation.Nonnull List<PsiDirectory> writableDirectoryList,
                                  @Nonnull PsiElement context,
                                  @jakarta.annotation.Nonnull String presentation,
                                  @jakarta.annotation.Nonnull String redPart,
                                  @jakarta.annotation.Nullable ClassKind kind,
                                  @jakarta.annotation.Nullable String superClass,
                                  @Nullable final String templateName) {
    super(context);
    myRedPart = redPart;
    myTemplateName = templateName;
    myWritableDirectoryList = writableDirectoryList;
    myClassKind = kind;
    mySuperClass = superClass;
    myPresentation = presentation;
  }

  @Override
  @jakarta.annotation.Nonnull
  public String getText() {
    return JavaQuickFixBundle.message(
      myClassKind == ClassKind.INTERFACE ? "create.interface.text" : myClassKind != null ? "create.class.text" : "create.package.text",
      myPresentation);
  }

  @Override
  @jakarta.annotation.Nonnull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public void invoke(@jakarta.annotation.Nonnull final Project project,
                     @jakarta.annotation.Nonnull final PsiFile file,
                     @jakarta.annotation.Nullable Editor editor,
                     @jakarta.annotation.Nonnull final PsiElement startElement,
                     @jakarta.annotation.Nonnull PsiElement endElement) {
    if (isAvailable(project, null, file)) {
      new WriteCommandAction(project) {
        @Override
        protected void run(Result result) throws Throwable {
          final PsiDirectory directory = chooseDirectory(project, file);
          if (directory == null) return;
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              doCreate(directory, startElement);
            }
          });
        }
      }.execute();
    }
  }

  private static boolean checkCreateClassOrPackage(final boolean createJavaClass, final PsiDirectory directory, final String name) {
    try {
      if (createJavaClass) {
        JavaDirectoryService.getInstance().checkCreateClass(directory, name);
      }
      else {
        directory.checkCreateSubdirectory(name);
      }
      return true;
    }
    catch (IncorrectOperationException ex) {
      return false;
    }
  }

  @jakarta.annotation.Nullable
  private PsiDirectory chooseDirectory(final Project project, final PsiFile file) {
    PsiDirectory preferredDirectory = myWritableDirectoryList.isEmpty() ? null : myWritableDirectoryList.get(0);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile virtualFile = file.getVirtualFile();
    assert virtualFile != null;
    final Module moduleForFile = fileIndex.getModuleForFile(virtualFile);
    if (myWritableDirectoryList.size() > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
      if (moduleForFile != null) {
        for (PsiDirectory directory : myWritableDirectoryList) {
          if (fileIndex.getModuleForFile(directory.getVirtualFile()) == moduleForFile) {
            preferredDirectory = directory;
            break;
          }
        }
      }

      return DirectoryChooserUtil
          .chooseDirectory(myWritableDirectoryList.toArray(new PsiDirectory[myWritableDirectoryList.size()]),
                           preferredDirectory, project,
                           new HashMap<PsiDirectory, String>());
    }
    return preferredDirectory;
  }

  private void doCreate(final PsiDirectory baseDirectory, PsiElement myContext) {
    final PsiManager manager = baseDirectory.getManager();
    PsiDirectory directory = baseDirectory;
    String lastName;
    for (StringTokenizer st = new StringTokenizer(myRedPart, "."); ;) {
      lastName = st.nextToken();
      if (st.hasMoreTokens()) {
        try {
          final PsiDirectory subdirectory = directory.findSubdirectory(lastName);
          directory = subdirectory != null ? subdirectory : directory.createSubdirectory(lastName);
        }
        catch (IncorrectOperationException e) {
          CreateFromUsageUtils.scheduleFileOrPackageCreationFailedMessageBox(e, lastName, directory, true);
          return;
        }
      }
      else {
        break;
      }
    }
    if (myClassKind != null) {
      PsiClass createdClass;
      if (myTemplateName != null) {
        createdClass = CreateClassUtil.createClassFromCustomTemplate(directory, null, lastName, myTemplateName);
      }
      else {
        createdClass = CreateFromUsageUtils
            .createClass(myClassKind == ClassKind.INTERFACE ? CreateClassKind.INTERFACE : CreateClassKind.CLASS, directory, lastName,
                         manager, myContext, null, mySuperClass);
      }
      if (createdClass != null) {
        createdClass.navigate(true);
      }
    }
    else {
      try {
        directory.createSubdirectory(lastName);
      }
      catch (IncorrectOperationException e) {
        CreateFromUsageUtils.scheduleFileOrPackageCreationFailedMessageBox(e, lastName, directory, true);
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static List<PsiDirectory> getWritableDirectoryListDefault(@jakarta.annotation.Nullable final PsiJavaPackage context,
                                                                   final GlobalSearchScope scope,
                                                                   final PsiManager psiManager) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Getting writable directory list for package '" + (context == null ? null : context.getQualifiedName()) + "', scope=" + scope);
    }
    final List<PsiDirectory> writableDirectoryList = new ArrayList<PsiDirectory>();
    if (context != null) {
      for (PsiDirectory directory : context.getDirectories()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Package directory: " + directory);
        }
        if (directory.isWritable() && scope.contains(directory.getVirtualFile())) {
          writableDirectoryList.add(directory);
        }
      }
    }
    else {
      for (VirtualFile root : ProjectRootManager.getInstance(psiManager.getProject()).getContentSourceRoots()) {
        PsiDirectory directory = psiManager.findDirectory(root);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Root: " + root + ", directory: " + directory);
        }
        if (directory != null && directory.isWritable() && scope.contains(directory.getVirtualFile())) {
          writableDirectoryList.add(directory);
        }
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Result " + writableDirectoryList);
    }
    return writableDirectoryList;
  }
}
