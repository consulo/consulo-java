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
package com.intellij.java.impl.ide.actions;

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiNameHelper;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ActionImpl;
import consulo.application.dumb.DumbAware;
import consulo.dataContext.DataContext;
import consulo.fileTemplate.AttributesDefaults;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.ide.IdeView;
import consulo.ide.action.CreateFromTemplateActionBase;
import consulo.ide.localize.IdeLocalize;
import consulo.java.localize.JavaLocalize;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.psi.PsiDirectory;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;

import static com.intellij.java.language.impl.codeInsight.template.JavaTemplateUtil.INTERNAL_PACKAGE_INFO_TEMPLATE_NAME;

/**
 * @author Bas Leijdekkers
 */
@ActionImpl(id = "NewPackageInfo")
public class CreatePackageInfoAction extends CreateFromTemplateActionBase implements DumbAware {
    public CreatePackageInfoAction() {
        super(
            JavaLocalize.actionCreateNewPackageInfoTitle(),
            JavaLocalize.actionCreateNewPackageInfoDescription(),
            JavaFileType.INSTANCE.getIcon()
        );
    }

    @Nullable
    @Override
    @RequiredUIAccess
    protected PsiDirectory getTargetDirectory(DataContext dataContext, IdeView view) {
        PsiDirectory[] directories = view.getDirectories();
        for (PsiDirectory directory : directories) {
            PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
            if (aPackage == null) {
                continue;
            }
            if (directory.findFile(PsiJavaPackage.PACKAGE_INFO_FILE) != null) {
                Messages.showErrorDialog(
                    dataContext.getData(Project.KEY),
                    JavaLocalize.errorPackageAlreadyContainsPackageInfo(aPackage.getQualifiedName()).get(),
                    IdeLocalize.titleCannotCreateFile().get()
                );
                return null;
            }
            else if (directory.findFile("package.html") != null) {
                if (Messages.showOkCancelDialog(
                    dataContext.getData(Project.KEY),
                    JavaLocalize.errorPackageAlreadyContainsPackageHtml(aPackage.getQualifiedName()).get(),
                    JavaLocalize.errorPackageHtmlFoundTitle().get(),
                    IdeLocalize.buttonCreate().get(),
                    CommonLocalize.buttonCancel().get(),
                    UIUtil.getQuestionIcon()
                ) != Messages.OK) {
                    return null;
                }
            }

        }
        return super.getTargetDirectory(dataContext, view);
    }

    @Override
    @RequiredReadAction
    public void update(AnActionEvent e) {
        if (!e.getPresentation().isVisible()) {
            return;
        }

        e.getPresentation().setEnabledAndVisible(isAvailable(e.getDataContext()));
    }

    @RequiredReadAction
    private static boolean isAvailable(DataContext dataContext) {
        Project project = dataContext.getData(Project.KEY);
        IdeView view = dataContext.getData(IdeView.KEY);
        if (project == null || view == null) {
            return false;
        }
        PsiDirectory[] directories = view.getDirectories();
        if (directories.length == 0) {
            return false;
        }
        ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        JavaDirectoryService directoryService = JavaDirectoryService.getInstance();
        PsiNameHelper nameHelper = PsiNameHelper.getInstance(project);
        for (PsiDirectory directory : directories) {
            if (projectFileIndex.isUnderContentFolderType(directory.getVirtualFile(), LanguageContentFolderScopes.productionAndTest())
                && PsiUtil.isLanguageLevel5OrHigher(directory)) {
                PsiJavaPackage aPackage = directoryService.getPackage(directory);
                if (aPackage != null) {
                    String qualifiedName = aPackage.getQualifiedName();
                    if (StringUtil.isEmpty(qualifiedName) || nameHelper.isQualifiedName(qualifiedName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    @Override
    public AttributesDefaults getAttributesDefaults(DataContext dataContext) {
        return new AttributesDefaults(INTERNAL_PACKAGE_INFO_TEMPLATE_NAME).withFixedName(true);
    }

    @Override
    protected FileTemplate getTemplate(Project project, PsiDirectory dir) {
        return FileTemplateManager.getInstance(project).getInternalTemplate(INTERNAL_PACKAGE_INFO_TEMPLATE_NAME);
    }
}