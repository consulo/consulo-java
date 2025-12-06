package com.intellij.java.impl.codeInspection.inheritance;

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.impl.codeInspection.inheritance.search.InheritorsStatisticalDataSearch;
import com.intellij.java.impl.codeInspection.inheritance.search.InheritorsStatisticsSearchResult;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.localize.LocalizeValue;
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

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesInheritanceIssues();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO(
            "Class may extend a commonly used base class instead of implementing interface or extending abstract class"
        );
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nullable
    @Override
    @RequiredReadAction
    public ProblemDescriptor[] checkClass(
        @Nonnull PsiClass aClass,
        @Nonnull InspectionManager manager,
        boolean isOnTheFly,
        Object state
    ) {
        if (aClass.isInterface()
            || aClass instanceof PsiTypeParameter
            || aClass.getMethods().length != 0
            || aClass.isAbstract()) {
            return null;
        }

        PsiClass superClass = getSuperIfUnique(aClass);
        if (superClass == null) {
            return null;
        }

        List<InheritorsStatisticsSearchResult> topInheritors =
            InheritorsStatisticalDataSearch.search(superClass, aClass, aClass.getResolveScope(), MIN_PERCENT_RATIO);

        if (topInheritors.isEmpty()) {
            return null;
        }

        Collection<LocalQuickFix> topInheritorsQuickFix = new ArrayList<>(topInheritors.size());

        boolean isFirst = true;
        for (InheritorsStatisticsSearchResult searchResult : topInheritors) {
            LocalQuickFix quickFix;
            if (isFirst) {
                quickFix = new ChangeSuperClassFix(searchResult.getPsiClass(), searchResult.getPercent(), superClass);
                isFirst = false;
            }
            else {
                quickFix = new ChangeSuperClassFix.LowPriority(searchResult.getPsiClass(), searchResult.getPercent(), superClass);
            }
            topInheritorsQuickFix.add(quickFix);
            if (topInheritorsQuickFix.size() >= MAX_QUICK_FIX_COUNTS) {
                break;
            }
        }
        return new ProblemDescriptor[]{
            manager.newProblemDescriptor(LocalizeValue.localizeTODO(
                    "Class may extend a commonly used base class instead of implementing interface or extending abstract class"
                ))
                .range(aClass)
                .highlightType(ProblemHighlightType.INFORMATION)
                .withFixes(topInheritorsQuickFix)
                .create()
        };
    }

    @Nullable
    @RequiredReadAction
    private static PsiClass getSuperIfUnique(@Nonnull PsiClass aClass) {
        if (aClass instanceof PsiAnonymousClass anonymousClass) {
            return (PsiClass) anonymousClass.getBaseClassReference().resolve();
        }
        PsiReferenceList extendsList = aClass.getExtendsList();
        if (extendsList != null) {
            PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
            if (referenceElements.length == 1) {
                PsiClass returnClass = (PsiClass) referenceElements[0].resolve();
                if (returnClass != null
                    && !CommonClassNames.JAVA_LANG_OBJECT.equals(returnClass.getQualifiedName())
                    && !returnClass.isInterface()) {
                    return returnClass;
                }
            }
        }

        PsiReferenceList implementsList = aClass.getImplementsList();
        if (implementsList != null) {
            PsiJavaCodeReferenceElement[] referenceElements = implementsList.getReferenceElements();
            if (referenceElements.length == 1) {
                PsiClass returnClass = (PsiClass) referenceElements[0].resolve();
                if (returnClass != null && returnClass.isInterface()) {
                    return returnClass;
                }
            }
        }
        return null;
    }
}
