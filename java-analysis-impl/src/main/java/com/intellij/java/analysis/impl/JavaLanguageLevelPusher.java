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
package com.intellij.java.analysis.impl;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.JavaLanguageLevelPersistence;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.content.FilePropertyPusher;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.PushedFilePropertiesUpdater;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;

import java.io.IOException;

/**
 * @author Gregory.Shrago
 */
@ExtensionImpl
public class JavaLanguageLevelPusher implements FilePropertyPusher<LanguageLevel> {
    public static void pushLanguageLevel(final Project project) {
        PushedFilePropertiesUpdater.getInstance(project).pushAll(new JavaLanguageLevelPusher());
    }

    @Override
    public Key<LanguageLevel> getFileDataKey() {
        return LanguageLevel.KEY;
    }

    @Override
    public boolean pushDirectoriesOnly() {
        return true;
    }

    @Override
    public LanguageLevel getDefaultValue() {
        return LanguageLevel.HIGHEST;
    }

    @Override
    public LanguageLevel getImmediateValue(Project project, VirtualFile file) {
        if (file == null) {
            return null;
        }
        final Module moduleForFile = ModuleUtilCore.findModuleForFile(file, project);
        if (moduleForFile == null) {
            return null;
        }
        return getImmediateValue(moduleForFile);
    }

    @Override
    public LanguageLevel getImmediateValue(Module module) {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

        final JavaModuleExtension extension = moduleRootManager.getExtension(JavaModuleExtension.class);
        return extension == null ? null : extension.getLanguageLevel();
    }

    @Override
    public boolean acceptsDirectory(VirtualFile file, Project project) {
        return ProjectFileIndex.getInstance(project).isInSourceContent(file);
    }


    @Override
    public void persistAttribute(
        Project project,
        VirtualFile fileOrDir,
        LanguageLevel level
    ) throws IOException {
        if (JavaLanguageLevelPersistence.getPersistedLanguageLevel(fileOrDir) == level) {
            return;
        }

        JavaLanguageLevelPersistence.persistLanguageLevel(fileOrDir, level);

        for (VirtualFile child : fileOrDir.getChildren()) {
            if (!child.isDirectory() && JavaFileType.INSTANCE == child.getFileType()) {
                PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(child);
            }
        }
    }

    @Override
    public void afterRootsChanged(Project project) {
    }

    @Override
    public boolean acceptsFile(VirtualFile file, Project project) {
        return false;
    }

    public LocalizeValue getInconsistencyLanguageLevelMessage(
        LocalizeValue message,
        PsiElement element,
        LanguageLevel level,
        PsiFile file
    ) {
        return LocalizeValue.empty();
    }
}
