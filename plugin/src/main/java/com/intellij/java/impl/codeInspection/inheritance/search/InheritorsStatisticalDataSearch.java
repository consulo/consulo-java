package com.intellij.java.impl.codeInspection.inheritance.search;

import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.java.indexing.search.searches.DirectClassInheritorsSearch;
import consulo.application.util.function.Processor;
import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class InheritorsStatisticalDataSearch {

  /**
   * search for most used inheritors of superClass in scope
   *
   * @param aClass          - class that excluded from inheritors of superClass
   * @param minPercentRatio - head volume
   * @return - search results in relevant ordering (frequency descent)
   */
  public static List<InheritorsStatisticsSearchResult> search(@Nonnull PsiClass superClass,
                                                              @Nonnull PsiClass aClass,
                                                              @Nonnull GlobalSearchScope scope,
                                                              int minPercentRatio) {
    String superClassName = superClass.getName();
    String aClassName = aClass.getName();
    Set<String> disabledNames = new HashSet<String>();
    disabledNames.add(aClassName);
    disabledNames.add(superClassName);
    Set<InheritorsCountData> collector = new TreeSet<InheritorsCountData>();
    Pair<Integer, Integer> collectingResult = collectInheritorsInfo(superClass, collector, disabledNames);
    int allAnonymousInheritors = collectingResult.getSecond();
    int allInheritors = collectingResult.getFirst() + allAnonymousInheritors - 1;

    List<InheritorsStatisticsSearchResult> result = new ArrayList<InheritorsStatisticsSearchResult>();

    Integer firstPercent = null;
    for (InheritorsCountData data : collector) {
      int inheritorsCount = data.getInheritorsCount();
      if (inheritorsCount < allAnonymousInheritors) {
        break;
      }
      int percent = (inheritorsCount * 100) / allInheritors;
      if (percent < 1) {
        break;
      }
      if (firstPercent == null) {
        firstPercent = percent;
      }
      else if (percent * minPercentRatio < firstPercent) {
        break;
      }

      PsiClass psiClass = data.getPsiClass();
      VirtualFile file = psiClass.getContainingFile().getVirtualFile();
      if (file != null && scope.contains(file)) {
        result.add(new InheritorsStatisticsSearchResult(psiClass, percent));
      }
    }
    return result;
  }

  private static Pair<Integer, Integer> collectInheritorsInfo(PsiClass superClass,
                                                              Set<InheritorsCountData> collector,
                                                              Set<String> disabledNames) {
    return collectInheritorsInfo(superClass, collector, disabledNames, new HashSet<String>(), new HashSet<String>());
  }

  private static Pair<Integer, Integer> collectInheritorsInfo(PsiClass aClass,
                                                              Set<InheritorsCountData> collector,
                                                              Set<String> disabledNames,
                                                              Set<String> processedElements,
                                                              Set<String> allNotAnonymousInheritors) {
    String className = aClass.getName();
    if (!processedElements.add(className)) return Pair.create(0, 0);

    MyInheritorsInfoProcessor processor = new MyInheritorsInfoProcessor(collector, disabledNames, processedElements);
    DirectClassInheritorsSearch.search(aClass).forEach(processor);

    allNotAnonymousInheritors.addAll(processor.getAllNotAnonymousInheritors());

    int allInheritorsCount = processor.getAllNotAnonymousInheritors().size() + processor.getAnonymousInheritorsCount();
    if (!aClass.isInterface() && allInheritorsCount != 0 && !disabledNames.contains(className)) {
      collector.add(new InheritorsCountData(aClass, allInheritorsCount));
    }
    return Pair.create(allNotAnonymousInheritors.size(), processor.getAnonymousInheritorsCount());
  }

  private static class MyInheritorsInfoProcessor implements Processor<PsiClass> {
    private final Set<InheritorsCountData> myCollector;
    private final Set<String> myDisabledNames;
    private final Set<String> myProcessedElements;
    private final Set<String> myAllNotAnonymousInheritors;

    private MyInheritorsInfoProcessor(Set<InheritorsCountData> collector, Set<String> disabledNames, Set<String> processedElements) {
      myCollector = collector;
      myDisabledNames = disabledNames;
      myProcessedElements = processedElements;
      myAllNotAnonymousInheritors = new HashSet<String>();
    }

    private int myAnonymousInheritorsCount = 0;

    private Set<String> getAllNotAnonymousInheritors() {
      return myAllNotAnonymousInheritors;
    }

    private int getAnonymousInheritorsCount() {
      return myAnonymousInheritorsCount;
    }

    @Override
    public boolean process(PsiClass psiClass) {
      String inheritorName = psiClass.getName();
      if (inheritorName == null) {
        myAnonymousInheritorsCount++;
      }
      else {
        Pair<Integer, Integer> res =
          collectInheritorsInfo(psiClass, myCollector, myDisabledNames, myProcessedElements, myAllNotAnonymousInheritors);
        myAnonymousInheritorsCount += res.getSecond();
        if (!psiClass.isInterface()) {
          myAllNotAnonymousInheritors.add(inheritorName);
        }
      }
      return true;
    }
  }
}
