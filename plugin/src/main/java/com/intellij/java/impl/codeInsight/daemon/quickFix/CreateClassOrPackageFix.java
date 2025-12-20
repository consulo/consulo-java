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

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.java.impl.psi.util.CreateClassUtil;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.util.ClassKind;
import consulo.application.ApplicationManager;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.ide.util.DirectoryChooserUtil;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class CreateClassOrPackageFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private static final Logger LOG = Logger.getInstance(CreateClassOrPackageFix.class);
    private final List<PsiDirectory> myWritableDirectoryList;
    private final String myPresentation;

    @Nullable
    private final ClassKind myClassKind;
    @Nullable
    private final String mySuperClass;
    private final String myRedPart;
    @Nullable
    private final String myTemplateName;

    @Nullable
    public static CreateClassOrPackageFix createFix(@Nonnull String qualifiedName,
                                                    @Nonnull GlobalSearchScope scope,
                                                    @Nonnull PsiElement context,
                                                    @Nullable PsiJavaPackage basePackage,
                                                    @Nullable ClassKind kind,
                                                    @Nullable String superClass,
                                                    @Nullable String templateName) {
        List<PsiDirectory> directories = getWritableDirectoryListDefault(basePackage, scope, context.getManager());
        if (directories.isEmpty()) {
            return null;
        }
        String redPart = basePackage == null ? qualifiedName : qualifiedName.substring(basePackage.getQualifiedName().length() + 1);
        int dot = redPart.indexOf('.');
        boolean fixPath = dot >= 0;
        String firstRedName = fixPath ? redPart.substring(0, dot) : redPart;
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

    @Nullable
    public static CreateClassOrPackageFix createFix(@Nonnull String qualifiedName,
                                                    @Nonnull PsiElement context,
                                                    @Nullable ClassKind kind, String superClass) {
        return createFix(qualifiedName, context.getResolveScope(), context, null, kind, superClass, null);
    }

    private CreateClassOrPackageFix(@Nonnull List<PsiDirectory> writableDirectoryList,
                                    @Nonnull PsiElement context,
                                    @Nonnull String presentation,
                                    @Nonnull String redPart,
                                    @Nullable ClassKind kind,
                                    @Nullable String superClass,
                                    @Nullable String templateName) {
        super(context);
        myRedPart = redPart;
        myTemplateName = templateName;
        myWritableDirectoryList = writableDirectoryList;
        myClassKind = kind;
        mySuperClass = superClass;
        myPresentation = presentation;
    }

    @Override
    @Nonnull
    public LocalizeValue getText() {
        if (myClassKind == ClassKind.INTERFACE) {
            return JavaQuickFixLocalize.createInterfaceText(myPresentation);
        }
        else {
            if (myClassKind != null) {
                return JavaQuickFixLocalize.createClassText(myPresentation);
            }
            else {
                return JavaQuickFixLocalize.createPackageText(myPresentation);
            }
        }
    }

    @Override
    public void invoke(@Nonnull final Project project,
                       @Nonnull final PsiFile file,
                       @Nullable Editor editor,
                       @Nonnull final PsiElement startElement,
                       @Nonnull PsiElement endElement) {
        if (isAvailable(project, null, file)) {
            new WriteCommandAction(project) {
                @Override
                protected void run(Result result) throws Throwable {
                    final PsiDirectory directory = chooseDirectory(project, file);
                    if (directory == null) {
                        return;
                    }
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

    private static boolean checkCreateClassOrPackage(boolean createJavaClass, PsiDirectory directory, String name) {
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

    @Nullable
    private PsiDirectory chooseDirectory(Project project, PsiFile file) {
        PsiDirectory preferredDirectory = myWritableDirectoryList.isEmpty() ? null : myWritableDirectoryList.get(0);
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        VirtualFile virtualFile = file.getVirtualFile();
        assert virtualFile != null;
        Module moduleForFile = fileIndex.getModuleForFile(virtualFile);
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

    private void doCreate(PsiDirectory baseDirectory, PsiElement myContext) {
        PsiManager manager = baseDirectory.getManager();
        PsiDirectory directory = baseDirectory;
        String lastName;
        for (StringTokenizer st = new StringTokenizer(myRedPart, "."); ; ) {
            lastName = st.nextToken();
            if (st.hasMoreTokens()) {
                try {
                    PsiDirectory subdirectory = directory.findSubdirectory(lastName);
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

    public static List<PsiDirectory> getWritableDirectoryListDefault(@Nullable PsiJavaPackage context,
                                                                     GlobalSearchScope scope,
                                                                     PsiManager psiManager) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Getting writable directory list for package '" + (context == null ? null : context.getQualifiedName()) + "', scope=" + scope);
        }
        List<PsiDirectory> writableDirectoryList = new ArrayList<PsiDirectory>();
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
