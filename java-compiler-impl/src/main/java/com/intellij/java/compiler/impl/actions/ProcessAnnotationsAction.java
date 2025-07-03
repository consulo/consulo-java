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
package com.intellij.java.compiler.impl.actions;

import com.intellij.java.compiler.impl.javaCompiler.AnnotationProcessingCompiler;
import com.intellij.java.compiler.impl.javaCompiler.JavaCompilerConfiguration;
import com.intellij.java.compiler.impl.javaCompiler.annotationProcessing.AnnotationProcessingConfiguration;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.access.RequiredReadAction;
import consulo.compiler.Compiler;
import consulo.compiler.CompilerManager;
import consulo.compiler.action.CompileActionBase;
import consulo.compiler.resourceCompiler.ResourceCompiler;
import consulo.compiler.scope.FileSetCompileScope;
import consulo.compiler.scope.ModuleCompileScope;
import consulo.dataContext.DataContext;
import consulo.java.compiler.localize.JavaCompilerLocalize;
import consulo.language.editor.LangDataKeys;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public class ProcessAnnotationsAction extends CompileActionBase {
    public ProcessAnnotationsAction() {
        super(JavaCompilerLocalize.actionRunAptText(), JavaCompilerLocalize.actionRunAptDescription(), null);
    }

    @Override
    @RequiredUIAccess
    protected void doAction(DataContext dataContext, Project project) {
        Module module = dataContext.getData(LangDataKeys.MODULE_CONTEXT);
        Predicate<Compiler> filter = compiler -> {
            // EclipseLink CanonicalModelProcessor reads input from output hence adding ResourcesCompiler
            return compiler instanceof AnnotationProcessingCompiler || compiler instanceof ResourceCompiler;
        };
        if (module != null) {
            CompilerManager.getInstance(project).make(new ModuleCompileScope(module, false, true), filter, null);
        }
        else {
            FileSetCompileScope scope = getCompilableFiles(project, dataContext.getData(VirtualFile.KEY_OF_ARRAY));
            if (scope != null) {
                CompilerManager.getInstance(project).make(scope, filter, null);
            }
        }
    }

    @Override
    @RequiredReadAction
    public void update(@Nonnull AnActionEvent event) {
        super.update(event);
        Presentation presentation = event.getPresentation();
        if (!presentation.isEnabled()) {
            return;
        }
        presentation.setVisible(false);

        Project project = event.getData(Project.KEY);
        if (project == null) {
            presentation.setEnabled(false);
            return;
        }

        JavaCompilerConfiguration compilerConfiguration = JavaCompilerConfiguration.getInstance(project);

        Module module = event.getData(Module.KEY);
        Module moduleContext = event.getData(LangDataKeys.MODULE_CONTEXT);

        if (module == null) {
            presentation.setEnabled(false);
            return;
        }
        AnnotationProcessingConfiguration profile = compilerConfiguration.getAnnotationProcessingConfiguration(module);
        if (!profile.isEnabled() || (!profile.isObtainProcessorsFromClasspath() && profile.getProcessors().isEmpty())) {
            presentation.setEnabled(false);
            return;
        }

        presentation.setEnabled(true);
        presentation.setVisible(true);
        presentation.setTextValue(JavaCompilerLocalize.actionRunAptText());

        FileSetCompileScope scope = getCompilableFiles(project, event.getData(VirtualFile.KEY_OF_ARRAY));
        if (moduleContext == null && scope == null) {
            presentation.setEnabled(false);
            return;
        }

        if (moduleContext != null) {
            presentation.setTextValue(JavaCompilerLocalize.actionRunAptModuleText(trimName(moduleContext.getName())));
        }
        else {
            PsiJavaPackage aPackage = null;
            Collection<VirtualFile> files = scope.getRootFiles();
            if (files.size() == 1) {
                PsiDirectory directory = PsiManager.getInstance(project).findDirectory(files.iterator().next());
                if (directory != null) {
                    aPackage = JavaDirectoryService.getInstance().getPackage(directory);
                }
            }
            else {
                PsiElement element = event.getData(PsiElement.KEY);
                if (element instanceof PsiJavaPackage javaPackage) {
                    aPackage = javaPackage;
                }
            }

            if (aPackage != null) {
                String name = aPackage.getQualifiedName();
                presentation.setTextValue(
                    StringUtil.isNotEmpty(name)
                        ? JavaCompilerLocalize.actionRunApt0Text(trimName(name))
                        : JavaCompilerLocalize.actionRunAptDefaultText()
                );
            }
            else if (files.size() == 1) {
                VirtualFile file = files.iterator().next();
                FileType fileType = file.getFileType();
                if (CompilerManager.getInstance(project).isCompilableFileType(fileType)) {
                    presentation.setTextValue(JavaCompilerLocalize.actionRunApt0Text(trimName(file.getName())));
                }
                else {
                    presentation.setEnabled(false);
                    // the action should be invisible in popups for non-java files
                    presentation.setVisible(ActionPlaces.MAIN_MENU.equals(event.getPlace()));
                }
            }
            else {
                presentation.setTextValue(JavaCompilerLocalize.actionRunAptSelectedFilesText());
            }
        }
    }

    private static String trimName(String name) {
        int length = name.length();
        return length > 23 ? '…' + name.substring(length - 20, length) : name;
    }

    @Nullable
    @RequiredReadAction
    private static FileSetCompileScope getCompilableFiles(Project project, VirtualFile[] files) {
        if (files == null || files.length == 0) {
            return null;
        }
        PsiManager psiManager = PsiManager.getInstance(project);
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        CompilerManager compilerManager = CompilerManager.getInstance(project);
        List<VirtualFile> filesToCompile = new ArrayList<>();
        List<Module> affectedModules = new ArrayList<>();
        for (VirtualFile file : files) {
            if (!fileIndex.isInSourceContent(file)) {
                continue;
            }
            if (!file.isInLocalFileSystem()) {
                continue;
            }
            if (file.isDirectory()) {
                PsiDirectory directory = psiManager.findDirectory(file);
                if (directory == null || JavaDirectoryService.getInstance().getPackage(directory) == null) {
                    continue;
                }
            }
            else {
                FileType fileType = file.getFileType();
                if (!(compilerManager.isCompilableFileType(fileType))) {
                    continue;
                }
            }
            filesToCompile.add(file);
            ContainerUtil.addIfNotNull(affectedModules, fileIndex.getModuleForFile(file));
        }
        if (filesToCompile.isEmpty()) {
            return null;
        }
        return new FileSetCompileScope(filesToCompile, affectedModules.toArray(new Module[affectedModules.size()]));
    }
}