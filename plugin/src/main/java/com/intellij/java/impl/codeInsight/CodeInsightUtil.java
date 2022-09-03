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
package com.intellij.java.impl.codeInsight;

import consulo.language.editor.FileModificationService;
import consulo.language.editor.PsiEquivalenceUtil;
import consulo.language.editor.impl.internal.completion.CompletionUtil;
import consulo.application.util.matcher.PrefixMatcher;
import com.intellij.java.analysis.impl.codeInsight.JavaPsiEquivalenceUtil;
import com.intellij.java.impl.codeInsight.completion.AllClassesGetter;
import com.intellij.java.impl.codeInsight.completion.JavaCompletionUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.codeEditor.Editor;
import consulo.fileEditor.FileEditorManager;
import consulo.navigation.OpenFileDescriptor;
import consulo.application.progress.ProgressManager;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.ide.impl.psi.util.proximity.PsiProximityComparator;
import consulo.ide.impl.idea.util.Consumer;
import consulo.application.util.query.FilteredQuery;
import consulo.application.util.function.Processor;
import consulo.application.util.query.Query;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.JBTreeTraverser;
import consulo.java.analysis.codeInsight.JavaCodeInsightUtilCore;
import consulo.java.language.module.util.JavaClassNames;
import consulo.logging.Logger;

import javax.annotation.Nonnull;
import java.util.*;

public class CodeInsightUtil extends JavaCodeInsightUtilCore {
  private static final Logger LOG = Logger.getInstance(CodeInsightUtil.class);

  public static <T extends PsiMember & PsiDocCommentOwner> void sortIdenticalShortNamedMembers(T[] members, @Nonnull PsiReference context) {
    if (members.length <= 1) {
      return;
    }

    PsiElement leaf = context.getElement().getFirstChild(); // the same proximity weighers are used in completion, where the leafness is critical
    final Comparator<T> comparator = createSortIdenticalNamedMembersComparator(leaf);
    Arrays.sort(members, comparator);
  }

  public static <T extends PsiMember & PsiDocCommentOwner> Comparator<T> createSortIdenticalNamedMembersComparator(PsiElement place) {
    final PsiProximityComparator proximityComparator = new PsiProximityComparator(place);
    return (o1, o2) ->
    {
      boolean deprecated1 = JavaCompletionUtil.isEffectivelyDeprecated(o1);
      boolean deprecated2 = JavaCompletionUtil.isEffectivelyDeprecated(o2);
      if (deprecated1 && !deprecated2) {
        return 1;
      }
      if (!deprecated1 && deprecated2) {
        return -1;
      }
      int compare = proximityComparator.compare(o1, o2);
      if (compare != 0) {
        return compare;
      }

      String qname1 = o1 instanceof PsiClass ? ((PsiClass) o1).getQualifiedName() : null;
      String qname2 = o2 instanceof PsiClass ? ((PsiClass) o2).getQualifiedName() : null;
      if (qname1 == null || qname2 == null) {
        return 0;
      }
      return qname1.compareToIgnoreCase(qname2);
    };
  }

  public static PsiExpression[] findExpressionOccurrences(PsiElement scope, PsiExpression expr) {
    List<PsiExpression> array = new ArrayList<>();
    addExpressionOccurrences(RefactoringUtil.unparenthesizeExpression(expr), array, scope);
    if (expr.isPhysical()) {
      boolean found = false;
      for (PsiExpression psiExpression : array) {
        if (PsiTreeUtil.isAncestor(expr, psiExpression, false) || PsiTreeUtil.isAncestor(psiExpression, expr, false)) {
          found = true;
          break;
        }
      }
      if (!found) {
        array.add(expr);
      }
    }
    return array.toArray(new PsiExpression[array.size()]);
  }

  private static void addExpressionOccurrences(PsiExpression expr, List<PsiExpression> array, PsiElement scope) {
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiExpression) {
        if (JavaPsiEquivalenceUtil.areExpressionsEquivalent(RefactoringUtil.unparenthesizeExpression((PsiExpression) child), expr)) {
          array.add((PsiExpression) child);
          continue;
        }
      }
      addExpressionOccurrences(expr, array, child);
    }
  }

  public static PsiExpression[] findReferenceExpressions(PsiElement scope, PsiElement referee) {
    ArrayList<PsiElement> array = new ArrayList<>();
    if (scope != null) {
      addReferenceExpressions(array, scope, referee);
    }
    return array.toArray(new PsiExpression[array.size()]);
  }

  private static void addReferenceExpressions(ArrayList<PsiElement> array, PsiElement scope, PsiElement referee) {
    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiReferenceExpression) {
        PsiElement ref = ((PsiReferenceExpression) child).resolve();
        if (ref != null && PsiEquivalenceUtil.areElementsEquivalent(ref, referee)) {
          array.add(child);
        }
      }
      addReferenceExpressions(array, child, referee);
    }
  }

  public static Editor positionCursorAtLBrace(final Project project, PsiFile targetFile, @Nonnull PsiClass psiClass) {
    final PsiElement lBrace = psiClass.getLBrace();
    return positionCursor(project, targetFile, lBrace != null ? lBrace : psiClass);
  }

  public static Editor positionCursor(final Project project, PsiFile targetFile, @Nonnull PsiElement element) {
    TextRange range = element.getTextRange();
    LOG.assertTrue(range != null, "element: " + element + "; valid: " + element.isValid());
    int textOffset = range.getStartOffset();

    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, targetFile.getVirtualFile(), textOffset);
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public static boolean preparePsiElementsForWrite(@Nonnull PsiElement... elements) {
    return FileModificationService.getInstance().preparePsiElementsForWrite(Arrays.asList(elements));
  }

  public static void processSubTypes(PsiType psiType, final PsiElement context, boolean getRawSubtypes, @Nonnull final PrefixMatcher matcher, Consumer<PsiType> consumer) {
    int arrayDim = psiType.getArrayDimensions();

    psiType = psiType.getDeepComponentType();
    if (!(psiType instanceof PsiClassType)) {
      return;
    }

    PsiClassType baseType = JavaCompletionUtil.originalize((PsiClassType) psiType);
    PsiClassType.ClassResolveResult baseResult = baseType.resolveGenerics();
    PsiClass baseClass = baseResult.getElement();
    PsiSubstitutor baseSubstitutor = baseResult.getSubstitutor();
    if (baseClass == null) {
      return;
    }

    GlobalSearchScope scope = context.getResolveScope();

    Processor<PsiClass> inheritorsProcessor = createInheritorsProcessor(context, baseType, arrayDim, getRawSubtypes, consumer, baseClass, baseSubstitutor);

    addContextTypeArguments(context, baseType, inheritorsProcessor);

    if (baseClass.hasModifierProperty(PsiModifier.FINAL)) {
      return;
    }

    if (matcher.getPrefix().length() > 2) {
      JBTreeTraverser<PsiClass> traverser = new JBTreeTraverser<>(c -> Arrays.asList(c.getInnerClasses()));
      AllClassesGetter.processJavaClasses(matcher, context.getProject(), scope, psiClass ->
      {
        Iterable<PsiClass> inheritors = traverser.withRoot(psiClass).filter(c -> c.isInheritor(baseClass, true));
        return ContainerUtil.process(inheritors, inheritorsProcessor);
      });
    } else {
      Query<PsiClass> baseQuery = ClassInheritorsSearch.search(baseClass, scope, true, true, false);
      Query<PsiClass> query = new FilteredQuery<>(baseQuery, psiClass -> !(psiClass instanceof PsiTypeParameter) && ContainerUtil.exists(JavaCompletionUtil.getAllLookupStrings(psiClass),
          matcher::prefixMatches));
      query.forEach(inheritorsProcessor);
    }
  }

  private static void addContextTypeArguments(PsiElement context, PsiClassType baseType, Processor<PsiClass> inheritorsProcessor) {
    Set<String> usedNames = ContainerUtil.newHashSet();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    PsiElement each = context;
    while (true) {
      PsiTypeParameterListOwner typed = PsiTreeUtil.getParentOfType(each, PsiTypeParameterListOwner.class);
      if (typed == null) {
        break;
      }
      for (PsiTypeParameter parameter : typed.getTypeParameters()) {
        if (baseType.isAssignableFrom(factory.createType(parameter)) && usedNames.add(parameter.getName())) {
          inheritorsProcessor.process(CompletionUtil.getOriginalOrSelf(parameter));
        }
      }

      each = typed;
    }
  }

  public static Processor<PsiClass> createInheritorsProcessor(PsiElement context,
                                                              PsiClassType baseType,
                                                              int arrayDim,
                                                              boolean getRawSubtypes,
                                                              Consumer<PsiType> result,
                                                              @Nonnull PsiClass baseClass,
                                                              PsiSubstitutor baseSubstitutor) {
    PsiManager manager = context.getManager();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    PsiResolveHelper resolveHelper = facade.getResolveHelper();

    return inheritor ->
    {
      ProgressManager.checkCanceled();

      if (!facade.getResolveHelper().isAccessible(inheritor, context, null)) {
        return true;
      }

      if (inheritor.getQualifiedName() == null && !manager.areElementsEquivalent(inheritor.getContainingFile(), context.getContainingFile().getOriginalFile())) {
        return true;
      }

      if (JavaCompletionUtil.isInExcludedPackage(inheritor, false)) {
        return true;
      }

      PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(baseClass, inheritor, PsiSubstitutor.EMPTY);
      if (superSubstitutor == null) {
        return true;
      }
      if (getRawSubtypes) {
        result.consume(createType(inheritor, facade.getElementFactory().createRawSubstitutor(inheritor), arrayDim));
        return true;
      }

      PsiSubstitutor inheritorSubstitutor = PsiSubstitutor.EMPTY;
      for (PsiTypeParameter inheritorParameter : PsiUtil.typeParametersIterable(inheritor)) {
        for (PsiTypeParameter baseParameter : PsiUtil.typeParametersIterable(baseClass)) {
          final PsiType substituted = superSubstitutor.substitute(baseParameter);
          PsiType arg = baseSubstitutor.substitute(baseParameter);
          if (arg instanceof PsiWildcardType) {
            PsiType bound = ((PsiWildcardType) arg).getBound();
            arg = bound != null ? bound : ((PsiWildcardType) arg).getExtendsBound();
          }
          PsiType substitution = resolveHelper.getSubstitutionForTypeParameter(inheritorParameter, substituted, arg, true, PsiUtil.getLanguageLevel(context));
          if (PsiType.NULL.equals(substitution) || substitution != null && substitution.equalsToText(JavaClassNames.JAVA_LANG_OBJECT) || substitution instanceof PsiWildcardType) {
            continue;
          }
          if (substitution == null) {
            result.consume(createType(inheritor, facade.getElementFactory().createRawSubstitutor(inheritor), arrayDim));
            return true;
          }
          inheritorSubstitutor = inheritorSubstitutor.put(inheritorParameter, substitution);
          break;
        }
      }

      PsiType toAdd = createType(inheritor, inheritorSubstitutor, arrayDim);
      if (baseType.isAssignableFrom(toAdd)) {
        result.consume(toAdd);
      }
      return true;
    };
  }

  private static PsiType createType(PsiClass cls, PsiSubstitutor currentSubstitutor, int arrayDim) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(cls.getProject()).getElementFactory();
    PsiType newType = elementFactory.createType(cls, currentSubstitutor);
    for (int i = 0; i < arrayDim; i++) {
      newType = newType.createArrayType();
    }
    return newType;
  }
}
