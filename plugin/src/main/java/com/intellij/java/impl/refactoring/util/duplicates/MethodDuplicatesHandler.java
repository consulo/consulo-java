/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.util.duplicates;

import com.intellij.java.analysis.impl.refactoring.extractMethod.InputVariables;
import com.intellij.java.analysis.impl.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.java.analysis.impl.refactoring.util.duplicates.Match;
import com.intellij.java.analysis.impl.refactoring.util.duplicates.ReturnValue;
import com.intellij.java.analysis.impl.refactoring.util.duplicates.VariableReturnValue;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.openapi.project.ProjectUtil;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import consulo.language.editor.refactoring.ContextAwareActionHandler;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.editor.ui.awt.scope.BaseAnalysisActionDialog;
import consulo.language.editor.ui.scope.AnalysisUIOptions;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiRecursiveElementVisitor;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localHistory.LocalHistory;
import consulo.localHistory.LocalHistoryAction;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.util.ModuleContentUtil;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.undoRedo.CommandProcessor;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author dsl
 */
public class MethodDuplicatesHandler implements RefactoringActionHandler, ContextAwareActionHandler {
    public static final LocalizeValue REFACTORING_NAME = RefactoringLocalize.replaceMethodCodeDuplicatesTitle();
    private static final Logger LOG = Logger.getInstance(MethodDuplicatesHandler.class);

    @Override
    @RequiredReadAction
    public boolean isAvailableForQuickList(@Nonnull Editor editor, @Nonnull PsiFile file, @Nonnull DataContext dataContext) {
        PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
        return getCannotRefactorMessage(PsiTreeUtil.getParentOfType(element, PsiMember.class)) == null;
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull final Project project, Editor editor, PsiFile file, DataContext dataContext) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        final PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
        String cannotRefactorMessage = getCannotRefactorMessage(member);
        if (cannotRefactorMessage != null) {
            LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(cannotRefactorMessage);
            showErrorMessage(message, project, editor);
            return;
        }

        final AnalysisScope scope = new AnalysisScope(file);
        Module module = file.getModule();
        final BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog(
            RefactoringLocalize.replaceMethodDuplicatesScopeChooserTitle(REFACTORING_NAME).get(),
            RefactoringLocalize.replaceMethodDuplicatesScopeChooserMessage().get(),
            project,
            scope,
            module != null ? module.getName() : null,
            false,
            AnalysisUIOptions.getInstance(project),
            element
        );
        if (dlg.showAndGet()) {
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Locate duplicates", true) {
                @Override
                public void run(@Nonnull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    invokeOnScope(project, member, dlg.getScope(scope));
                }
            });
        }
    }

    @Nullable
    private static String getCannotRefactorMessage(PsiMember member) {
        if (member == null) {
            return RefactoringLocalize.locateCaretInsideAMethod().get();
        }
        if (member instanceof PsiMethod method) {
            if (method.isConstructor()) {
                return RefactoringLocalize.replaceWithMethodCallDoesNotWorkForConstructors().get();
            }
            PsiCodeBlock body = method.getBody();
            if (body == null) {
                return RefactoringLocalize.methodDoesNotHaveABody(member.getName()).get();
            }
            PsiStatement[] statements = body.getStatements();
            if (statements.length == 0) {
                return RefactoringLocalize.methodHasAnEmptyBody(member.getName()).get();
            }
        }
        else if (member instanceof PsiField field) {
            if (field.getInitializer() == null) {
                return "Field " + member.getName() + " doesn't have initializer";
            }
            PsiClass containingClass = field.getContainingClass();
            if (!field.isFinal() || !field.isStatic() || containingClass == null || containingClass.getQualifiedName() == null) {
                return "Replace Duplicates works with constants only";
            }
        }
        else {
            return "Caret should be inside method or constant";
        }
        return null;
    }

    public static void invokeOnScope(Project project, PsiMember member, AnalysisScope scope) {
        invokeOnScope(project, Collections.singleton(member), scope, false);
    }

    public static void invokeOnScope(final Project project, final Set<PsiMember> members, AnalysisScope scope, boolean silent) {
        final Map<PsiMember, List<Match>> duplicates = new HashMap<>();
        final int fileCount = scope.getFileCount();
        final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        if (progressIndicator != null) {
            progressIndicator.setIndeterminate(false);
        }

        final Map<PsiMember, Set<Module>> memberWithModulesMap = new HashMap<>();
        for (PsiMember member : members) {
            Module module = ReadAction.compute(member::getModule);
            if (module != null) {
                Set<Module> dependencies = new HashSet<>();
                ReadAction.run(() -> ModuleContentUtil.collectModulesDependsOn(module, dependencies));
                memberWithModulesMap.put(member, dependencies);
            }
        }

        scope.accept(new PsiRecursiveElementVisitor() {
            private int myFileCount = 0;

            @Override
            @RequiredReadAction
            public void visitFile(PsiFile file) {
                if (progressIndicator != null) {
                    if (progressIndicator.isCanceled()) {
                        return;
                    }
                    progressIndicator.setFraction(((double) myFileCount++) / fileCount);
                    VirtualFile virtualFile = file.getVirtualFile();
                    if (virtualFile != null) {
                        progressIndicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
                    }
                }
                Module targetModule = file.getModule();
                if (targetModule == null) {
                    return;
                }
                for (Map.Entry<PsiMember, Set<Module>> entry : memberWithModulesMap.entrySet()) {
                    Set<Module> dependencies = entry.getValue();
                    if (dependencies == null || !dependencies.contains(targetModule)) {
                        continue;
                    }

                    PsiMember method = entry.getKey();
                    List<Match> matchList = hasDuplicates(file, method);
                    for (Iterator<Match> iterator = matchList.iterator(); iterator.hasNext(); ) {
                        Match match = iterator.next();
                        PsiElement matchStart = match.getMatchStart();
                        PsiElement matchEnd = match.getMatchEnd();
                        for (PsiMember psiMember : members) {
                            if (PsiTreeUtil.isAncestor(psiMember, matchStart, false) || PsiTreeUtil.isAncestor(
                                psiMember,
                                matchEnd,
                                false
                            )) {
                                iterator.remove();
                                break;
                            }
                        }
                    }
                    if (!matchList.isEmpty()) {
                        List<Match> matches = duplicates.get(method);
                        if (matches == null) {
                            matches = new ArrayList<>();
                            duplicates.put(method, matches);
                        }
                        matches.addAll(matchList);
                    }
                }
            }
        });
        if (duplicates.isEmpty()) {
            if (!silent) {
                Application application = Application.get();
                Runnable nothingFoundRunnable = () -> {
                    LocalizeValue message =
                        RefactoringLocalize.ideaHasNotFoundAnyCodeThatCanBeReplacedWithMethodCall(application.getName());
                    Messages.showInfoMessage(project, message.get(), REFACTORING_NAME.get());
                };
                if (application.isUnitTestMode()) {
                    nothingFoundRunnable.run();
                }
                else {
                    application.invokeLater(nothingFoundRunnable, application.getNoneModalityState());
                }
            }
        }
        else {
            replaceDuplicate(project, duplicates, members);
        }
    }

    private static void replaceDuplicate(Project project, Map<PsiMember, List<Match>> duplicates, Set<PsiMember> methods) {
        ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        if (progressIndicator != null && progressIndicator.isCanceled()) {
            return;
        }

        Runnable replaceRunnable = () -> {
            LocalHistoryAction a = LocalHistory.getInstance().startAction(REFACTORING_NAME);
            try {
                for (PsiMember member : methods) {
                    List<Match> matches = duplicates.get(member);
                    if (matches == null) {
                        continue;
                    }
                    int duplicatesNo = matches.size();
                    CommandProcessor.getInstance().newCommand()
                        .project(project)
                        .name(REFACTORING_NAME)
                        .groupId(REFACTORING_NAME)
                        .run(() -> PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(() -> {
                            MatchProvider matchProvider = member instanceof PsiMethod method
                                ? new MethodDuplicatesMatchProvider(method, matches)
                                : new ConstantMatchProvider(member, project, matches);
                            DuplicatesImpl.invoke(project, matchProvider);
                        }));
                }
            }
            finally {
                a.finish();
            }
        };
        Application application = Application.get();
        application.invokeLater(replaceRunnable, application.getNoneModalityState());
    }

    @RequiredReadAction
    public static List<Match> hasDuplicates(PsiFile file, PsiMember member) {
        DuplicatesFinder duplicatesFinder = createDuplicatesFinder(member);
        if (duplicatesFinder == null) {
            return Collections.emptyList();
        }

        return duplicatesFinder.findDuplicates(file);
    }

    @Nullable
    @RequiredReadAction
    public static DuplicatesFinder createDuplicatesFinder(PsiMember member) {
        PsiElement[] pattern;
        ReturnValue matchedReturnValue = null;
        if (member instanceof PsiMethod method) {
            PsiCodeBlock body = method.getBody();
            LOG.assertTrue(body != null);
            PsiStatement[] statements = body.getStatements();
            pattern = statements;
            matchedReturnValue = null;
            if (statements.length != 1 || !(statements[0] instanceof PsiReturnStatement)) {
                if (statements.length > 0
                    && statements[statements.length - 1] instanceof PsiReturnStatement returnStmt
                    && returnStmt.getReturnValue() instanceof PsiReferenceExpression returnRef
                    && returnRef.resolve() instanceof PsiVariable variable) {
                    pattern = new PsiElement[statements.length - 1];
                    System.arraycopy(statements, 0, pattern, 0, statements.length - 1);
                    matchedReturnValue = new VariableReturnValue(variable);
                }
            }
            else {
                PsiExpression returnValue = ((PsiReturnStatement) statements[0]).getReturnValue();
                if (returnValue != null) {
                    pattern = new PsiElement[]{returnValue};
                }
            }
        }
        else {
            pattern = new PsiElement[]{((PsiField) member).getInitializer()};
        }
        if (pattern.length == 0) {
            return null;
        }

        List<? extends PsiVariable> inputVariables = member instanceof PsiMethod method
            ? Arrays.asList(method.getParameterList().getParameters())
            : new ArrayList<>();
        return new DuplicatesFinder(
            pattern,
            new InputVariables(inputVariables, member.getProject(), new LocalSearchScope(pattern), false),
            matchedReturnValue,
            new ArrayList<>()
        );
    }

    static LocalizeValue getStatusMessage(int duplicatesNo) {
        return RefactoringLocalize.methodDuplicatesFoundMessage(duplicatesNo);
    }

    @RequiredUIAccess
    private static void showErrorMessage(@Nonnull LocalizeValue message, Project project, Editor editor) {
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.METHOD_DUPLICATES);
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
        throw new UnsupportedOperationException();
    }
}
