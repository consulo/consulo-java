package com.intellij.coverage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import consulo.psi.PsiPackage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Roman.Chernyatchik
 */
@Singleton
public class JavaCoverageAnnotator extends BaseCoverageAnnotator {
  private final Map<String, PackageAnnotator.PackageCoverageInfo> myPackageCoverageInfos = new HashMap<String, PackageAnnotator.PackageCoverageInfo>();
  private final Map<String, PackageAnnotator.PackageCoverageInfo> myFlattenPackageCoverageInfos = new HashMap<String, PackageAnnotator.PackageCoverageInfo>();
  private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myDirCoverageInfos =
    new HashMap<VirtualFile, PackageAnnotator.PackageCoverageInfo>();
  private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myTestDirCoverageInfos =
    new HashMap<VirtualFile, PackageAnnotator.PackageCoverageInfo>();
  private final Map<String, PackageAnnotator.ClassCoverageInfo> myClassCoverageInfos = new HashMap<String, PackageAnnotator.ClassCoverageInfo>();

  @Inject
  public JavaCoverageAnnotator(final Project project) {
    super(project);
  }

  public static JavaCoverageAnnotator getInstance(final Project project) {
    return ServiceManager.getService(project, JavaCoverageAnnotator.class);
  }

  @Nullable
  public String getDirCoverageInformationString(@Nonnull final PsiDirectory directory,
                                                @Nonnull final CoverageSuitesBundle currentSuite,
                                                @Nonnull final CoverageDataManager coverageDataManager) {
    final PsiJavaPackage psiPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (psiPackage == null) return null;

    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(directory.getProject()).getFileIndex();
    final VirtualFile virtualFile = directory.getVirtualFile();

    final boolean isInTestContent = projectFileIndex.isInTestSourceContent(virtualFile);

    if (!currentSuite.isTrackTestFolders() && isInTestContent) {
      return null;
    }
    return isInTestContent ? getCoverageInformationString(myTestDirCoverageInfos.get(virtualFile), coverageDataManager.isSubCoverageActive())
                           : getCoverageInformationString(myDirCoverageInfos.get(virtualFile), coverageDataManager.isSubCoverageActive());

  }

  @Nullable
  public String getFileCoverageInformationString(@Nonnull PsiFile file, @Nonnull CoverageSuitesBundle currentSuite, @Nonnull CoverageDataManager manager) {
    // N/A here we work with java classes
    return null;
  }

  public void onSuiteChosen(CoverageSuitesBundle newSuite) {
    super.onSuiteChosen(newSuite);

    myPackageCoverageInfos.clear();
    myFlattenPackageCoverageInfos.clear();
    myDirCoverageInfos.clear();
    myTestDirCoverageInfos.clear();
    myClassCoverageInfos.clear();
  }

  protected Runnable createRenewRequest(@Nonnull final CoverageSuitesBundle suite, @Nonnull final CoverageDataManager dataManager) {


    final Project project = getProject();
    final List<PsiJavaPackage> packages = new ArrayList<PsiJavaPackage>();
    final List<PsiClass> classes = new ArrayList<PsiClass>();

    for (CoverageSuite coverageSuite : suite.getSuites()) {
      final JavaCoverageSuite javaSuite = (JavaCoverageSuite)coverageSuite;
      classes.addAll(javaSuite.getCurrentSuiteClasses(project));
      packages.addAll(javaSuite.getCurrentSuitePackages(project));
    }

    if (packages.isEmpty() && classes.isEmpty()) {
      return null;
    }

    return new Runnable() {
      public void run() {
        final PackageAnnotator.Annotator annotator = new PackageAnnotator.Annotator() {
          public void annotatePackage(String packageQualifiedName, PackageAnnotator.PackageCoverageInfo packageCoverageInfo) {
            myPackageCoverageInfos.put(packageQualifiedName, packageCoverageInfo);
          }

          public void annotatePackage(String packageQualifiedName,
                                      PackageAnnotator.PackageCoverageInfo packageCoverageInfo,
                                      boolean flatten) {
            if (flatten) {
              myFlattenPackageCoverageInfos.put(packageQualifiedName, packageCoverageInfo);
            }
            else {
              annotatePackage(packageQualifiedName, packageCoverageInfo);
            }
          }

          public void annotateSourceDirectory(VirtualFile dir,
                                              PackageAnnotator.PackageCoverageInfo dirCoverageInfo,
                                              Module module) {
            myDirCoverageInfos.put(dir, dirCoverageInfo);
          }

          public void annotateTestDirectory(VirtualFile virtualFile,
                                            PackageAnnotator.PackageCoverageInfo packageCoverageInfo,
                                            Module module) {
            myTestDirCoverageInfos.put(virtualFile, packageCoverageInfo);
          }

          public void annotateClass(String classQualifiedName, PackageAnnotator.ClassCoverageInfo classCoverageInfo) {
            myClassCoverageInfos.put(classQualifiedName, classCoverageInfo);
          }
        };
        for (PsiJavaPackage aPackage : packages) {
          new PackageAnnotator(aPackage).annotate(suite, annotator);
        }
        for (final PsiClass aClass : classes) {
          Runnable runnable = new Runnable() {
            public void run() {
              final String packageName = ((PsiClassOwner)aClass.getContainingFile()).getPackageName();
              final PsiJavaPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
              if (psiPackage == null) return;
              new PackageAnnotator(psiPackage).annotateFilteredClass(aClass, suite, annotator);
            }
          };
          ApplicationManager.getApplication().runReadAction(runnable);
        }
        dataManager.triggerPresentationUpdate();
      }
    };
  }

  @Nullable
  public static String getCoverageInformationString(PackageAnnotator.PackageCoverageInfo info, boolean subCoverageActive) {
    if (info == null) return null;
    if (info.totalClassCount == 0 || info.totalLineCount == 0) return null;
    if (subCoverageActive) {
      return info.coveredClassCount + info.coveredLineCount > 0 ? "covered" : null;
    }
    return (int)((double)info.coveredClassCount / info.totalClassCount * 100) +  "% classes, " +
           (int)((double)info.coveredLineCount / info.totalLineCount * 100) + "% lines covered";
  }

  /**
   *
   * @param psiPackage qualified name of a package to obtain coverage information for
   * @param module optional parameter to restrict coverage to source directories of a certain module
   * @param coverageDataManager
   * @return human-readable coverage information
   */
  @Nullable
  public String getPackageCoverageInformationString(final PsiPackage psiPackage,
                                                    @Nullable final Module module,
                                                    @Nonnull final CoverageDataManager coverageDataManager) {
    return getPackageCoverageInformationString(psiPackage, module, coverageDataManager, false);
  }

  /**
   *
   *
   * @param psiPackage qualified name of a package to obtain coverage information for
   * @param module optional parameter to restrict coverage to source directories of a certain module
   * @param coverageDataManager
   * @param flatten
   * @return human-readable coverage information
   */
  @javax.annotation.Nullable
  public String getPackageCoverageInformationString(final PsiPackage psiPackage,
                                                    @Nullable final Module module,
                                                    @Nonnull final CoverageDataManager coverageDataManager,
                                                    boolean flatten) {
    if (psiPackage == null) return null;
    final boolean subCoverageActive = coverageDataManager.isSubCoverageActive();
    PackageAnnotator.PackageCoverageInfo info;
    if (module != null) {
      final PsiDirectory[] directories = psiPackage.getDirectories(GlobalSearchScope.moduleScope(module));
      PackageAnnotator.PackageCoverageInfo result = null;
      for (PsiDirectory directory : directories) {
        final VirtualFile virtualFile = directory.getVirtualFile();
        result = merge(result, myDirCoverageInfos.get(virtualFile));
        result = merge(result, myTestDirCoverageInfos.get(virtualFile));
      }
      return getCoverageInformationString(result, subCoverageActive);
    }
    else {
      info = getPackageCoverageInfo(psiPackage, flatten);
    }
    return getCoverageInformationString(info, subCoverageActive);
  }

  public PackageAnnotator.PackageCoverageInfo getPackageCoverageInfo(@Nonnull PsiPackage psiPackage, boolean flattenPackages) {
    final String qualifiedName = psiPackage.getQualifiedName();
    return flattenPackages ? myFlattenPackageCoverageInfos.get(qualifiedName) : myPackageCoverageInfos.get(qualifiedName);
  }
  
  public String getPackageClassPercentage(@Nonnull final PsiPackage psiPackage, boolean flatten) {
    final PackageAnnotator.PackageCoverageInfo packageCoverageInfo = getPackageCoverageInfo(psiPackage, flatten);
    if (packageCoverageInfo == null) return null;
    return getPercentage(packageCoverageInfo.coveredClassCount, packageCoverageInfo.totalClassCount);
  }
  
  public String getPackageMethodPercentage(PsiPackage psiPackage, boolean flatten) {
    final PackageAnnotator.PackageCoverageInfo packageCoverageInfo = getPackageCoverageInfo(psiPackage, flatten);
    if (packageCoverageInfo == null) return null;
    return getPercentage(packageCoverageInfo.coveredMethodCount, packageCoverageInfo.totalMethodCount);
  }

  public String getPackageLinePercentage(final PsiPackage psiPackage, boolean flatten) {
    final PackageAnnotator.PackageCoverageInfo packageCoverageInfo = getPackageCoverageInfo(psiPackage, flatten);
    if (packageCoverageInfo == null) return null;
    return getPercentage(packageCoverageInfo.coveredLineCount, packageCoverageInfo.totalLineCount);
  }

  public String getClassLinePercentage(String classFQName) {
    final PackageAnnotator.ClassCoverageInfo info = myClassCoverageInfos.get(classFQName);
    if (info == null) return null;
    final int coveredLines = info.fullyCoveredLineCount + info.partiallyCoveredLineCount;
    return getPercentage(coveredLines, info.totalLineCount);
  }
  
  public String getClassMethodPercentage(String classFQName) {
    final PackageAnnotator.ClassCoverageInfo info = myClassCoverageInfos.get(classFQName);
    if (info == null) return null;
    return getPercentage(info.coveredMethodCount, info.totalMethodCount);
  }

  public String getClassCoveredPercentage(String classFQName) {
    final PackageAnnotator.ClassCoverageInfo info = myClassCoverageInfos.get(classFQName);
    if (info == null) return null;
    return getPercentage(info.coveredClassCount, info.totalClassCount);
  }

  private static String getPercentage(int covered, int total) {
    return (int)((double)covered /total * 100) +"% (" + covered + "/" + total + ")";
  }

  public static PackageAnnotator.PackageCoverageInfo merge(final PackageAnnotator.PackageCoverageInfo info,
                                                           final PackageAnnotator.PackageCoverageInfo testInfo) {
    if (info == null) return testInfo;
    if (testInfo == null) return info;
    final PackageAnnotator.PackageCoverageInfo coverageInfo = new PackageAnnotator.PackageCoverageInfo();
    coverageInfo.totalClassCount = info.totalClassCount + testInfo.totalClassCount;
    coverageInfo.coveredClassCount = info.coveredClassCount + testInfo.coveredClassCount;

    coverageInfo.totalLineCount = info.totalLineCount + testInfo.totalLineCount;
    coverageInfo.coveredLineCount = info.coveredLineCount + testInfo.coveredLineCount;
    return coverageInfo;
  }

  /**
   * @param classFQName to obtain coverage information for
   * @return human-readable coverage information
   */
  @Nullable
  public String getClassCoverageInformationString(String classFQName, CoverageDataManager coverageDataManager) {
    final PackageAnnotator.ClassCoverageInfo info = myClassCoverageInfos.get(classFQName);
    if (info == null) return null;
    if (info.totalMethodCount == 0 || info.totalLineCount == 0) return null;
    if (coverageDataManager.isSubCoverageActive()){
      return info.coveredMethodCount + info.fullyCoveredLineCount + info.partiallyCoveredLineCount > 0 ? "covered" : null;
    }
    return (int)((double)info.coveredMethodCount / info.totalMethodCount * 100) +  "% methods, " +
           (int)((double)(info.fullyCoveredLineCount + info.partiallyCoveredLineCount) / info.totalLineCount * 100) + "% lines covered";
  }

  @Nullable
  public PackageAnnotator.ClassCoverageInfo getClassCoverageInfo(String classFQName) {
    return myClassCoverageInfos.get(classFQName);
  }
}
