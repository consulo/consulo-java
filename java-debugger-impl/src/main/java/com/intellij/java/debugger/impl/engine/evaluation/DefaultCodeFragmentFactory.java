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
package com.intellij.java.debugger.impl.engine.evaluation;

import com.intellij.java.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.java.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.java.debugger.engine.evaluation.TextWithImports;
import com.intellij.java.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.codeinsight.RuntimeTypeEvaluator;
import com.intellij.java.debugger.impl.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.JavaCodeFragment;
import com.intellij.java.language.psi.JavaCodeFragmentFactory;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.application.progress.ProgressManager;
import consulo.application.util.Semaphore;
import consulo.java.analysis.impl.codeInsight.completion.JavaCompletionUtilCore;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionService;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.PairFunction;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Eugene Zhuravlev
 * Date: Jun 7, 2005
 */
public class DefaultCodeFragmentFactory extends CodeFragmentFactory {
  private static final class SingletonHolder {
    public static final DefaultCodeFragmentFactory ourInstance = new DefaultCodeFragmentFactory();
  }

  public static DefaultCodeFragmentFactory getInstance() {
    return SingletonHolder.ourInstance;
  }

  @Override
  public JavaCodeFragment createPresentationCodeFragment(final TextWithImports item, final PsiElement context, final Project project) {
    return createCodeFragment(item, context, project);
  }

  @Override
  public JavaCodeFragment createCodeFragment(TextWithImports item, PsiElement context, final Project project) {
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    final String text = item.getText();

    final JavaCodeFragment fragment;
    if (CodeFragmentKind.EXPRESSION == item.getKind()) {
      final String expressionText = StringUtil.endsWithChar(text, ';') ? text.substring(0, text.length() - 1) : text;
      fragment = factory.createExpressionCodeFragment(expressionText, context, null, true);
    }
    else /*if (CodeFragmentKind.CODE_BLOCK == item.getKind())*/ {
      fragment = factory.createCodeBlockCodeFragment(text, context, true);
    }

    if (item.getImports().length() > 0) {
      fragment.addImportsFromString(item.getImports());
    }
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    //noinspection HardCodedStringLiteral
    fragment.putUserData(KEY, "DebuggerComboBoxEditor.IS_DEBUGGER_EDITOR");
    fragment.putCopyableUserData(JavaCompletionUtilCore.DYNAMIC_TYPE_EVALUATOR,
                                 new PairFunction<PsiExpression, CompletionParameters, PsiType>() {
                                   @Override
                                   public PsiType fun(PsiExpression expression, CompletionParameters parameters) {
                                     if (!RuntimeTypeEvaluator.isSubtypeable(expression)) {
                                       return null;
                                     }

                                     if (parameters.getInvocationCount() <= 1 && JavaCompletionUtilCore.mayHaveSideEffects(expression)) {
                                       final CompletionService service = CompletionService.getCompletionService();
                                       if (parameters.getInvocationCount() < 2) {
                                         service.setAdvertisementText("Invoke completion once more to see runtime type variants");
                                       }
                                       return null;
                                     }

                                     final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(project).getContext();
                                     DebuggerSession debuggerSession = debuggerContext.getDebuggerSession();
                                     if (debuggerSession != null && debuggerContext.getSuspendContext() != null) {
                                       final Semaphore semaphore = new Semaphore();
                                       semaphore.down();
                                       final AtomicReference<PsiType> nameRef = new AtomicReference<PsiType>();
                                       final RuntimeTypeEvaluator worker = new RuntimeTypeEvaluator(null,
                                                                                                    expression,
                                                                                                    debuggerContext,
                                                                                                    ProgressManager.getInstance()
                                                                                                                   .getProgressIndicator()) {
                                         @Override
                                         protected void typeCalculationFinished(@Nullable PsiType type) {
                                           nameRef.set(type);
                                           semaphore.up();
                                         }
                                       };
                                       debuggerSession.getProcess().getManagerThread().invoke(worker);
                                       for (int i = 0; i < 50; i++) {
                                         ProgressManager.checkCanceled();
                                         if (semaphore.waitFor(20)) {
                                           break;
                                         }
                                       }
                                       return nameRef.get();
                                     }
                                     return null;
                                   }
                                 });

    return fragment;
  }

  @Override
  public boolean isContextAccepted(PsiElement contextElement) {
    return true; // default factory works everywhere debugger can stop
  }

  @Override
  @Nonnull
  public LanguageFileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  public EvaluatorBuilder getEvaluatorBuilder() {
    return EvaluatorBuilderImpl.getInstance();
  }

  public static final Key<String> KEY = Key.create("DefaultCodeFragmentFactory.KEY");

  public static boolean isDebuggerFile(PsiFile file) {
    return file.getUserData(KEY) != null;
  }
}
