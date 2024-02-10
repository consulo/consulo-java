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
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.application.util.function.Computable;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.analysis.AnalysisUIOptions;
import consulo.ide.impl.idea.analysis.BaseAnalysisActionDialog;
import consulo.ide.impl.idea.openapi.project.ProjectUtil;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import consulo.language.editor.refactoring.ContextAwareActionHandler;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiRecursiveElementVisitor;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.localHistory.LocalHistory;
import consulo.localHistory.LocalHistoryAction;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.project.ui.wm.WindowManager;
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
  public static final String REFACTORING_NAME = RefactoringBundle.message("replace.method.code.duplicates.title");
  private static final Logger LOG = Logger.getInstance(MethodDuplicatesHandler.class);

  @Override
  public boolean isAvailableForQuickList(@Nonnull Editor editor, @Nonnull PsiFile file, @Nonnull DataContext dataContext) {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    return getCannotRefactorMessage(PsiTreeUtil.getParentOfType(element, PsiMember.class)) == null;
  }

  @Override
  public void invoke(@Nonnull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    final PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
    final String cannotRefactorMessage = getCannotRefactorMessage(member);
    if (cannotRefactorMessage != null) {
      String message = RefactoringBundle.getCannotRefactorMessage(cannotRefactorMessage);
      showErrorMessage(message, project, editor);
      return;
    }

    final AnalysisScope scope = new AnalysisScope(file);
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    final BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog(RefactoringBundle.message("replace.method.duplicates.scope.chooser.title", REFACTORING_NAME),
        RefactoringBundle.message("replace.method.duplicates.scope.chooser.message"), project, scope, module != null ? module.getName() : null, false, AnalysisUIOptions.getInstance(project),
        element);
    if (dlg.showAndGet()) {
      ProgressManager.getInstance().run(new Task.Backgroundable(project, "Locate duplicates", true) {
        @Override
        public void run(@Nonnull ProgressIndicator indicator) {
          indicator.setIndeterminate(true);
          invokeOnScope(project, member, dlg.getScope(AnalysisUIOptions.getInstance(project), scope, project, module));
        }
      });
    }
  }

  @Nullable
  private static String getCannotRefactorMessage(PsiMember member) {
    if (member == null) {
      return RefactoringBundle.message("locate.caret.inside.a.method");
    }
    if (member instanceof PsiMethod) {
      if (((PsiMethod) member).isConstructor()) {
        return RefactoringBundle.message("replace.with.method.call.does.not.work.for.constructors");
      }
      final PsiCodeBlock body = ((PsiMethod) member).getBody();
      if (body == null) {
        return RefactoringBundle.message("method.does.not.have.a.body", member.getName());
      }
      final PsiStatement[] statements = body.getStatements();
      if (statements.length == 0) {
        return RefactoringBundle.message("method.has.an.empty.body", member.getName());
      }
    } else if (member instanceof PsiField) {
      final PsiField field = (PsiField) member;
      if (field.getInitializer() == null) {
        return "Field " + member.getName() + " doesn't have initializer";
      }
      final PsiClass containingClass = field.getContainingClass();
      if (!field.hasModifierProperty(PsiModifier.FINAL) || !field.hasModifierProperty(PsiModifier.STATIC) ||
          containingClass == null || containingClass.getQualifiedName() == null) {
        return "Replace Duplicates works with constants only";
      }
    } else {
      return "Caret should be inside method or constant";
    }
    return null;
  }

  public static void invokeOnScope(final Project project, final PsiMember member, final AnalysisScope scope) {
    invokeOnScope(project, Collections.singleton(member), scope, false);
  }

  public static void invokeOnScope(final Project project, final Set<PsiMember> members, final AnalysisScope scope, boolean silent) {
    final Map<PsiMember, List<Match>> duplicates = new HashMap<PsiMember, List<Match>>();
    final int fileCount = scope.getFileCount();
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) {
      progressIndicator.setIndeterminate(false);
    }

    final Map<PsiMember, Set<Module>> memberWithModulesMap = new HashMap<PsiMember, Set<Module>>();
    for (final PsiMember member : members) {
      final Module module = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
        @Override
        public Module compute() {
          return ModuleUtilCore.findModuleForPsiElement(member);
        }
      });
      if (module != null) {
        final HashSet<Module> dependencies = new HashSet<Module>();
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            ModuleUtilCore.collectModulesDependsOn(module, dependencies);
          }
        });
        memberWithModulesMap.put(member, dependencies);
      }
    }

    scope.accept(new PsiRecursiveElementVisitor() {
      private int myFileCount = 0;

      @Override
      public void visitFile(final PsiFile file) {
        if (progressIndicator != null) {
          if (progressIndicator.isCanceled()) {
            return;
          }
          progressIndicator.setFraction(((double) myFileCount++) / fileCount);
          final VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile != null) {
            progressIndicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
          }
        }
        final Module targetModule = ModuleUtilCore.findModuleForPsiElement(file);
        if (targetModule == null) {
          return;
        }
        for (Map.Entry<PsiMember, Set<Module>> entry : memberWithModulesMap.entrySet()) {
          final Set<Module> dependencies = entry.getValue();
          if (dependencies == null || !dependencies.contains(targetModule)) {
            continue;
          }

          final PsiMember method = entry.getKey();
          final List<Match> matchList = hasDuplicates(file, method);
          for (Iterator<Match> iterator = matchList.iterator(); iterator.hasNext(); ) {
            Match match = iterator.next();
            final PsiElement matchStart = match.getMatchStart();
            final PsiElement matchEnd = match.getMatchEnd();
            for (PsiMember psiMember : members) {
              if (PsiTreeUtil.isAncestor(psiMember, matchStart, false) || PsiTreeUtil.isAncestor(psiMember, matchEnd, false)) {
                iterator.remove();
                break;
              }
            }
          }
          if (!matchList.isEmpty()) {
            List<Match> matches = duplicates.get(method);
            if (matches == null) {
              matches = new ArrayList<Match>();
              duplicates.put(method, matches);
            }
            matches.addAll(matchList);
          }
        }
      }
    });
    if (duplicates.isEmpty()) {
      if (!silent) {
        final Runnable nothingFoundRunnable = new Runnable() {
          @Override
          public void run() {
            final String message = RefactoringBundle.message("idea.has.not.found.any.code.that.can.be.replaced.with.method.call", Application.get().getName().get());
            Messages.showInfoMessage(project, message, REFACTORING_NAME);
          }
        };
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          nothingFoundRunnable.run();
        } else {
          ApplicationManager.getApplication().invokeLater(nothingFoundRunnable, Application.get().getNoneModalityState());
        }
      }
    } else {
      replaceDuplicate(project, duplicates, members);
    }
  }

  private static void replaceDuplicate(final Project project, final Map<PsiMember, List<Match>> duplicates, final Set<PsiMember> methods) {
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null && progressIndicator.isCanceled()) {
      return;
    }

    final Runnable replaceRunnable = new Runnable() {
      @Override
      public void run() {
        LocalHistoryAction a = LocalHistory.getInstance().startAction(REFACTORING_NAME);
        try {
          for (final PsiMember member : methods) {
            final List<Match> matches = duplicates.get(member);
            if (matches == null) {
              continue;
            }
            final int duplicatesNo = matches.size();
            WindowManager.getInstance().getStatusBar(project).setInfo(getStatusMessage(duplicatesNo));
            CommandProcessor.getInstance().executeCommand(project, new Runnable() {
              @Override
              public void run() {
                PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Runnable() {
                  @Override
                  public void run() {
                    final MatchProvider matchProvider = member instanceof PsiMethod ? new MethodDuplicatesMatchProvider((PsiMethod) member,
                        matches) : new ConstantMatchProvider(member, project, matches);
                    DuplicatesImpl.invoke(project, matchProvider);
                  }
                });
              }
            }, REFACTORING_NAME, REFACTORING_NAME);

            WindowManager.getInstance().getStatusBar(project).setInfo("");
          }
        } finally {
          a.finish();
        }
      }
    };
    ApplicationManager.getApplication().invokeLater(replaceRunnable, Application.get().getNoneModalityState());
  }

  public static List<Match> hasDuplicates(final PsiFile file, final PsiMember member) {
    final DuplicatesFinder duplicatesFinder = createDuplicatesFinder(member);
    if (duplicatesFinder == null) {
      return Collections.emptyList();
    }

    return duplicatesFinder.findDuplicates(file);
  }

  @Nullable
  public static DuplicatesFinder createDuplicatesFinder(PsiMember member) {
    PsiElement[] pattern;
    ReturnValue matchedReturnValue = null;
    if (member instanceof PsiMethod) {
      final PsiCodeBlock body = ((PsiMethod) member).getBody();
      LOG.assertTrue(body != null);
      final PsiStatement[] statements = body.getStatements();
      pattern = statements;
      matchedReturnValue = null;
      if (statements.length != 1 || !(statements[0] instanceof PsiReturnStatement)) {
        final PsiStatement lastStatement = statements.length > 0 ? statements[statements.length - 1] : null;
        if (lastStatement instanceof PsiReturnStatement) {
          final PsiExpression returnValue = ((PsiReturnStatement) lastStatement).getReturnValue();
          if (returnValue instanceof PsiReferenceExpression) {
            final PsiElement resolved = ((PsiReferenceExpression) returnValue).resolve();
            if (resolved instanceof PsiVariable) {
              pattern = new PsiElement[statements.length - 1];
              System.arraycopy(statements, 0, pattern, 0, statements.length - 1);
              matchedReturnValue = new VariableReturnValue((PsiVariable) resolved);
            }
          }
        }
      } else {
        final PsiExpression returnValue = ((PsiReturnStatement) statements[0]).getReturnValue();
        if (returnValue != null) {
          pattern = new PsiElement[]{returnValue};
        }
      }
    } else {
      pattern = new PsiElement[]{((PsiField) member).getInitializer()};
    }
    if (pattern.length == 0) {
      return null;
    }
    final List<? extends PsiVariable> inputVariables = member instanceof PsiMethod ? Arrays.asList(((PsiMethod) member).getParameterList().getParameters()) : new ArrayList<PsiVariable>();
    return new DuplicatesFinder(pattern, new InputVariables(inputVariables, member.getProject(), new LocalSearchScope(pattern), false), matchedReturnValue, new ArrayList<PsiVariable>());
  }

  static String getStatusMessage(final int duplicatesNo) {
    return RefactoringBundle.message("method.duplicates.found.message", duplicatesNo);
  }

  private static void showErrorMessage(String message, Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.METHOD_DUPLICATES);
  }

  @Override
  public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    throw new UnsupportedOperationException();
  }
}
