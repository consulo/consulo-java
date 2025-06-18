package com.intellij.java.coverage;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import consulo.annotation.access.RequiredReadAction;
import consulo.compiler.ModuleCompilerPathsManager;
import consulo.content.ContentFolderTypeProvider;
import consulo.execution.coverage.CoverageDataManager;
import consulo.execution.coverage.CoverageSuite;
import consulo.execution.coverage.CoverageSuitesBundle;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleFileIndex;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ContentFolder;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author ven
 */
public class PackageAnnotator {
    private final PsiJavaPackage myPackage;
    private final Project myProject;
    private final PsiManager myManager;
    private final CoverageDataManager myCoverageManager;

    public PackageAnnotator(PsiJavaPackage aPackage) {
        myPackage = aPackage;
        myProject = myPackage.getProject();
        myManager = PsiManager.getInstance(myProject);
        myCoverageManager = CoverageDataManager.getInstance(myProject);
    }

    public interface Annotator {
        void annotateSourceDirectory(VirtualFile virtualFile, PackageCoverageInfo packageCoverageInfo, Module module);

        void annotateTestDirectory(VirtualFile virtualFile, PackageCoverageInfo packageCoverageInfo, Module module);

        void annotatePackage(String packageQualifiedName, PackageCoverageInfo packageCoverageInfo);

        void annotatePackage(String packageQualifiedName, PackageCoverageInfo packageCoverageInfo, boolean flatten);

        void annotateClass(String classQualifiedName, ClassCoverageInfo classCoverageInfo);
    }

    public static class ClassCoverageInfo {
        public int totalLineCount;
        public int fullyCoveredLineCount;
        public int partiallyCoveredLineCount;
        public int totalMethodCount;
        public int coveredMethodCount;

        public int totalClassCount = 1;
        public int coveredClassCount;
    }

    public static class PackageCoverageInfo {
        public int totalClassCount;
        public int coveredClassCount;
        public int totalLineCount;
        public int coveredLineCount;

        public int coveredMethodCount;
        public int totalMethodCount;
    }

    public static class DirCoverageInfo extends PackageCoverageInfo {
        public VirtualFile sourceRoot;

        public DirCoverageInfo(VirtualFile sourceRoot) {
            this.sourceRoot = sourceRoot;
        }
    }

    //get read lock myself when needed
    public void annotate(CoverageSuitesBundle suite, Annotator annotator) {
        ProjectData data = suite.getCoverageData();

        if (data == null) {
            return;
        }

        String qualifiedName = myPackage.getQualifiedName();
        boolean filtered = false;
        for (CoverageSuite coverageSuite : suite.getSuites()) {
            if (((JavaCoverageSuite) coverageSuite).isPackageFiltered(qualifiedName)) {
                filtered = true;
                break;
            }

        }
        if (!filtered) {
            return;
        }

        GlobalSearchScope scope = suite.getSearchScope(myProject);
        Module[] modules = myCoverageManager.doInReadActionIfProjectOpen(() -> ModuleManager.getInstance(myProject).getModules());

        if (modules == null) {
            return;
        }

        Map<String, PackageCoverageInfo> packageCoverageMap = new HashMap<>();
        Map<String, PackageCoverageInfo> flattenPackageCoverageMap = new HashMap<>();
        for (Module module : modules) {
            if (!scope.isSearchInModuleContent(module)) {
                continue;
            }
            String rootPackageVMName = qualifiedName.replaceAll("\\.", "/");
            VirtualFile output = myCoverageManager.doInReadActionIfProjectOpen(() -> ModuleCompilerPathsManager.getInstance(module)
                .getCompilerOutput(ProductionContentFolderTypeProvider.getInstance()));

            if (output != null) {
                File outputRoot = findRelativeFile(rootPackageVMName, output);
                if (outputRoot.exists()) {
                    collectCoverageInformation(
                        outputRoot,
                        packageCoverageMap,
                        flattenPackageCoverageMap,
                        data,
                        rootPackageVMName,
                        annotator,
                        module,
                        suite.isTrackTestFolders(),
                        false
                    );
                }

            }

            if (suite.isTrackTestFolders()) {
                VirtualFile testPackageRoot =
                    myCoverageManager.doInReadActionIfProjectOpen(() -> ModuleCompilerPathsManager.getInstance(module)
                        .getCompilerOutput(TestContentFolderTypeProvider.getInstance()));

                if (testPackageRoot != null) {
                    File outputRoot = findRelativeFile(rootPackageVMName, testPackageRoot);
                    if (outputRoot.exists()) {
                        collectCoverageInformation(
                            outputRoot,
                            packageCoverageMap,
                            flattenPackageCoverageMap,
                            data,
                            rootPackageVMName,
                            annotator,
                            module,
                            suite.isTrackTestFolders(),
                            true
                        );
                    }
                }
            }
        }

        for (Map.Entry<String, PackageCoverageInfo> entry : packageCoverageMap.entrySet()) {
            String packageFQName = entry.getKey().replaceAll("/", ".");
            PackageCoverageInfo info = entry.getValue();
            annotator.annotatePackage(packageFQName, info);
        }

        for (Map.Entry<String, PackageCoverageInfo> entry : flattenPackageCoverageMap.entrySet()) {
            String packageFQName = entry.getKey().replaceAll("/", ".");
            PackageCoverageInfo info = entry.getValue();
            annotator.annotatePackage(packageFQName, info, true);
        }
    }

    private static File findRelativeFile(String rootPackageVMName, VirtualFile output) {
        File outputRoot = VirtualFileUtil.virtualToIoFile(output);
        outputRoot = rootPackageVMName.length() > 0 ? new File(outputRoot, FileUtil.toSystemDependentName(rootPackageVMName)) : outputRoot;
        return outputRoot;
    }

    @RequiredReadAction
    public void annotateFilteredClass(PsiClass psiClass, CoverageSuitesBundle bundle, Annotator annotator) {
        ProjectData data = bundle.getCoverageData();
        if (data == null) {
            return;
        }
        Module module = psiClass.getModule();
        if (module != null) {
            boolean isInTests = ProjectRootManager.getInstance(module.getProject()).getFileIndex()
                .isInTestSourceContent(psiClass.getContainingFile().getVirtualFile());
            ModuleCompilerPathsManager moduleExtension = ModuleCompilerPathsManager.getInstance(module);
            VirtualFile outputPath = isInTests
                ? moduleExtension.getCompilerOutput(TestContentFolderTypeProvider.getInstance())
                : moduleExtension.getCompilerOutput(ProductionContentFolderTypeProvider.getInstance());

            if (outputPath != null) {
                String qualifiedName = psiClass.getQualifiedName();
                if (qualifiedName == null) {
                    return;
                }
                String packageVMName = StringUtil.getPackageName(qualifiedName).replace('.', '/');
                File packageRoot = findRelativeFile(packageVMName, outputPath);
                if (packageRoot != null && packageRoot.exists()) {
                    Map<String, ClassCoverageInfo> toplevelClassCoverage = new HashMap<>();
                    File[] files = packageRoot.listFiles();
                    if (files != null) {
                        for (File child : files) {
                            if (isClassFile(child)) {
                                String childName = getClassName(child);
                                String classFqVMName = packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
                                String toplevelClassSrcFQName = getSourceToplevelFQName(classFqVMName);
                                if (toplevelClassSrcFQName.equals(qualifiedName)) {
                                    collectClassCoverageInformation(child, new PackageCoverageInfo(), data, toplevelClassCoverage,
                                        classFqVMName.replace("/", "."), toplevelClassSrcFQName
                                    );
                                }
                            }
                        }
                    }
                    for (ClassCoverageInfo coverageInfo : toplevelClassCoverage.values()) {
                        annotator.annotateClass(qualifiedName, coverageInfo);
                    }
                }
            }
        }
    }

    @Nullable
    private DirCoverageInfo[] collectCoverageInformation(
        File packageOutputRoot,
        Map<String, PackageCoverageInfo> packageCoverageMap,
        Map<String, PackageCoverageInfo> flattenPackageCoverageMap,
        ProjectData projectInfo,
        String packageVMName,
        Annotator annotator,
        Module module,
        boolean trackTestFolders,
        boolean isTestHierarchy
    ) {
        List<DirCoverageInfo> dirs = new ArrayList<>();
        ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
        for (ContentEntry contentEntry : contentEntries) {
            Predicate<ContentFolderTypeProvider> predicate = isTestHierarchy
                ? LanguageContentFolderScopes.productionAndTest()
                : LanguageContentFolderScopes.production();
            for (ContentFolder folder : contentEntry.getFolders(predicate)) {
                VirtualFile file = folder.getFile();
                if (file == null) {
                    continue;
                }
                VirtualFile relativeSrcRoot = file.findFileByRelativePath(StringUtil.trimStart(packageVMName, ""));
                dirs.add(new DirCoverageInfo(relativeSrcRoot));
            }
        }

        File[] children = packageOutputRoot.listFiles();

        if (children == null) {
            return null;
        }

        Map<String, ClassCoverageInfo> toplevelClassCoverage = new HashMap<>();
        for (File child : children) {
            if (child.isDirectory()) {
                String childName = child.getName();
                String childPackageVMName = packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
                DirCoverageInfo[] childCoverageInfo = collectCoverageInformation(
                    child,
                    packageCoverageMap,
                    flattenPackageCoverageMap,
                    projectInfo,
                    childPackageVMName,
                    annotator,
                    module,
                    trackTestFolders,
                    isTestHierarchy
                );
                if (childCoverageInfo != null) {
                    for (int i = 0; i < childCoverageInfo.length; i++) {
                        DirCoverageInfo coverageInfo = childCoverageInfo[i];
                        DirCoverageInfo parentDir = dirs.get(i);
                        parentDir.totalClassCount += coverageInfo.totalClassCount;
                        parentDir.coveredClassCount += coverageInfo.coveredClassCount;
                        parentDir.totalLineCount += coverageInfo.totalLineCount;
                        parentDir.coveredLineCount += coverageInfo.coveredLineCount;
                        parentDir.totalMethodCount += coverageInfo.totalMethodCount;
                        parentDir.coveredMethodCount += coverageInfo.coveredMethodCount;
                    }
                }
            }
            else {
                if (isClassFile(child)) {
                    String childName = getClassName(child);
                    String classFqVMName = packageVMName.length() > 0 ? packageVMName + "/" + childName : childName;
                    String toplevelClassSrcFQName = getSourceToplevelFQName(classFqVMName);
                    VirtualFile[] containingFile = new VirtualFile[1];
                    Boolean isInSource = myCoverageManager.doInReadActionIfProjectOpen(() -> {
                        PsiClass aClass = JavaPsiFacade.getInstance(myManager.getProject()).findClass(
                            toplevelClassSrcFQName,
                            GlobalSearchScope.moduleScope(module)
                        );
                        if (aClass == null || !aClass.isValid()) {
                            return Boolean.FALSE;
                        }
                        containingFile[0] = aClass.getContainingFile().getVirtualFile();
                        assert containingFile[0] != null : aClass;
                        ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
                        return fileIndex.isInSourceContent(containingFile[0])
                            && (trackTestFolders || !fileIndex.isInTestSourceContent(containingFile[0]));
                    });
                    if (isInSource != null && isInSource) {
                        for (DirCoverageInfo dirCoverageInfo : dirs) {
                            if (dirCoverageInfo.sourceRoot != null
                                && VirtualFileUtil.isAncestor(dirCoverageInfo.sourceRoot, containingFile[0], false)) {
                                collectClassCoverageInformation(
                                    child,
                                    dirCoverageInfo,
                                    projectInfo,
                                    toplevelClassCoverage,
                                    classFqVMName.replace("/", "."),
                                    toplevelClassSrcFQName
                                );
                                break;
                            }
                        }
                    }
                }
            }
        }

        for (Map.Entry<String, ClassCoverageInfo> entry : toplevelClassCoverage.entrySet()) {
            String toplevelClassName = entry.getKey();
            ClassCoverageInfo coverageInfo = entry.getValue();
            annotator.annotateClass(toplevelClassName, coverageInfo);
        }

        PackageCoverageInfo flattenPackageCoverageInfo = getOrCreateCoverageInfo(flattenPackageCoverageMap, packageVMName);
        for (Map.Entry<String, ClassCoverageInfo> entry : toplevelClassCoverage.entrySet()) {
            ClassCoverageInfo coverageInfo = entry.getValue();
            flattenPackageCoverageInfo.coveredClassCount += coverageInfo.coveredClassCount;
            flattenPackageCoverageInfo.totalClassCount += coverageInfo.totalClassCount;

            flattenPackageCoverageInfo.coveredLineCount += coverageInfo.fullyCoveredLineCount + coverageInfo.partiallyCoveredLineCount;
            flattenPackageCoverageInfo.totalLineCount += coverageInfo.totalLineCount;

            flattenPackageCoverageInfo.coveredMethodCount += coverageInfo.coveredMethodCount;
            flattenPackageCoverageInfo.totalMethodCount += coverageInfo.totalMethodCount;
        }

        PackageCoverageInfo packageCoverageInfo = getOrCreateCoverageInfo(packageCoverageMap, packageVMName);
        for (DirCoverageInfo dir : dirs) {
            packageCoverageInfo.totalClassCount += dir.totalClassCount;
            packageCoverageInfo.totalLineCount += dir.totalLineCount;
            packageCoverageInfo.coveredClassCount += dir.coveredClassCount;
            packageCoverageInfo.coveredLineCount += dir.coveredLineCount;
            packageCoverageInfo.coveredMethodCount += dir.coveredMethodCount;
            packageCoverageInfo.totalMethodCount += dir.totalMethodCount;

            if (isTestHierarchy) {
                annotator.annotateTestDirectory(dir.sourceRoot, dir, module);
            }
            else {
                annotator.annotateSourceDirectory(dir.sourceRoot, dir, module);
            }
        }

        return dirs.toArray(new DirCoverageInfo[dirs.size()]);
    }

    private static boolean isClassFile(File classFile) {
        return classFile.getName().endsWith(".class");
    }

    private static String getClassName(File classFile) {
        return StringUtil.trimEnd(classFile.getName(), ".class");
    }

    private static PackageCoverageInfo getOrCreateCoverageInfo(
        Map<String, PackageCoverageInfo> packageCoverageMap,
        String packageVMName
    ) {
        PackageCoverageInfo coverageInfo = packageCoverageMap.get(packageVMName);
        if (coverageInfo == null) {
            coverageInfo = new PackageCoverageInfo();
            packageCoverageMap.put(packageVMName, coverageInfo);
        }
        return coverageInfo;
    }

    private void collectClassCoverageInformation(
        File classFile,
        PackageCoverageInfo packageCoverageInfo,
        ProjectData projectInfo,
        Map<String, ClassCoverageInfo> toplevelClassCoverage,
        String className,
        String toplevelClassSrcFQName
    ) {
        ClassCoverageInfo toplevelClassCoverageInfo = new ClassCoverageInfo();

        ClassData classData = projectInfo.getClassData(className);

        if (classData != null && classData.getLines() != null) {
            for (Object l : classData.getLines()) {
                if (l instanceof LineData lineData) {
                    if (lineData.getStatus() == LineCoverage.FULL) {
                        toplevelClassCoverageInfo.fullyCoveredLineCount++;
                    }
                    else if (lineData.getStatus() == LineCoverage.PARTIAL) {
                        toplevelClassCoverageInfo.partiallyCoveredLineCount++;
                    }
                    toplevelClassCoverageInfo.totalLineCount++;
                    packageCoverageInfo.totalLineCount++;
                }
            }
            boolean touchedClass = false;
            Collection<String> methodSigs = classData.getMethodSigs();
            for (String nameAndSig : methodSigs) {
                int covered = classData.getStatus(nameAndSig);
                if (covered != LineCoverage.NONE) {
                    toplevelClassCoverageInfo.coveredMethodCount++;
                    touchedClass = true;
                }
            }
            if (!methodSigs.isEmpty()) {
                if (touchedClass) {
                    packageCoverageInfo.coveredClassCount++;
                }
                toplevelClassCoverageInfo.totalMethodCount += methodSigs.size();
                packageCoverageInfo.totalClassCount++;

                packageCoverageInfo.coveredLineCount += toplevelClassCoverageInfo.fullyCoveredLineCount;
                packageCoverageInfo.coveredLineCount += toplevelClassCoverageInfo.partiallyCoveredLineCount;
                packageCoverageInfo.coveredMethodCount += toplevelClassCoverageInfo.coveredMethodCount;
                packageCoverageInfo.totalMethodCount += toplevelClassCoverageInfo.totalMethodCount;
            }
            else {
                return;
            }
        }
        else if (!collectNonCoveredClassInfo(classFile, toplevelClassCoverageInfo, packageCoverageInfo)) {
            return;
        }

        ClassCoverageInfo classCoverageInfo = getOrCreateClassCoverageInfo(toplevelClassCoverage, toplevelClassSrcFQName);
        classCoverageInfo.totalLineCount += toplevelClassCoverageInfo.totalLineCount;
        classCoverageInfo.fullyCoveredLineCount += toplevelClassCoverageInfo.fullyCoveredLineCount;
        classCoverageInfo.partiallyCoveredLineCount += toplevelClassCoverageInfo.partiallyCoveredLineCount;

        classCoverageInfo.totalMethodCount += toplevelClassCoverageInfo.totalMethodCount;
        classCoverageInfo.coveredMethodCount += toplevelClassCoverageInfo.coveredMethodCount;
        if (toplevelClassCoverageInfo.coveredMethodCount > 0) {
            classCoverageInfo.coveredClassCount++;
        }
    }

    private static ClassCoverageInfo getOrCreateClassCoverageInfo(
        Map<String, ClassCoverageInfo> toplevelClassCoverage,
        String sourceToplevelFQName
    ) {
        ClassCoverageInfo toplevelClassCoverageInfo = toplevelClassCoverage.get(sourceToplevelFQName);
        if (toplevelClassCoverageInfo == null) {
            toplevelClassCoverageInfo = new ClassCoverageInfo();
            toplevelClassCoverage.put(sourceToplevelFQName, toplevelClassCoverageInfo);
        }
        else {
            toplevelClassCoverageInfo.totalClassCount++;
        }
        return toplevelClassCoverageInfo;
    }

    private static String getSourceToplevelFQName(String classFQVMName) {
        int index = classFQVMName.indexOf('$');
        if (index > 0) {
            classFQVMName = classFQVMName.substring(0, index);
        }
        if (classFQVMName.startsWith("/")) {
            classFQVMName = classFQVMName.substring(1);
        }
        return classFQVMName.replaceAll("/", ".");
    }


    /**
     * return true if there is executable code in the class
     */
    private boolean collectNonCoveredClassInfo(
        File classFile,
        ClassCoverageInfo classCoverageInfo,
        PackageCoverageInfo packageCoverageInfo
    ) {
        byte[] content = myCoverageManager.doInReadActionIfProjectOpen(() -> {
            try {
                return Files.readAllBytes(classFile.toPath());
            }
            catch (IOException e) {
                return null;
            }
        });
        CoverageSuitesBundle coverageSuite = CoverageDataManager.getInstance(myProject).getCurrentSuitesBundle();
        //noinspection SimplifiableIfStatement
        if (coverageSuite == null) {
            return false;
        }
        return SourceLineCounterUtil.collectNonCoveredClassInfo(
            classCoverageInfo,
            packageCoverageInfo,
            content,
            coverageSuite.isTracingEnabled()
        );
    }
}
