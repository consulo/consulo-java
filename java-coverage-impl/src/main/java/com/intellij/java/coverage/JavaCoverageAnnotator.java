package com.intellij.java.coverage;

import com.intellij.java.language.psi.*;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.execution.coverage.BaseCoverageAnnotator;
import consulo.execution.coverage.CoverageDataManager;
import consulo.execution.coverage.CoverageSuite;
import consulo.execution.coverage.CoverageSuitesBundle;
import consulo.ide.ServiceManager;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Roman.Chernyatchik
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class JavaCoverageAnnotator extends BaseCoverageAnnotator {
    private final Map<String, PackageAnnotator.PackageCoverageInfo> myPackageCoverageInfos = new HashMap<>();
    private final Map<String, PackageAnnotator.PackageCoverageInfo> myFlattenPackageCoverageInfos = new HashMap<>();
    private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myDirCoverageInfos = new HashMap<>();
    private final Map<VirtualFile, PackageAnnotator.PackageCoverageInfo> myTestDirCoverageInfos = new HashMap<>();
    private final Map<String, PackageAnnotator.ClassCoverageInfo> myClassCoverageInfos = new HashMap<>();

    @Inject
    public JavaCoverageAnnotator(Project project) {
        super(project);
    }

    public static JavaCoverageAnnotator getInstance(Project project) {
        return ServiceManager.getService(project, JavaCoverageAnnotator.class);
    }

    @Nullable
    @Override
    public String getDirCoverageInformationString(
        @Nonnull PsiDirectory directory,
        @Nonnull CoverageSuitesBundle currentSuite,
        @Nonnull CoverageDataManager coverageDataManager
    ) {
        PsiJavaPackage psiPackage = JavaDirectoryService.getInstance().getPackage(directory);
        if (psiPackage == null) {
            return null;
        }

        ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(directory.getProject()).getFileIndex();
        VirtualFile virtualFile = directory.getVirtualFile();

        boolean isInTestContent = projectFileIndex.isInTestSourceContent(virtualFile);

        if (!currentSuite.isTrackTestFolders() && isInTestContent) {
            return null;
        }
        return isInTestContent
            ? getCoverageInformationString(myTestDirCoverageInfos.get(virtualFile), coverageDataManager.isSubCoverageActive())
            : getCoverageInformationString(myDirCoverageInfos.get(virtualFile), coverageDataManager.isSubCoverageActive());
    }

    @Nullable
    @Override
    public String getFileCoverageInformationString(
        @Nonnull PsiFile file,
        @Nonnull CoverageSuitesBundle currentSuite,
        @Nonnull CoverageDataManager manager
    ) {
        // N/A here we work with java classes
        return null;
    }

    @Override
    public void onSuiteChosen(CoverageSuitesBundle newSuite) {
        super.onSuiteChosen(newSuite);

        myPackageCoverageInfos.clear();
        myFlattenPackageCoverageInfos.clear();
        myDirCoverageInfos.clear();
        myTestDirCoverageInfos.clear();
        myClassCoverageInfos.clear();
    }

    @Override
    protected Runnable createRenewRequest(@Nonnull CoverageSuitesBundle suite, @Nonnull CoverageDataManager dataManager) {
        Project project = getProject();
        List<PsiJavaPackage> packages = new ArrayList<>();
        List<PsiClass> classes = new ArrayList<>();

        for (CoverageSuite coverageSuite : suite.getSuites()) {
            JavaCoverageSuite javaSuite = (JavaCoverageSuite) coverageSuite;
            classes.addAll(javaSuite.getCurrentSuiteClasses(project));
            packages.addAll(javaSuite.getCurrentSuitePackages(project));
        }

        if (packages.isEmpty() && classes.isEmpty()) {
            return null;
        }

        return () -> {
            PackageAnnotator.Annotator annotator = new PackageAnnotator.Annotator() {
                @Override
                public void annotatePackage(String packageQualifiedName, PackageAnnotator.PackageCoverageInfo packageCoverageInfo) {
                    myPackageCoverageInfos.put(packageQualifiedName, packageCoverageInfo);
                }

                @Override
                public void annotatePackage(
                    String packageQualifiedName,
                    PackageAnnotator.PackageCoverageInfo packageCoverageInfo,
                    boolean flatten
                ) {
                    if (flatten) {
                        myFlattenPackageCoverageInfos.put(packageQualifiedName, packageCoverageInfo);
                    }
                    else {
                        annotatePackage(packageQualifiedName, packageCoverageInfo);
                    }
                }

                @Override
                public void annotateSourceDirectory(
                    VirtualFile dir,
                    PackageAnnotator.PackageCoverageInfo dirCoverageInfo,
                    Module module
                ) {
                    myDirCoverageInfos.put(dir, dirCoverageInfo);
                }

                @Override
                public void annotateTestDirectory(
                    VirtualFile virtualFile,
                    PackageAnnotator.PackageCoverageInfo packageCoverageInfo,
                    Module module
                ) {
                    myTestDirCoverageInfos.put(virtualFile, packageCoverageInfo);
                }

                @Override
                public void annotateClass(String classQualifiedName, PackageAnnotator.ClassCoverageInfo classCoverageInfo) {
                    myClassCoverageInfos.put(classQualifiedName, classCoverageInfo);
                }
            };
            for (PsiJavaPackage aPackage : packages) {
                new PackageAnnotator(aPackage).annotate(suite, annotator);
            }
            for (PsiClass aClass : classes) {
                Runnable runnable = () -> {
                    String packageName = ((PsiClassOwner) aClass.getContainingFile()).getPackageName();
                    PsiJavaPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
                    if (psiPackage == null) {
                        return;
                    }
                    new PackageAnnotator(psiPackage).annotateFilteredClass(aClass, suite, annotator);
                };
                aClass.getApplication().runReadAction(runnable);
            }
            dataManager.triggerPresentationUpdate();
        };
    }

    @Nullable
    public static String getCoverageInformationString(PackageAnnotator.PackageCoverageInfo info, boolean subCoverageActive) {
        if (info == null) {
            return null;
        }
        if (info.totalClassCount == 0 || info.totalLineCount == 0) {
            return null;
        }
        if (subCoverageActive) {
            return info.coveredClassCount + info.coveredLineCount > 0 ? "covered" : null;
        }
        return (int) ((double) info.coveredClassCount / info.totalClassCount * 100) + "% classes, " +
            (int) ((double) info.coveredLineCount / info.totalLineCount * 100) + "% lines covered";
    }

    /**
     * @param psiPackage          qualified name of a package to obtain coverage information for
     * @param module              optional parameter to restrict coverage to source directories of a certain module
     * @param coverageDataManager
     * @return human-readable coverage information
     */
    @Nullable
    public String getPackageCoverageInformationString(
        PsiPackage psiPackage,
        @Nullable Module module,
        @Nonnull CoverageDataManager coverageDataManager
    ) {
        return getPackageCoverageInformationString(psiPackage, module, coverageDataManager, false);
    }

    /**
     * @param psiPackage          qualified name of a package to obtain coverage information for
     * @param module              optional parameter to restrict coverage to source directories of a certain module
     * @param coverageDataManager
     * @param flatten
     * @return human-readable coverage information
     */
    @Nullable
    public String getPackageCoverageInformationString(
        PsiPackage psiPackage,
        @Nullable Module module,
        @Nonnull CoverageDataManager coverageDataManager,
        boolean flatten
    ) {
        if (psiPackage == null) {
            return null;
        }
        boolean subCoverageActive = coverageDataManager.isSubCoverageActive();
        PackageAnnotator.PackageCoverageInfo info;
        if (module != null) {
            PsiDirectory[] directories = psiPackage.getDirectories(GlobalSearchScope.moduleScope(module));
            PackageAnnotator.PackageCoverageInfo result = null;
            for (PsiDirectory directory : directories) {
                VirtualFile virtualFile = directory.getVirtualFile();
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
        String qualifiedName = psiPackage.getQualifiedName();
        return flattenPackages ? myFlattenPackageCoverageInfos.get(qualifiedName) : myPackageCoverageInfos.get(qualifiedName);
    }

    public String getPackageClassPercentage(@Nonnull PsiPackage psiPackage, boolean flatten) {
        PackageAnnotator.PackageCoverageInfo packageCoverageInfo = getPackageCoverageInfo(psiPackage, flatten);
        if (packageCoverageInfo == null) {
            return null;
        }
        return getPercentage(packageCoverageInfo.coveredClassCount, packageCoverageInfo.totalClassCount);
    }

    public String getPackageMethodPercentage(PsiPackage psiPackage, boolean flatten) {
        PackageAnnotator.PackageCoverageInfo packageCoverageInfo = getPackageCoverageInfo(psiPackage, flatten);
        if (packageCoverageInfo == null) {
            return null;
        }
        return getPercentage(packageCoverageInfo.coveredMethodCount, packageCoverageInfo.totalMethodCount);
    }

    public String getPackageLinePercentage( PsiPackage psiPackage, boolean flatten) {
        PackageAnnotator.PackageCoverageInfo packageCoverageInfo = getPackageCoverageInfo(psiPackage, flatten);
        if (packageCoverageInfo == null) {
            return null;
        }
        return getPercentage(packageCoverageInfo.coveredLineCount, packageCoverageInfo.totalLineCount);
    }

    public String getClassLinePercentage(String classFQName) {
        PackageAnnotator.ClassCoverageInfo info = myClassCoverageInfos.get(classFQName);
        if (info == null) {
            return null;
        }
        int coveredLines = info.fullyCoveredLineCount + info.partiallyCoveredLineCount;
        return getPercentage(coveredLines, info.totalLineCount);
    }

    public String getClassMethodPercentage(String classFQName) {
        PackageAnnotator.ClassCoverageInfo info = myClassCoverageInfos.get(classFQName);
        if (info == null) {
            return null;
        }
        return getPercentage(info.coveredMethodCount, info.totalMethodCount);
    }

    public String getClassCoveredPercentage(String classFQName) {
        PackageAnnotator.ClassCoverageInfo info = myClassCoverageInfos.get(classFQName);
        if (info == null) {
            return null;
        }
        return getPercentage(info.coveredClassCount, info.totalClassCount);
    }

    private static String getPercentage(int covered, int total) {
        return (int) ((double) covered / total * 100) + "% (" + covered + "/" + total + ")";
    }

    public static PackageAnnotator.PackageCoverageInfo merge(
        PackageAnnotator.PackageCoverageInfo info,
        PackageAnnotator.PackageCoverageInfo testInfo
    ) {
        if (info == null) {
            return testInfo;
        }
        if (testInfo == null) {
            return info;
        }
        PackageAnnotator.PackageCoverageInfo coverageInfo = new PackageAnnotator.PackageCoverageInfo();
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
        PackageAnnotator.ClassCoverageInfo info = myClassCoverageInfos.get(classFQName);
        if (info == null) {
            return null;
        }
        if (info.totalMethodCount == 0 || info.totalLineCount == 0) {
            return null;
        }
        if (coverageDataManager.isSubCoverageActive()) {
            return info.coveredMethodCount + info.fullyCoveredLineCount + info.partiallyCoveredLineCount > 0 ? "covered" : null;
        }
        return (int) ((double) info.coveredMethodCount / info.totalMethodCount * 100) + "% methods, " +
            (int) ((double) (info.fullyCoveredLineCount + info.partiallyCoveredLineCount) / info.totalLineCount * 100) + "% lines covered";
    }

    @Nullable
    public PackageAnnotator.ClassCoverageInfo getClassCoverageInfo(String classFQName) {
        return myClassCoverageInfos.get(classFQName);
    }
}
