package com.intellij.java.coverage;

import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.coverage.CoverageAnnotator;
import consulo.execution.coverage.CoverageDataManager;
import consulo.execution.coverage.CoverageSuitesBundle;
import consulo.execution.coverage.view.AbstractCoverageProjectViewNodeDecorator;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import consulo.project.ui.view.tree.PackageElement;
import consulo.project.ui.view.tree.ProjectViewNode;
import consulo.ui.ex.tree.PresentationData;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;

import javax.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class CoverageProjectViewClassNodeDecorator extends AbstractCoverageProjectViewNodeDecorator {
  @Inject
  public CoverageProjectViewClassNodeDecorator(final CoverageDataManager coverageDataManager) {
    super(coverageDataManager);
  }

//  @Override
//  public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {
//    final PsiElement element = node.getPsiElement();
//    if (element == null || !element.isValid()) {
//      return;
//    }
//
//    final CoverageDataManager dataManager = getCoverageDataManager();
//    final CoverageSuitesBundle currentSuite = dataManager.getCurrentSuitesBundle();
//    final Project project = element.getProject();
//
//    final JavaCoverageAnnotator javaCovAnnotator = getCovAnnotator(currentSuite, project);
//    // This decorator is applicable only to JavaCoverageAnnotator
//    if (javaCovAnnotator == null) {
//      return;
//    }
//
//    if (element instanceof PsiClass) {
//      final String qName = ((PsiClass) element).getQualifiedName();
//      if (qName != null) {
//        appendCoverageInfo(cellRenderer, javaCovAnnotator.getClassCoverageInformationString(qName, dataManager));
//      }
//    }
//  }

  @Override
  public void decorate(ProjectViewNode node, PresentationData data) {
    final CoverageDataManager coverageDataManager = getCoverageDataManager();
    final CoverageSuitesBundle currentSuite = coverageDataManager.getCurrentSuitesBundle();

    final Project project = node.getProject();
    final JavaCoverageAnnotator javaCovAnnotator = getCovAnnotator(currentSuite, project);
    // This decorator is applicable only to JavaCoverageAnnotator
    if (javaCovAnnotator == null) {
      return;
    }

    final Object value = node.getValue();
    PsiElement element = null;
    if (value instanceof PsiElement) {
      element = (PsiElement) value;
    } else if (value instanceof SmartPsiElementPointer) {
      element = ((SmartPsiElementPointer) value).getElement();
    } else if (value instanceof PackageElement) {
      PackageElement packageElement = (PackageElement) value;
      final String coverageString = javaCovAnnotator.getPackageCoverageInformationString(packageElement.getPackage(),
          packageElement.getModule(),
          coverageDataManager);
      data.setLocationString(coverageString);
    }

    if (element instanceof PsiClass) {
      final GlobalSearchScope searchScope = currentSuite.getSearchScope(project);
      final VirtualFile vFile = PsiUtilCore.getVirtualFile(element);
      if (vFile != null && searchScope.contains(vFile)) {
        final String qName = ((PsiClass) element).getQualifiedName();
        if (qName != null) {
          data.setLocationString(javaCovAnnotator.getClassCoverageInformationString(qName, coverageDataManager));
        }
      }
    }
  }

  @Nullable
  private static JavaCoverageAnnotator getCovAnnotator(final CoverageSuitesBundle currentSuite, Project project) {
    if (currentSuite != null) {
      final CoverageAnnotator coverageAnnotator = currentSuite.getAnnotator(project);
      if (coverageAnnotator instanceof JavaCoverageAnnotator) {
        return (JavaCoverageAnnotator) coverageAnnotator;
      }
    }
    return null;
  }
}