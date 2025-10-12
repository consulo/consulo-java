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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.codeInsight.PackageUtil;
import com.intellij.java.impl.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.language.editor.LangDataKeys;
import consulo.language.editor.hint.QuestionAction;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.action.RefactoringActionHandlerFactory;
import consulo.language.editor.ui.PsiElementListCellRenderer;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.popup.AWTPopupFactory;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author cdr
 */
public class MoveClassToModuleFix implements SyntheticIntentionAction {
    private final Map<PsiClass, Module> myModules = new LinkedHashMap<PsiClass, Module>();
    private final String myReferenceName;
    private final Module myCurrentModule;
    private final PsiDirectory mySourceRoot;
    private static final Logger LOG = Logger.getInstance(MoveClassToModuleFix.class);

    public MoveClassToModuleFix(String referenceName, Module currentModule, PsiDirectory root, PsiElement psiElement) {
        myReferenceName = referenceName;
        myCurrentModule = currentModule;
        mySourceRoot = root;
        final Project project = psiElement.getProject();
        final PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(referenceName, GlobalSearchScope.allScope(project));
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        for (final PsiClass aClass : classes) {
            if (!facade.getResolveHelper().isAccessible(aClass, psiElement, aClass)) {
                continue;
            }
            final PsiFile psiFile = aClass.getContainingFile();
            if (!(psiFile instanceof PsiJavaFile)) {
                continue;
            }
            if (aClass.getQualifiedName() == null) {
                continue;
            }
            VirtualFile virtualFile = psiFile.getVirtualFile();
            if (virtualFile == null) {
                continue;
            }
            final Module classModule = fileIndex.getModuleForFile(virtualFile);
            if (classModule != null && classModule != currentModule && !ModuleRootManager.getInstance(currentModule).isDependsOn(classModule)) {
                myModules.put(aClass, classModule);
            }
        }
    }

    @Override
    @Nonnull
    public LocalizeValue getText() {
        if (myModules.size() == 1) {
            final PsiClass aClass = myModules.keySet().iterator().next();
            return LocalizeValue.localizeTODO("Move '" + aClass.getQualifiedName() + "' from module '" + myModules.get(aClass).getName() +
                "' to '" + myCurrentModule.getName() + "'");
        }
        return LocalizeValue.localizeTODO("Move '" + myReferenceName + "' in '" + myCurrentModule.getName() + "'...");
    }

    @Override
    public boolean isAvailable(@Nonnull final Project project, final Editor editor, final PsiFile file) {
        return !myModules.isEmpty();
    }

    @Override
    public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
        if (myModules.size() == 1) {
            moveClass(project, editor, file, myModules.keySet().iterator().next());
        }
        else {
            LOG.assertTrue(editor != null);
            final JBList list = new JBList(myModules.keySet());
            list.setCellRenderer(new PsiElementListCellRenderer<PsiClass>() {
                @Override
                public String getElementText(PsiClass psiClass) {
                    return psiClass.getQualifiedName();
                }

                @Nullable
                @Override
                protected String getContainerText(PsiClass element, String name) {
                    return null;
                }

                @Override
                protected int getIconFlags() {
                    return 0;
                }
            });
            JBPopup popup = ((AWTPopupFactory) JBPopupFactory.getInstance()).createListPopupBuilder(list)
                .setItemChoosenCallback(new Runnable() {
                    @Override
                    public void run() {
                        final Object value = list.getSelectedValue();
                        if (value instanceof PsiClass) {
                            moveClass(project, editor, file, (PsiClass) value);
                        }
                    }
                }).createPopup();

            EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
        }
    }

    private void moveClass(Project project, Editor editor, PsiFile file, PsiClass aClass) {
        RefactoringActionHandler moveHandler = RefactoringActionHandlerFactory.getInstance().createMoveHandler();
        DataManager dataManager = DataManager.getInstance();
        DataContext dataContext = dataManager.getDataContext();
        final String fqName = aClass.getQualifiedName();
        LOG.assertTrue(fqName != null);
        PsiDirectory directory = PackageUtil
            .findOrCreateDirectoryForPackage(myCurrentModule, StringUtil.getPackageName(fqName), mySourceRoot, true);
        DataContext context = DataContext.builder().parent(dataContext).add(LangDataKeys.TARGET_PSI_ELEMENT, directory).build();

        moveHandler.invoke(project, new PsiElement[]{aClass}, context);
        PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
        PsiClass newClass = JavaPsiFacade.getInstance(project).findClass(fqName, GlobalSearchScope.moduleScope(myCurrentModule));
        if (reference != null && newClass != null) {
            final QuestionAction action = new AddImportAction(project, reference, editor, newClass);
            action.execute();
        }
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    public static void registerFixes(Consumer<IntentionAction> consumer, final PsiJavaCodeReferenceElement reference) {
        final PsiElement psiElement = reference.getElement();
        @NonNls final String referenceName = reference.getRangeInElement().substring(psiElement.getText());
        Project project = psiElement.getProject();
        final PsiFile containingFile = psiElement.getContainingFile();
        if (containingFile == null) {
            return;
        }
        PsiDirectory dir = containingFile.getContainingDirectory();
        if (dir == null) {
            return;
        }

        VirtualFile classVFile = containingFile.getVirtualFile();
        if (classVFile == null) {
            return;
        }

        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        final Module currentModule = fileIndex.getModuleForFile(classVFile);
        if (currentModule == null) {
            return;
        }
        VirtualFile[] sourceRoots = ModuleRootManager.getInstance(currentModule).getSourceRoots();
        if (sourceRoots.length == 0) {
            return;
        }
        final PsiDirectory sourceDirectory = PsiManager.getInstance(project).findDirectory(sourceRoots[0]);
        if (sourceDirectory == null) {
            return;
        }

        VirtualFile vsourceRoot = fileIndex.getSourceRootForFile(classVFile);
        if (vsourceRoot == null) {
            return;
        }
        final PsiDirectory sourceRoot = PsiManager.getInstance(project).findDirectory(vsourceRoot);
        if (sourceRoot == null) {
            return;
        }

        consumer.accept(new MoveClassToModuleFix(referenceName, currentModule, sourceRoot, psiElement));
    }
}
