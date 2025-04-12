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
package com.intellij.java.impl.codeInspection.inferNullity;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.application.Result;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.content.library.Library;
import consulo.document.Document;
import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.ide.impl.idea.openapi.project.ProjectUtil;
import consulo.ide.impl.idea.openapi.roots.libraries.LibraryUtil;
import consulo.ide.impl.idea.util.SequentialModalProgressTask;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.impl.action.BaseAnalysisAction;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.editor.scope.localize.AnalysisScopeLocalize;
import consulo.language.editor.ui.awt.scope.BaseAnalysisActionDialog;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.localHistory.LocalHistory;
import consulo.localHistory.LocalHistoryAction;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.content.util.ModuleRootModificationUtil;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.layout.VerticalLayout;
import consulo.usage.*;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

@ActionImpl(id = "InferNullity", parents =
@ActionParentRef(value = @ActionRef(id = IdeActions.ACTION_CODE_MENU), relatedToAction = @ActionRef(id = "AnalyzeStacktrace"), anchor = ActionRefAnchor.AFTER))
public class InferNullityAnnotationsAction extends BaseAnalysisAction {
    private static final String INFER_NULLITY_ANNOTATIONS = "Infer Nullity Annotations";
    private static final String ANNOTATE_LOCAL_VARIABLES = "annotate.local.variables";

    private CheckBox myAnnotateLocalVariablesCb;

    public InferNullityAnnotationsAction() {
        super("Infer Nullity", INFER_NULLITY_ANNOTATIONS);
    }

    @Override
    @RequiredUIAccess
    protected void analyze(@Nonnull Project project, @Nonnull AnalysisScope scope) {
        boolean annotateLocaVars = myAnnotateLocalVariablesCb.getValueOrError();
        PropertiesComponent.getInstance().setValue(ANNOTATE_LOCAL_VARIABLES, annotateLocaVars);
        myAnnotateLocalVariablesCb = null;

        ProgressManager progressManager = ProgressManager.getInstance();
        Set<Module> modulesWithoutAnnotations = new HashSet<>();
        Set<Module> modulesWithLL = new HashSet<>();
        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
        String defaultNullable = NullableNotNullManager.getInstance(project).getDefaultNullable();
        int[] fileCount = new int[]{0};
        if (!progressManager.runProcessWithProgressSynchronously(() -> scope.accept(new PsiElementVisitor() {
            private Set<Module> processed = new HashSet<>();

            @Override
            @RequiredReadAction
            public void visitFile(PsiFile file) {
                fileCount[0]++;
                ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
                if (progressIndicator != null) {
                    VirtualFile virtualFile = file.getVirtualFile();
                    if (virtualFile != null) {
                        progressIndicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
                    }
                    progressIndicator.setTextValue(AnalysisScopeLocalize.scanningScopeProgressTitle());
                }
                if (!(file instanceof PsiJavaFile)) {
                    return;
                }
                Module module = file.getModule();
                if (module != null && processed.add(module)) {
                    if (PsiUtil.getLanguageLevel(file).compareTo(LanguageLevel.JDK_1_5) < 0) {
                        modulesWithLL.add(module);
                    }
                    else if (javaPsiFacade.findClass(
                        defaultNullable,
                        GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
                    ) == null) {
                        modulesWithoutAnnotations.add(module);
                    }
                }
            }
        }), "Check Applicability...", true, project)) {
            return;
        }
        if (!modulesWithLL.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "Infer Nullity Annotations requires the project language level be set to 1.5 or greater.",
                INFER_NULLITY_ANNOTATIONS
            );
            return;
        }
        if (!modulesWithoutAnnotations.isEmpty()) {
            if (addAnnotationsDependency(project, modulesWithoutAnnotations, defaultNullable, INFER_NULLITY_ANNOTATIONS)) {
                restartAnalysis(project, scope);
            }
            return;
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        UsageInfo[] usageInfos = findUsages(annotateLocaVars, project, scope, fileCount[0]);
        if (usageInfos == null) {
            return;
        }

        processUsages(annotateLocaVars, project, scope, usageInfos);
    }

    protected void processUsages(
        boolean annotateLocaVars,
        @Nonnull Project project,
        @Nonnull AnalysisScope scope,
        @Nonnull UsageInfo[] usageInfos
    ) {
        if (usageInfos.length < 5) {
            applyRunnable(project, () -> usageInfos).run();
        }
        else {
            showUsageView(annotateLocaVars, project, usageInfos, scope);
        }
    }

    @RequiredUIAccess
    public static boolean addAnnotationsDependency(
        @Nonnull Project project,
        @Nonnull Set<Module> modulesWithoutAnnotations,
        @Nonnull String annoFQN,
        String title
    ) {
        Library annotationsLib = LibraryUtil.findLibraryByClass(annoFQN, project);
        if (annotationsLib != null) {
            @NonNls
            String message = "Module" + (modulesWithoutAnnotations.size() == 1 ? " " : "s ");
            message += StringUtil.join(modulesWithoutAnnotations, Module::getName, ", ");
            message += (modulesWithoutAnnotations.size() == 1 ? " doesn't" : " don't");
            message += " refer to the existing '" + annotationsLib.getName() + "' library" +
                " with Consulo nullity annotations. Would you like to add the dependenc";
            message += (modulesWithoutAnnotations.size() == 1 ? "y" : "ies") + " now?";
            if (Messages.showOkCancelDialog(project, message, title, UIUtil.getErrorIcon()) == Messages.OK) {
                project.getApplication().runWriteAction(() ->
                {
                    for (Module module : modulesWithoutAnnotations) {
                        ModuleRootModificationUtil.addDependency(module, annotationsLib);
                    }
                });
                return true;
            }
            return false;
        }

		/*if (Messages.showOkCancelDialog(project, "It is required that JetBrains annotations" + " be available in all your project sources.\n\nYou will need to add annotations.jar as a library. " +
        "It is possible to configure custom JAR\nin e.g. Constant Conditions & Exceptions inspection or use JetBrains annotations available in installation. " + "\nIntelliJ IDEA nullity " +
				"annotations are freely usable and redistributable under the Apache 2.0 license.\nWould you like to do it now?", title, Messages.getErrorIcon()) == Messages.OK)
		{
			Module firstModule = modulesWithoutAnnotations.iterator().next();
			JavaProjectModelModificationService.getInstance(project).addDependency(modulesWithoutAnnotations, JetBrainsAnnotationsExternalLibraryResolver.getAnnotationsLibraryDescriptor(firstModule)
					, DependencyScope.COMPILE);
			return true;
		} */
        return false;
    }

    @Nullable
    protected UsageInfo[] findUsages(
        boolean annotateLocaVars,
        @Nonnull Project project,
        @Nonnull AnalysisScope scope,
        int fileCount
    ) {
        NullityInferrer inferrer = new NullityInferrer(annotateLocaVars, project);
        PsiManager psiManager = PsiManager.getInstance(project);
        Runnable searchForUsages = () -> scope.accept(new PsiElementVisitor() {
            int myFileCount;

            @Override
            public void visitFile(PsiFile file) {
                myFileCount++;
                VirtualFile virtualFile = file.getVirtualFile();
                FileViewProvider viewProvider = psiManager.findViewProvider(virtualFile);
                Document document = viewProvider == null ? null : viewProvider.getDocument();
                if (document == null || virtualFile.getFileType().isBinary()) {
                    return; //do not inspect binary files
                }
                ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
                if (progressIndicator != null) {
                    progressIndicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
                    progressIndicator.setFraction(((double)myFileCount) / fileCount);
                }
                if (file instanceof PsiJavaFile) {
                    inferrer.collect(file);
                }
            }
        });
        if (project.getApplication().isDispatchThread()) {
            if (!ProgressManager.getInstance()
                .runProcessWithProgressSynchronously(searchForUsages, INFER_NULLITY_ANNOTATIONS, true, project)) {
                return null;
            }
        }
        else {
            searchForUsages.run();
        }

        List<UsageInfo> usages = new ArrayList<>();
        inferrer.collect(usages);
        return usages.toArray(new UsageInfo[usages.size()]);
    }

    private static Runnable applyRunnable(Project project, Supplier<UsageInfo[]> computable) {
        return () -> {
            LocalHistoryAction action = LocalHistory.getInstance().startAction(INFER_NULLITY_ANNOTATIONS);
            try {
                new WriteCommandAction(project, INFER_NULLITY_ANNOTATIONS) {
                    @Override
                    protected void run(@Nonnull Result result) throws Throwable {
                        UsageInfo[] infos = computable.get();
                        if (infos.length > 0) {

                            Set<PsiElement> elements = new LinkedHashSet<>();
                            for (UsageInfo info : infos) {
                                PsiElement element = info.getElement();
                                if (element != null) {
                                    ContainerUtil.addIfNotNull(elements, element.getContainingFile());
                                }
                            }
                            if (!FileModificationService.getInstance().preparePsiElementsForWrite(elements)) {
                                return;
                            }

                            SequentialModalProgressTask progressTask =
                                new SequentialModalProgressTask(project, INFER_NULLITY_ANNOTATIONS, false);
                            progressTask.setMinIterationTime(200);
                            progressTask.setTask(new AnnotateTask(project, progressTask, infos));
                            ProgressManager.getInstance().run(progressTask);
                        }
                        else {
                            NullityInferrer.nothingFoundMessage(project);
                        }
                    }
                }.execute();
            }
            finally {
                action.finish();
            }
        };
    }

    @RequiredUIAccess
    protected void restartAnalysis(Project project, AnalysisScope scope) {
        DumbService.getInstance(project).smartInvokeLater(() -> {
            if (DumbService.isDumb(project)) {
                restartAnalysis(project, scope);
            }
            else {
                analyze(project, scope);
            }
        });
    }

    private void showUsageView(
        boolean annotateLocaVars,
        @Nonnull Project project,
        UsageInfo[] usageInfos,
        @Nonnull AnalysisScope scope
    ) {
        UsageTarget[] targets = UsageTarget.EMPTY_ARRAY;
        SimpleReference<Usage[]> convertUsagesRef = new SimpleReference<>();
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> project.getApplication().runReadAction(() -> convertUsagesRef.set(UsageInfo2UsageAdapter.convert(usageInfos))),
            "Preprocess Usages",
            true,
            project
        )) {
            return;
        }

        if (convertUsagesRef.isNull()) {
            return;
        }
        Usage[] usages = convertUsagesRef.get();

        UsageViewPresentation presentation = new UsageViewPresentation();
        presentation.setTabText("Infer Nullity Preview");
        presentation.setShowReadOnlyStatusAsRed(true);
        presentation.setShowCancelButton(true);
        presentation.setUsagesString(RefactoringLocalize.usageviewUsagestext().get());

        UsageView usageView =
            UsageViewManager.getInstance(project).showUsages(targets, usages, presentation, rerunFactory(annotateLocaVars, project, scope));

        Runnable refactoringRunnable = applyRunnable(project, () ->
        {
            Set<UsageInfo> infos = UsageViewUtil.getNotExcludedUsageInfos(usageView);
            return infos.toArray(new UsageInfo[infos.size()]);
        });

        String canNotMakeString =
            "Cannot perform operation.\nThere were changes in code after usages have been found.\nPlease perform operation search again.";

        usageView.addPerformOperationAction(
            refactoringRunnable,
            INFER_NULLITY_ANNOTATIONS,
            canNotMakeString,
            INFER_NULLITY_ANNOTATIONS,
            false
        );
    }

    @Nonnull
    private Supplier<UsageSearcher> rerunFactory(
        boolean annotateLocaVars,
        @Nonnull Project project,
        @Nonnull AnalysisScope scope
    ) {
        return () -> new UsageInfoSearcherAdapter() {
            @Nonnull
            @Override
            protected UsageInfo[] findUsages() {
                return ObjectUtil.notNull(
                    InferNullityAnnotationsAction.this.findUsages(annotateLocaVars, project, scope, scope.getFileCount()),
                    UsageInfo.EMPTY_ARRAY
                );
            }

            @Override
            public void generate(@Nonnull Predicate<Usage> processor) {
                processUsages(processor, project);
            }
        };
    }

    @RequiredUIAccess
    @Override
    protected void extendMainLayout(BaseAnalysisActionDialog dialog, VerticalLayout layout, Project project) {
        myAnnotateLocalVariablesCb =
            CheckBox.create(LocalizeValue.localizeTODO("Annotate local variables"));
        myAnnotateLocalVariablesCb.setValue(PropertiesComponent.getInstance().getBoolean(ANNOTATE_LOCAL_VARIABLES));
        layout.add(myAnnotateLocalVariablesCb);
    }

    @Override
    protected void canceled() {
        super.canceled();
        myAnnotateLocalVariablesCb = null;
    }
}
