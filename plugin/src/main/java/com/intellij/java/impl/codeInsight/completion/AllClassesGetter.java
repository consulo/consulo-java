// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.indexing.impl.search.AllClassesSearchExecutor;
import com.intellij.java.language.psi.PsiClass;
import consulo.application.progress.ProgressManager;
import consulo.application.util.function.Processor;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.OffsetKey;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

public class AllClassesGetter {
  private static final Logger LOG = Logger.getInstance(AllClassesGetter.class);
  public static final InsertHandler<JavaPsiClassReferenceElement> TRY_SHORTENING = new InsertHandler<JavaPsiClassReferenceElement>() {

    private void _handleInsert(final InsertionContext context, final JavaPsiClassReferenceElement item) {
      final Editor editor = context.getEditor();
      final PsiClass psiClass = item.getObject();
      if (!psiClass.isValid()) {
        return;
      }

      int endOffset = editor.getCaretModel().getOffset();
      final String qname = psiClass.getQualifiedName();
      if (qname == null) {
        return;
      }

      if (endOffset == 0) {
        return;
      }

      final Document document = editor.getDocument();
      final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(psiClass.getProject());
      final PsiFile file = context.getFile();
      if (file.findElementAt(endOffset - 1) == null) {
        return;
      }

      final OffsetKey key = OffsetKey.create("endOffset", false);
      context.getOffsetMap().addOffset(key, endOffset);
      PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting();

      final int newOffset = context.getOffsetMap().getOffset(key);
      if (newOffset >= 0) {
        endOffset = newOffset;
      } else {
        LOG.error(endOffset + " became invalid: " + context.getOffsetMap() + "; inserting " + qname);
      }

      final RangeMarker toDelete = JavaCompletionUtil.insertTemporary(endOffset, document, " ");
      psiDocumentManager.commitAllDocuments();
      PsiReference psiReference = file.findReferenceAt(endOffset - 1);

      boolean insertFqn = true;
      if (psiReference != null) {
        final PsiManager psiManager = file.getManager();
        if (psiManager.areElementsEquivalent(psiClass, JavaCompletionUtil.resolveReference(psiReference))) {
          insertFqn = false;
        } else if (psiClass.isValid()) {
          try {
            context.setTailOffset(psiReference.getRangeInElement().getEndOffset() + psiReference.getElement().getTextRange().getStartOffset());
            final PsiElement newUnderlying = psiReference.bindToElement(psiClass);
            if (newUnderlying != null) {
              final PsiElement psiElement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newUnderlying);
              if (psiElement != null) {
                for (final PsiReference reference : psiElement.getReferences()) {
                  if (psiManager.areElementsEquivalent(psiClass, JavaCompletionUtil.resolveReference(reference))) {
                    insertFqn = false;
                    break;
                  }
                }
              }
            }
          } catch (IncorrectOperationException e) {
            //if it's empty we just insert fqn below
          }
        }
      }
      if (toDelete != null && toDelete.isValid()) {
        document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
        context.setTailOffset(toDelete.getStartOffset());
      }

      if (insertFqn) {
        INSERT_FQN.handleInsert(context, item);
      }
    }

    @Override
    public void handleInsert(@Nonnull final InsertionContext context, @Nonnull final JavaPsiClassReferenceElement item) {
      _handleInsert(context, item);
      item.getTailType().processTail(context.getEditor(), context.getEditor().getCaretModel().getOffset());
    }

  };

  public static final InsertHandler<JavaPsiClassReferenceElement> INSERT_FQN = (context, item) -> {
    final String qName = item.getQualifiedName();
    if (qName != null) {
      int start = JavaCompletionUtil.findQualifiedNameStart(context);
      context.getDocument().replaceString(start, context.getTailOffset(), qName);
      LOG.assertTrue(context.getTailOffset() >= 0);
    }
  };

  public static void processJavaClasses(@Nonnull final CompletionParameters parameters,
                                        @Nonnull final PrefixMatcher prefixMatcher,
                                        final boolean filterByScope,
                                        @Nonnull final Consumer<? super PsiClass> consumer) {
    final PsiElement context = parameters.getPosition();
    final Project project = context.getProject();
    final GlobalSearchScope scope = filterByScope ? context.getContainingFile().getResolveScope() : GlobalSearchScope.allScope(project);

    processJavaClasses(prefixMatcher, project, scope, new LimitedAccessibleClassPreprocessor(parameters, filterByScope, c -> {
      consumer.accept(c);
      return true;
    }));
  }

  public static void processJavaClasses(@Nonnull final PrefixMatcher prefixMatcher,
                                        @Nonnull Project project,
                                        @Nonnull GlobalSearchScope scope,
                                        @Nonnull Processor<? super PsiClass> processor) {
    final Set<String> names = new HashSet<>(10000);
    AllClassesSearchExecutor.processClassNames(project, scope, s -> {
      if (prefixMatcher.prefixMatches(s)) {
        names.add(s);
      }
      return true;
    });
    LinkedHashSet<String> sorted = prefixMatcher.sortMatching(names);
    AllClassesSearchExecutor.processClassesByNames(project, scope, sorted, processor);
  }

  public static boolean isAcceptableInContext(@Nonnull final PsiElement context,
                                              @Nonnull final PsiClass psiClass,
                                              final boolean filterByScope, final boolean pkgContext) {
    ProgressManager.checkCanceled();

    if (JavaCompletionUtil.isInExcludedPackage(psiClass, false)) {
      return false;
    }

    final String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) {
      return false;
    }

    if (!filterByScope && !(psiClass instanceof PsiCompiledElement)) {
      return true;
    }

    return JavaCompletionUtil.isSourceLevelAccessible(context, psiClass, pkgContext);
  }

  public static JavaPsiClassReferenceElement createLookupItem(@Nonnull final PsiClass psiClass,
                                                              final InsertHandler<JavaPsiClassReferenceElement> insertHandler) {
    final JavaPsiClassReferenceElement item = new JavaPsiClassReferenceElement(psiClass);
    item.setInsertHandler(insertHandler);
    return item;
  }

}
