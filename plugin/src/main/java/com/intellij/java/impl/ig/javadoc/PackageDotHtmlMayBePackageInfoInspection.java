/*
 * Copyright 2011-2013 Bas Leijdekkers
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
package com.intellij.java.impl.ig.javadoc;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiPackageStatement;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.undoRedo.CommandProcessor;
import consulo.undoRedo.UndoConfirmationPolicy;
import consulo.util.concurrent.AsyncResult;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.psi.html.HtmlTag;
import consulo.xml.psi.xml.XmlFile;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.psi.xml.XmlTagValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class PackageDotHtmlMayBePackageInfoInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.packageDotHtmlMayBePackageInfoDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        return (Boolean) infos[1]
            ? InspectionGadgetsLocalize.packageDotHtmlMayBePackageInfoExistsProblemDescriptor().get()
            : InspectionGadgetsLocalize.packageDotHtmlMayBePackageInfoProblemDescriptor().get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final boolean packageInfoExists = (Boolean) infos[1];
        if (packageInfoExists) {
            return new DeletePackageDotHtmlFix();
        }
        final String aPackage = (String) infos[0];
        return new PackageDotHtmlMayBePackageInfoFix(aPackage);
    }

    private static class DeletePackageDotHtmlFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.packageDotHtmlMayBePackageInfoDeleteQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) {
            final PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof XmlFile)) {
                return;
            }
            final XmlFile xmlFile = (XmlFile) element;
            new WriteCommandAction.Simple(project, InspectionGadgetsLocalize.packageDotHtmlDeleteCommand().get(), xmlFile) {
                @Override
                protected void run() throws Throwable {
                    element.delete();
                }

                @Override
                protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
                    return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
                }
            }.execute();
        }
    }

    private static class PackageDotHtmlMayBePackageInfoFix extends InspectionGadgetsFix {

        private final String aPackage;

        public PackageDotHtmlMayBePackageInfoFix(String aPackage) {
            this.aPackage = aPackage;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.packageDotHtmlMayBePackageInfoConvertQuickfix();
        }

        @Override
        protected void doFix(final Project project, ProblemDescriptor descriptor) {
            final PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof XmlFile)) {
                return;
            }
            final XmlFile xmlFile = (XmlFile) element;
            final PsiDirectory directory = xmlFile.getContainingDirectory();
            if (directory == null) {
                return;
            }
            final PsiFile file = directory.findFile("package-info.java");
            if (file != null) {
                return;
            }
            new WriteCommandAction.Simple(project, InspectionGadgetsLocalize.packageDotHtmlConvertCommand().get(), file) {
                @Override
                protected void run() throws Throwable {
                    final PsiJavaFile file = (PsiJavaFile) directory.createFile("package-info.java");
                    CommandProcessor.getInstance().addAffectedFiles(project, file.getVirtualFile());
                    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
                    String packageInfoText = getPackageInfoText(xmlFile);
                    if (packageInfoText == null) {
                        packageInfoText = xmlFile.getText();
                    }
                    final StringBuilder commentText = new StringBuilder("/**\n");
                    final String[] lines = StringUtil.splitByLines(packageInfoText);
                    boolean appended = false;
                    for (String line : lines) {
                        if (!appended && line.length() == 0) {
                            // skip empty lines at the beginning
                            continue;
                        }
                        commentText.append(" * ").append(line).append('\n');
                        appended = true;
                    }
                    commentText.append("*/");
                    final PsiDocComment comment = elementFactory.createDocCommentFromText(commentText.toString());
                    if (aPackage.length() > 0) {
                        final PsiPackageStatement packageStatement = elementFactory.createPackageStatement(aPackage);
                        final PsiElement addedElement = file.add(packageStatement);
                        file.addBefore(comment, addedElement);
                    }
                    else {
                        file.add(comment);
                    }
                    element.delete();
                    if (!isOnTheFly()) {
                        return;
                    }
                    final AsyncResult<DataContext> dataContextFromFocus = DataManager.getInstance().getDataContextFromFocus();
                    dataContextFromFocus.doWhenDone(dataContext -> {
                        final FileEditorManager editorManager = FileEditorManager.getInstance(project);
                        final VirtualFile virtualFile = file.getVirtualFile();
                        if (virtualFile == null) {
                            return;
                        }
                        editorManager.openFile(virtualFile, true);
                    });
                }

                @Override
                protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
                    return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
                }
            }.execute();
        }

        @Nullable
        private static String getPackageInfoText(XmlFile xmlFile) {
            final XmlTag rootTag = xmlFile.getRootTag();
            if (rootTag == null) {
                return null;
            }
            final PsiElement[] children = rootTag.getChildren();
            for (PsiElement child : children) {
                if (!(child instanceof HtmlTag)) {
                    continue;
                }
                final HtmlTag htmlTag = (HtmlTag) child;
                @NonNls final String name = htmlTag.getName();
                if ("body".equals(name)) {
                    final XmlTagValue value = htmlTag.getValue();
                    return value.getText();
                }
            }
            return null;
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new PackageDotHtmlMayBePackageInfoVisitor();
    }

    private static class PackageDotHtmlMayBePackageInfoVisitor extends BaseInspectionVisitor {

        @Override
        public void visitFile(PsiFile file) {
            super.visitFile(file);
            if (!(file instanceof XmlFile)) {
                return;
            }
            @NonNls final String fileName = file.getName();
            if (!"package.html".equals(fileName)) {
                return;
            }
            final PsiDirectory directory = file.getContainingDirectory();
            if (directory == null) {
                return;
            }
            final String aPackage = getPackage(directory);
            if (aPackage == null) {
                return;
            }
            final boolean exists = directory.findFile("package-info.java") != null;
            registerError(file, aPackage, Boolean.valueOf(exists));
        }

        public static String getPackage(@Nonnull PsiDirectory directory) {
            final VirtualFile virtualFile = directory.getVirtualFile();
            final Project project = directory.getProject();
            final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
            final ProjectFileIndex fileIndex = projectRootManager.getFileIndex();
            return fileIndex.getPackageNameByDirectory(virtualFile);
        }
    }
}
