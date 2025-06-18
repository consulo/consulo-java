package com.intellij.java.coverage;

import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.coverage.CoverageDataManager;
import consulo.execution.coverage.CoverageSuitesBundle;
import consulo.execution.coverage.view.AbstractCoverageProjectViewNodeDecorator;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.project.Project;
import consulo.project.ui.view.tree.PackageElement;
import consulo.project.ui.view.tree.ProjectViewNode;
import consulo.ui.ex.tree.PresentationData;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

/**
 * @author yole
 */
@ExtensionImpl
public class CoverageProjectViewClassNodeDecorator extends AbstractCoverageProjectViewNodeDecorator {
    @Inject
    public CoverageProjectViewClassNodeDecorator(CoverageDataManager coverageDataManager) {
        super(coverageDataManager);
    }

//    @Override
//    public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {
//        PsiElement element = node.getPsiElement();
//        if (element == null || !element.isValid()) {
//            return;
//        }
//
//        CoverageDataManager dataManager = getCoverageDataManager();
//        CoverageSuitesBundle currentSuite = dataManager.getCurrentSuitesBundle();
//        Project project = element.getProject();
//
//        JavaCoverageAnnotator javaCovAnnotator = getCovAnnotator(currentSuite, project);
//        // This decorator is applicable only to JavaCoverageAnnotator
//        if (javaCovAnnotator == null) {
//            return;
//        }
//
//        if (element instanceof PsiClass psiClass) {
//            String qName = psiClass.getQualifiedName();
//            if (qName != null) {
//                appendCoverageInfo(cellRenderer, javaCovAnnotator.getClassCoverageInformationString(qName, dataManager));
//            }
//        }
//    }

    @Override
    @RequiredReadAction
    public void decorate(ProjectViewNode node, PresentationData data) {
        CoverageDataManager coverageDataManager = getCoverageDataManager();
        CoverageSuitesBundle currentSuite = coverageDataManager.getCurrentSuitesBundle();

        Project project = node.getProject();
        JavaCoverageAnnotator javaCovAnnotator = getCovAnnotator(currentSuite, project);
        // This decorator is applicable only to JavaCoverageAnnotator
        if (javaCovAnnotator == null) {
            return;
        }

        Object value = node.getValue();
        PsiElement element = null;
        if (value instanceof PsiElement psiElement) {
            element = psiElement;
        }
        else if (value instanceof SmartPsiElementPointer smartPsiElementPointer) {
            element = smartPsiElementPointer.getElement();
        }
        else if (value instanceof PackageElement packageElement) {
            String coverageString = javaCovAnnotator.getPackageCoverageInformationString(
                packageElement.getPackage(),
                packageElement.getModule(),
                coverageDataManager
            );
            data.setLocationString(coverageString);
        }

        if (element instanceof PsiClass psiClass) {
            VirtualFile vFile = PsiUtilCore.getVirtualFile(element);
            if (vFile != null && currentSuite.getSearchScope(project).contains(vFile)) {
                String qName = psiClass.getQualifiedName();
                if (qName != null) {
                    data.setLocationString(javaCovAnnotator.getClassCoverageInformationString(qName, coverageDataManager));
                }
            }
        }
    }

    @Nullable
    private static JavaCoverageAnnotator getCovAnnotator(CoverageSuitesBundle currentSuite, Project project) {
        if (currentSuite != null && currentSuite.getAnnotator(project) instanceof JavaCoverageAnnotator javaCoverageAnnotator) {
            return javaCoverageAnnotator;
        }
        return null;
    }
}