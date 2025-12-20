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

    private void _handleInsert(InsertionContext context, JavaPsiClassReferenceElement item) {
      Editor editor = context.getEditor();
      PsiClass psiClass = item.getObject();
      if (!psiClass.isValid()) {
        return;
      }

      int endOffset = editor.getCaretModel().getOffset();
      String qname = psiClass.getQualifiedName();
      if (qname == null) {
        return;
      }

      if (endOffset == 0) {
        return;
      }

      Document document = editor.getDocument();
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(psiClass.getProject());
      PsiFile file = context.getFile();
      if (file.findElementAt(endOffset - 1) == null) {
        return;
      }

      OffsetKey key = OffsetKey.create("endOffset", false);
      context.getOffsetMap().addOffset(key, endOffset);
      PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting();

      int newOffset = context.getOffsetMap().getOffset(key);
      if (newOffset >= 0) {
        endOffset = newOffset;
      } else {
        LOG.error(endOffset + " became invalid: " + context.getOffsetMap() + "; inserting " + qname);
      }

      RangeMarker toDelete = JavaCompletionUtil.insertTemporary(endOffset, document, " ");
      psiDocumentManager.commitAllDocuments();
      PsiReference psiReference = file.findReferenceAt(endOffset - 1);

      boolean insertFqn = true;
      if (psiReference != null) {
        PsiManager psiManager = file.getManager();
        if (psiManager.areElementsEquivalent(psiClass, JavaCompletionUtil.resolveReference(psiReference))) {
          insertFqn = false;
        } else if (psiClass.isValid()) {
          try {
            context.setTailOffset(psiReference.getRangeInElement().getEndOffset() + psiReference.getElement().getTextRange().getStartOffset());
            PsiElement newUnderlying = psiReference.bindToElement(psiClass);
            if (newUnderlying != null) {
              PsiElement psiElement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newUnderlying);
              if (psiElement != null) {
                for (PsiReference reference : psiElement.getReferences()) {
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
    public void handleInsert(@Nonnull InsertionContext context, @Nonnull JavaPsiClassReferenceElement item) {
      _handleInsert(context, item);
      item.getTailType().processTail(context.getEditor(), context.getEditor().getCaretModel().getOffset());
    }

  };

  public static final InsertHandler<JavaPsiClassReferenceElement> INSERT_FQN = (context, item) -> {
    String qName = item.getQualifiedName();
    if (qName != null) {
      int start = JavaCompletionUtil.findQualifiedNameStart(context);
      context.getDocument().replaceString(start, context.getTailOffset(), qName);
      LOG.assertTrue(context.getTailOffset() >= 0);
    }
  };

  public static void processJavaClasses(@Nonnull CompletionParameters parameters,
                                        @Nonnull PrefixMatcher prefixMatcher,
                                        boolean filterByScope,
                                        @Nonnull Consumer<? super PsiClass> consumer) {
    PsiElement context = parameters.getPosition();
    Project project = context.getProject();
    GlobalSearchScope scope = filterByScope ? context.getContainingFile().getResolveScope() : GlobalSearchScope.allScope(project);

    processJavaClasses(prefixMatcher, project, scope, new LimitedAccessibleClassPreprocessor(parameters, filterByScope, c -> {
      consumer.accept(c);
      return true;
    }));
  }

  public static void processJavaClasses(@Nonnull PrefixMatcher prefixMatcher,
                                        @Nonnull Project project,
                                        @Nonnull GlobalSearchScope scope,
                                        @Nonnull Processor<? super PsiClass> processor) {
    Set<String> names = new HashSet<>(10000);
    AllClassesSearchExecutor.processClassNames(project, scope, s -> {
      if (prefixMatcher.prefixMatches(s)) {
        names.add(s);
      }
      return true;
    });
    LinkedHashSet<String> sorted = prefixMatcher.sortMatching(names);
    AllClassesSearchExecutor.processClassesByNames(project, scope, sorted, processor);
  }

  public static boolean isAcceptableInContext(@Nonnull PsiElement context,
                                              @Nonnull PsiClass psiClass,
                                              boolean filterByScope, boolean pkgContext) {
    ProgressManager.checkCanceled();

    if (JavaCompletionUtil.isInExcludedPackage(psiClass, false)) {
      return false;
    }

    String qualifiedName = psiClass.getQualifiedName();
    if (qualifiedName == null) {
      return false;
    }

    if (!filterByScope && !(psiClass instanceof PsiCompiledElement)) {
      return true;
    }

    return JavaCompletionUtil.isSourceLevelAccessible(context, psiClass, pkgContext);
  }

  public static JavaPsiClassReferenceElement createLookupItem(@Nonnull PsiClass psiClass,
                                                              InsertHandler<JavaPsiClassReferenceElement> insertHandler) {
    JavaPsiClassReferenceElement item = new JavaPsiClassReferenceElement(psiClass);
    item.setInsertHandler(insertHandler);
    return item;
  }

}
