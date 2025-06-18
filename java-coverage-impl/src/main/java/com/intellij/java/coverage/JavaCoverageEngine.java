package com.intellij.java.coverage;

import com.intellij.java.analysis.impl.psi.controlFlow.AllVariablesControlFlowPolicy;
import com.intellij.java.coverage.view.JavaCoverageViewExtension;
import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.impl.application.ApplicationConfiguration;
import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.impl.psi.controlFlow.*;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiSwitchStatementImpl;
import com.intellij.java.language.psi.*;
import com.intellij.rt.coverage.data.JumpData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.SwitchData;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.compiler.CompilerManager;
import consulo.compiler.ModuleCompilerPathsManager;
import consulo.content.ContentFolderTypeProvider;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.coverage.*;
import consulo.execution.coverage.view.CoverageViewExtension;
import consulo.execution.test.AbstractTestProxy;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author Roman.Chernyatchik
 */
@ExtensionImpl
public class JavaCoverageEngine extends CoverageEngine {
    private static final Logger LOG = Logger.getInstance(JavaCoverageEngine.class);

    @Override
    public boolean isApplicableTo(@Nullable RunConfigurationBase conf) {
        if (conf instanceof CommonJavaRunConfigurationParameters) {
            return true;
        }
        for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensionList()) {
            if (extension.isApplicableTo(conf)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canHavePerTestCoverage(@Nullable RunConfigurationBase conf) {
        return !(conf instanceof ApplicationConfiguration) && conf instanceof CommonJavaRunConfigurationParameters;
    }

    @Nonnull
    @Override
    public CoverageEnabledConfiguration createCoverageEnabledConfiguration(@Nullable RunConfigurationBase conf) {
        return new JavaCoverageEnabledConfiguration(conf, this);
    }

    @Nullable
    @Override
    public CoverageSuite createCoverageSuite(
        @Nonnull CoverageRunner covRunner,
        @Nonnull String name,
        @Nonnull CoverageFileProvider coverageDataFileProvider,
        String[] filters,
        long lastCoverageTimeStamp,
        String suiteToMerge,
        boolean coverageByTestEnabled,
        boolean tracingEnabled,
        boolean trackTestFolders, Project project
    ) {
        return createSuite(
            covRunner,
            name,
            coverageDataFileProvider,
            filters,
            lastCoverageTimeStamp,
            coverageByTestEnabled,
            tracingEnabled,
            trackTestFolders,
            project
        );
    }

    @Override
    public CoverageSuite createCoverageSuite(
        @Nonnull CoverageRunner covRunner,
        @Nonnull String name,
        @Nonnull CoverageFileProvider coverageDataFileProvider,
        @Nonnull CoverageEnabledConfiguration config
    ) {
        if (config instanceof JavaCoverageEnabledConfiguration javaConfig) {
            return createSuite(
                covRunner,
                name,
                coverageDataFileProvider,
                javaConfig.getPatterns(),
                new Date().getTime(),
                javaConfig.isTrackPerTestCoverage() && !javaConfig.isSampling(),
                !javaConfig.isSampling(),
                javaConfig.isTrackTestFolders(),
                config.getConfiguration().getProject()
            );
        }
        return null;
    }

    @Nullable
    @Override
    public CoverageSuite createEmptyCoverageSuite(@Nonnull CoverageRunner coverageRunner) {
        return new JavaCoverageSuite(this);
    }

    @Nonnull
    @Override
    public CoverageAnnotator getCoverageAnnotator(Project project) {
        return JavaCoverageAnnotator.getInstance(project);
    }

    /**
     * Determines if coverage information should be displayed for given file
     *
     * @param psiFile
     * @return
     */
    @Override
    public boolean coverageEditorHighlightingApplicableTo(@Nonnull PsiFile psiFile) {
        if (!(psiFile instanceof PsiClassOwner)) {
            return false;
        }
        // let's show coverage only for module files
        Module module = psiFile.getApplication().runReadAction((Supplier<Module>) psiFile::getModule);
        return module != null;
    }

    @Override
    public boolean acceptedByFilters(@Nonnull PsiFile psiFile, @Nonnull CoverageSuitesBundle suite) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) {
            return false;
        }
        Project project = psiFile.getProject();
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        if (!suite.isTrackTestFolders() && fileIndex.isInTestSourceContent(virtualFile)) {
            return false;
        }

        for (CoverageSuite coverageSuite : suite.getSuites()) {
            JavaCoverageSuite javaSuite = (JavaCoverageSuite) coverageSuite;

            List<PsiJavaPackage> packages = javaSuite.getCurrentSuitePackages(project);
            if (isUnderFilteredPackages((PsiClassOwner) psiFile, packages)) {
                return true;
            }
            else {
                List<PsiClass> classes = javaSuite.getCurrentSuiteClasses(project);
                for (PsiClass aClass : classes) {
                    PsiFile containingFile = aClass.getContainingFile();
                    if (psiFile.equals(containingFile)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean recompileProjectAndRerunAction(
        @Nonnull Module module,
        @Nonnull CoverageSuitesBundle suite,
        @Nonnull Runnable chooseSuiteAction
    ) {
        VirtualFile outputpath =
            ModuleCompilerPathsManager.getInstance(module).getCompilerOutput(ProductionContentFolderTypeProvider.getInstance());
        VirtualFile testOutputpath =
            ModuleCompilerPathsManager.getInstance(module).getCompilerOutput(TestContentFolderTypeProvider.getInstance());

        if ((outputpath == null && isModuleOutputNeeded(module, ProductionContentFolderTypeProvider.getInstance()))
            || (suite.isTrackTestFolders() && testOutputpath == null
            && isModuleOutputNeeded(module, TestContentFolderTypeProvider.getInstance()))) {
            Project project = module.getProject();
            if (suite.isModuleChecked(module)) {
                return false;
            }
            suite.checkModule(module);
            Runnable runnable = () -> {
                if (Messages.showOkCancelDialog(
                    "Project class files are out of date. Would you like to recompile?" +
                        " The refusal to do it will result in incomplete coverage information",
                    "Project is out of date",
                    UIUtil.getWarningIcon()
                ) == Messages.OK) {
                    CompilerManager compilerManager = CompilerManager.getInstance(project);
                    compilerManager.make(
                        compilerManager.createProjectCompileScope(),
                        (aborted, errors, warnings, compileContext) -> {
                            if (aborted || errors != 0) {
                                return;
                            }
                            Application.get().invokeLater(() -> {
                                if (project.isDisposed()) {
                                    return;
                                }
                                CoverageDataManager.getInstance(project).chooseSuitesBundle(suite);
                            });
                        }
                    );
                }
                else if (!project.isDisposed()) {
                    CoverageDataManager.getInstance(project).chooseSuitesBundle(null);
                }
            };
            project.getApplication().invokeLater(runnable);
            return true;
        }
        return false;
    }

    private static boolean isModuleOutputNeeded(Module module, ContentFolderTypeProvider rootType) {
        return ModuleRootManager.getInstance(module).getContentFolderFiles(it -> it.equals(rootType)).length != 0;
    }

    public static boolean isUnderFilteredPackages(PsiClassOwner javaFile, List<PsiJavaPackage> packages) {
        String hisPackageName = javaFile.getApplication().runReadAction((Supplier<String>) javaFile::getPackageName);
        PsiPackage hisPackage = JavaPsiFacade.getInstance(javaFile.getProject()).findPackage(hisPackageName);
        if (hisPackage == null) {
            return false;
        }
        for (PsiPackage aPackage : packages) {
            if (PsiTreeUtil.isAncestor(aPackage, hisPackage, false)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @Override
    public List<Integer> collectSrcLinesForUntouchedFile(@Nonnull File classFile, @Nonnull CoverageSuitesBundle suite) {
        List<Integer> uncoveredLines = new ArrayList<>();

        byte[] content;
        try {
            content = Files.readAllBytes(classFile.toPath());
        }
        catch (IOException e) {
            return null;
        }

        try {
            SourceLineCounterUtil.collectSrcLinesForUntouchedFiles(uncoveredLines, content, suite.isTracingEnabled());
        }
        catch (Exception e) {
            LOG.error("Fail to process class from: " + classFile.getPath(), e);
        }
        return uncoveredLines;
    }

    @Override
    public boolean includeUntouchedFileInCoverage(
        @Nonnull String qualifiedName,
        @Nonnull File outputFile,
        @Nonnull PsiFile sourceFile,
        @Nonnull CoverageSuitesBundle suite
    ) {
        for (CoverageSuite coverageSuite : suite.getSuites()) {
            JavaCoverageSuite javaSuite = (JavaCoverageSuite) coverageSuite;
            if (javaSuite.isClassFiltered(qualifiedName) || javaSuite.isPackageFiltered(getPackageName(sourceFile))) {
                return true;
            }
        }
        return false;
    }


    @Override
    public String getQualifiedName(@Nonnull File outputFile, @Nonnull PsiFile sourceFile) {
        String packageFQName = getPackageName(sourceFile);
        return StringUtil.getQualifiedName(packageFQName, FileUtil.getNameWithoutExtension(outputFile));
    }

    @Nonnull
    @Override
    public Set<String> getQualifiedNames(@Nonnull PsiFile sourceFile) {
        Application application = sourceFile.getApplication();
        PsiClass[] classes = application.runReadAction((Supplier<PsiClass[]>) ((PsiClassOwner) sourceFile)::getClasses);
        Set<String> qNames = new HashSet<>();
        for (JavaCoverageEngineExtension nameExtension : JavaCoverageEngineExtension.EP_NAME.getExtensions()) {
            if (application.runReadAction((Supplier<Boolean>) () -> nameExtension.suggestQualifiedName(sourceFile, classes, qNames))) {
                return qNames;
            }
        }
        for (PsiClass aClass : classes) {
            String qName = application.runReadAction((Supplier<String>) aClass::getQualifiedName);
            if (qName == null) {
                continue;
            }
            qNames.add(qName);
        }
        return qNames;
    }

    @Nonnull
    @Override
    public Set<File> getCorrespondingOutputFiles(
        @Nonnull PsiFile srcFile,
        @Nullable Module module,
        @Nonnull CoverageSuitesBundle suite
    ) {
        if (module == null) {
            return Collections.emptySet();
        }
        Set<File> classFiles = new HashSet<>();
        ModuleCompilerPathsManager pathsManager = ModuleCompilerPathsManager.getInstance(module);
        VirtualFile outputpath = pathsManager.getCompilerOutput(ProductionContentFolderTypeProvider.getInstance());
        VirtualFile testOutputpath = pathsManager.getCompilerOutput(TestContentFolderTypeProvider.getInstance());

        for (JavaCoverageEngineExtension extension : JavaCoverageEngineExtension.EP_NAME.getExtensions()) {
            if (extension.collectOutputFiles(srcFile, outputpath, testOutputpath, suite, classFiles)) {
                return classFiles;
            }
        }

        String packageFQName = getPackageName(srcFile);
        String packageVmName = packageFQName.replace('.', '/');

        List<File> children = new ArrayList<>();
        File vDir = outputpath == null
            ? null
            : packageVmName.length() > 0
            ? new File(outputpath.getPath() + File.separator + packageVmName)
            : VirtualFileUtil.virtualToIoFile(outputpath);
        if (vDir != null && vDir.exists()) {
            Collections.addAll(children, vDir.listFiles());
        }

        if (suite.isTrackTestFolders()) {
            File testDir = testOutputpath == null
                ? null
                : packageVmName.length() > 0
                ? new File(testOutputpath.getPath() + File.separator + packageVmName)
                : VirtualFileUtil.virtualToIoFile(testOutputpath);
            if (testDir != null && testDir.exists()) {
                Collections.addAll(children, testDir.listFiles());
            }
        }

        Application application = srcFile.getApplication();
        PsiClass[] classes = application.runReadAction((Supplier<PsiClass[]>) ((PsiClassOwner) srcFile)::getClasses);
        for (PsiClass psiClass : classes) {
            String className = application.runReadAction((Supplier<String>) psiClass::getName);
            for (File child : children) {
                if (FileUtil.extensionEquals(child.getName(), JavaClassFileType.INSTANCE.getDefaultExtension())) {
                    String childName = FileUtil.getNameWithoutExtension(child);
                    if (childName.equals(className) ||  //class or inner
                        childName.startsWith(className) && childName.charAt(className.length()) == '$') {
                        classFiles.add(child);
                    }
                }
            }
        }
        return classFiles;
    }

    @Override
    @RequiredReadAction
    public String generateBriefReport(
        @Nonnull Editor editor,
        @Nonnull PsiFile psiFile,
        int lineNumber,
        int startOffset,
        int endOffset,
        @Nullable LineData lineData
    ) {
        StringBuilder buf = new StringBuilder();
        buf.append("Hits: ");
        if (lineData == null) {
            buf.append(0);
            return buf.toString();
        }
        buf.append(lineData.getHits()).append("\n");

        List<PsiExpression> expressions = new ArrayList<>();

        Project project = editor.getProject();
        for (int offset = startOffset; offset < endOffset; offset++) {
            PsiElement parent = PsiTreeUtil.getParentOfType(psiFile.findElementAt(offset), PsiStatement.class);
            PsiElement condition = null;
            if (parent instanceof PsiIfStatement ifStatement) {
                condition = ifStatement.getCondition();
            }
            else if (parent instanceof PsiSwitchStatement switchStatement) {
                condition = switchStatement.getExpression();
            }
            else if (parent instanceof PsiDoWhileStatement doWhileStatement) {
                condition = doWhileStatement.getCondition();
            }
            else if (parent instanceof PsiForStatement forStatement) {
                condition = forStatement.getCondition();
            }
            else if (parent instanceof PsiWhileStatement whileStatement) {
                condition = whileStatement.getCondition();
            }
            else if (parent instanceof PsiForeachStatement foreachStatement) {
                condition = foreachStatement.getIteratedValue();
            }
            else if (parent instanceof PsiAssertStatement assertStatement) {
                condition = assertStatement.getAssertCondition();
            }
            if (condition != null && PsiTreeUtil.isAncestor(condition, psiFile.findElementAt(offset), false)) {
                try {
                    ControlFlow controlFlow = ControlFlowFactory.getInstance(project)
                        .getControlFlow(parent, AllVariablesControlFlowPolicy.getInstance());
                    for (Instruction instruction : controlFlow.getInstructions()) {
                        if (instruction instanceof ConditionalBranchingInstruction branchingInstruction) {
                            PsiExpression expression = branchingInstruction.expression;
                            if (!expressions.contains(expression)) {
                                expressions.add(expression);
                            }
                        }
                    }
                }
                catch (AnalysisCanceledException e) {
                    return buf.toString();
                }
            }
        }

        String indent = "    ";
        try {
            int idx = 0;
            int hits = 0;
            if (lineData.getJumps() != null) {
                for (Object o : lineData.getJumps()) {
                    JumpData jumpData = (JumpData) o;
                    if (jumpData.getTrueHits() + jumpData.getFalseHits() > 0) {
                        PsiExpression expression = expressions.get(idx++);
                        PsiElement parentExpression = expression.getParent();
                        boolean reverse = parentExpression instanceof PsiPolyadicExpression polyExpr
                            && polyExpr.getOperationTokenType() == JavaTokenType.OROR
                            || parentExpression instanceof PsiDoWhileStatement
                            || parentExpression instanceof PsiAssertStatement;
                        buf.append(indent).append(expression.getText()).append("\n");
                        buf.append(indent)
                            .append(indent)
                            .append("true hits: ")
                            .append(reverse ? jumpData.getFalseHits() : jumpData.getTrueHits())
                            .append("\n");
                        buf.append(indent)
                            .append(indent)
                            .append("false hits: ")
                            .append(reverse ? jumpData.getTrueHits() : jumpData.getFalseHits())
                            .append("\n");
                        hits += jumpData.getTrueHits() + jumpData.getFalseHits();
                    }
                }
            }

            if (lineData.getSwitches() != null) {
                for (Object o : lineData.getSwitches()) {
                    SwitchData switchData = (SwitchData) o;
                    PsiExpression conditionExpression = expressions.get(idx++);
                    buf.append(indent).append(conditionExpression.getText()).append("\n");
                    int i = 0;
                    for (int key : switchData.getKeys()) {
                        int switchHits = switchData.getHits()[i++];
                        buf.append(indent).append(indent).append("case ").append(key).append(": ").append(switchHits).append("\n");
                        hits += switchHits;
                    }
                    int defaultHits = switchData.getDefaultHits();
                    boolean hasDefaultLabel = hasDefaultLabel(conditionExpression);
                    if (hasDefaultLabel || defaultHits > 0) {
                        if (!hasDefaultLabel) {
                            defaultHits -= hits;
                        }

                        if (hasDefaultLabel || defaultHits > 0) {
                            buf.append(indent).append(indent).append("default: ").append(defaultHits).append("\n");
                            hits += defaultHits;
                        }
                    }
                }
            }
            if (lineData.getHits() > hits && hits > 0) {
                buf.append("Unknown outcome: ").append(lineData.getHits() - hits);
            }
        }
        catch (Exception e) {
            LOG.info(e);
            return "Hits: " + lineData.getHits();
        }
        return buf.toString();
    }

    @Nullable
    @Override
    public String getTestMethodName(@Nonnull PsiElement element, @Nonnull AbstractTestProxy testProxy) {
        return testProxy.toString();
    }

    @Nonnull
    @Override
    public List<PsiElement> findTestsByNames(@Nonnull String[] testNames, @Nonnull Project project) {
        List<PsiElement> elements = new ArrayList<>();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
        for (String testName : testNames) {
            PsiClass psiClass =
                facade.findClass(StringUtil.getPackageName(testName, '_').replaceAll("\\_", "\\."), projectScope);
            int lastIdx = testName.lastIndexOf("_");
            if (psiClass != null) {
                collectTestsByName(elements, testName, psiClass, lastIdx);
            }
            else {
                String className = testName;
                while (lastIdx > 0) {
                    className = className.substring(0, lastIdx - 1);
                    psiClass = facade.findClass(
                        StringUtil.getPackageName(className, '_').replaceAll("\\_", "\\."),
                        projectScope
                    );
                    lastIdx = className.lastIndexOf("_");
                    if (psiClass != null) {
                        collectTestsByName(elements, testName, psiClass, lastIdx);
                        break;
                    }
                }
            }
        }
        return elements;
    }

    private static void collectTestsByName(List<PsiElement> elements, String testName, PsiClass psiClass, int lastIdx) {
        PsiMethod[] testsByName = psiClass.findMethodsByName(testName.substring(lastIdx + 1), true);
        if (testsByName.length == 1) {
            elements.add(testsByName[0]);
        }
    }

    @RequiredReadAction
    private static boolean hasDefaultLabel(PsiElement conditionExpression) {
        boolean hasDefault = false;
        PsiSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(conditionExpression, PsiSwitchStatement.class);
        PsiCodeBlock body = ((PsiSwitchStatementImpl) conditionExpression.getParent()).getBody();
        if (body != null) {
            PsiElement bodyElement = body.getFirstBodyElement();
            if (bodyElement != null) {
                PsiSwitchLabelStatement label = PsiTreeUtil.getNextSiblingOfType(bodyElement, PsiSwitchLabelStatement.class);
                while (label != null) {
                    if (label.getEnclosingSwitchStatement() == switchStatement) {
                        hasDefault |= label.isDefaultCase();
                    }
                    label = PsiTreeUtil.getNextSiblingOfType(label, PsiSwitchLabelStatement.class);
                }
            }
        }
        return hasDefault;
    }

    protected JavaCoverageSuite createSuite(
        CoverageRunner acceptedCovRunner,
        String name,
        CoverageFileProvider coverageDataFileProvider,
        String[] filters,
        long lastCoverageTimeStamp,
        boolean coverageByTestEnabled,
        boolean tracingEnabled,
        boolean trackTestFolders,
        Project project
    ) {
        return new JavaCoverageSuite(
            name,
            coverageDataFileProvider,
            filters,
            lastCoverageTimeStamp,
            coverageByTestEnabled,
            tracingEnabled,
            trackTestFolders,
            acceptedCovRunner,
            this,
            project
        );
    }

    @Nonnull
    protected static String getPackageName(PsiFile sourceFile) {
        return sourceFile.getApplication().runReadAction((Supplier<String>) ((PsiClassOwner) sourceFile)::getPackageName);
    }

    @Override
    public String getPresentableText() {
        return "Java Coverage";
    }

    @Override
    public CoverageViewExtension createCoverageViewExtension(
        Project project,
        CoverageSuitesBundle suiteBundle,
        CoverageViewManager.StateBean stateBean
    ) {
        return new JavaCoverageViewExtension((JavaCoverageAnnotator) getCoverageAnnotator(project), project, suiteBundle, stateBean);
    }
}
