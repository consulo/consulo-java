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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.projectRoots.JavaSdkVersionUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.codeEditor.Editor;
import consulo.content.bundle.Sdk;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.java.language.module.extension.JavaMutableModuleExtension;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author cdr
 */
public class IncreaseLanguageLevelFix implements SyntheticIntentionAction, LocalQuickFix {
    private static final Logger LOG = Logger.getInstance(IncreaseLanguageLevelFix.class);

    private final LanguageLevel myLevel;

    public IncreaseLanguageLevelFix(LanguageLevel targetLevel) {
        myLevel = targetLevel;
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return CodeInsightLocalize.setLanguageLevelTo0(myLevel.getDescription().get());
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return getText();
    }

    @Override
    @RequiredUIAccess
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        invoke(project, null, element.getContainingFile());
    }

    private static boolean isJdkSupportsLevel(@Nullable Sdk jdk, LanguageLevel level) {
        if (jdk == null) {
            return true;
        }
        JavaSdkVersion version = JavaSdkVersionUtil.getJavaSdkVersion(jdk);
        JavaSdkVersion required = JavaSdkVersion.fromLanguageLevel(level);
        return version != null && (level.isPreview() ? version.equals(required) : version.isAtLeast(required));
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) {
            return false;
        }

        Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
        return module != null && isLanguageLevelAcceptable(module, myLevel);
    }

    public static boolean isLanguageLevelAcceptable(Module module, LanguageLevel level) {
        return isJdkSupportsLevel(ModuleUtilCore.getSdk(module, JavaModuleExtension.class), level);
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        VirtualFile virtualFile = file.getVirtualFile();
        LOG.assertTrue(virtualFile != null);
        Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
        if (module == null) {
            return;
        }

        JavaModuleExtension extension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
        if (extension == null) {
            return;
        }

        ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        JavaMutableModuleExtension mutableModuleExtension = rootModel.getExtension(JavaMutableModuleExtension.class);

        assert mutableModuleExtension != null;

        mutableModuleExtension.getInheritableLanguageLevel().set(null, myLevel.getName());

        project.getApplication().runWriteAction(rootModel::commit);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
