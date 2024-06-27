package com.intellij.java.impl.codeInspection.inheritance;

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.impl.codeInspection.inheritance.search.InheritorsStatisticalDataSearch;
import com.intellij.java.impl.codeInspection.inheritance.search.InheritorsStatisticsSearchResult;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionManager;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
@ExtensionImpl
public class SuperClassHasFrequentlyUsedInheritorsInspection extends BaseJavaLocalInspectionTool {
  private final static int MIN_PERCENT_RATIO = 5;
  public final static int MAX_QUICK_FIX_COUNTS = 4;

  @Nls
  @Nonnull
  @Override
  public String getGroupDisplayName() {
    return InspectionLocalize.groupNamesInheritanceIssues().get();
  }

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return "Class may extend a commonly used base class instead of implementing interface or extending abstract class";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  @Override
  public ProblemDescriptor[] checkClass(@Nonnull final PsiClass aClass,
                                        @Nonnull final InspectionManager manager,
                                        final boolean isOnTheFly, Object state) {
    if (aClass.isInterface() ||
        aClass instanceof PsiTypeParameter ||
        aClass.getMethods().length != 0 ||
        aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return null;
    }

    final PsiClass superClass = getSuperIfUnique(aClass);
    if (superClass == null) return null;

    final List<InheritorsStatisticsSearchResult> topInheritors =
      InheritorsStatisticalDataSearch.search(superClass, aClass, aClass.getResolveScope(), MIN_PERCENT_RATIO);

    if (topInheritors.isEmpty()) {
      return null;
    }

    final Collection<LocalQuickFix> topInheritorsQuickFix = new ArrayList<LocalQuickFix>(topInheritors.size());

    boolean isFirst = true;
    for (final InheritorsStatisticsSearchResult searchResult : topInheritors) {
      final LocalQuickFix quickFix;
      if (isFirst) {
        quickFix = new ChangeSuperClassFix(searchResult.getPsiClass(), searchResult.getPercent(), superClass);
        isFirst = false;
      } else {
        quickFix = new ChangeSuperClassFix.LowPriority(searchResult.getPsiClass(), searchResult.getPercent(), superClass);
      }
      topInheritorsQuickFix.add(quickFix);
      if (topInheritorsQuickFix.size() >= MAX_QUICK_FIX_COUNTS) {
        break;
      }
    }
    return new ProblemDescriptor[]{manager
      .createProblemDescriptor(aClass, "Class may extend a commonly used base class instead of implementing interface or extending abstract class", false,
                               topInheritorsQuickFix.toArray(new LocalQuickFix[topInheritorsQuickFix.size()]),
                               ProblemHighlightType.INFORMATION)};
  }

  @Nullable
  private static PsiClass getSuperIfUnique(final @Nonnull PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return (PsiClass)((PsiAnonymousClass)aClass).getBaseClassReference().resolve();
    }
    final PsiReferenceList extendsList = aClass.getExtendsList();
    if (extendsList != null) {
      final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
      if (referenceElements.length == 1) {
        PsiClass returnClass = (PsiClass)referenceElements[0].resolve();
        if (returnClass != null &&
            !JavaClassNames.JAVA_LANG_OBJECT.equals(returnClass.getQualifiedName()) &&
            !returnClass.isInterface()) {
          return returnClass;
        }
      }
    }

    final PsiReferenceList implementsList = aClass.getImplementsList();
    if (implementsList != null) {
      final PsiJavaCodeReferenceElement[] referenceElements = implementsList.getReferenceElements();
      if (referenceElements.length == 1) {
        PsiClass returnClass = (PsiClass)referenceElements[0].resolve();
        if (returnClass != null && returnClass.isInterface()) {
          return returnClass;
        }
      }
    }
    return null;
  }
}
