package com.intellij.java.coverage;

import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.ModuleCompilerPathsManager;
import consulo.container.plugin.PluginManager;
import consulo.execution.coverage.BaseCoverageSuite;
import consulo.execution.coverage.CoverageSuite;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jacoco.core.analysis.*;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;

/**
 * @author anna
 * @since 2008-05-20
 */
@ExtensionImpl
public class JaCoCoCoverageRunner extends JavaCoverageRunner {
    @Override
    @RequiredReadAction
    public ProjectData loadCoverageData(@Nonnull File sessionDataFile, @Nullable CoverageSuite baseCoverageSuite) {
        ProjectData data = new ProjectData();
        try {
            Project project =
                baseCoverageSuite instanceof BaseCoverageSuite ? baseCoverageSuite.getProject() : null;
            if (project != null) {
                loadExecutionData(sessionDataFile, data, project);
            }
        }
        catch (Exception e) {
            return data;
        }
        return data;
    }

    @RequiredReadAction
    private static void loadExecutionData(@Nonnull File sessionDataFile, ProjectData data, @Nonnull Project project) throws IOException {
        ExecutionDataStore executionDataStore = new ExecutionDataStore();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(sessionDataFile);
            ExecutionDataReader executionDataReader = new ExecutionDataReader(fis);

            executionDataReader.setExecutionDataVisitor(executionDataStore);
            executionDataReader.setSessionInfoVisitor(info -> System.out.println(info.toString()));

            //noinspection StatementWithEmptyBody
            while (executionDataReader.read()) {
            }
        }
        finally {
            if (fis != null) {
                fis.close();
            }
        }

        CoverageBuilder coverageBuilder = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(executionDataStore, coverageBuilder);

        Module[] modules = ModuleManager.getInstance(project).getModules();
        for (Module module : modules) {
            ModuleCompilerPathsManager compilerModuleExtension = ModuleCompilerPathsManager.getInstance(module);
            VirtualFile compilerOutput = compilerModuleExtension.getCompilerOutput(ProductionContentFolderTypeProvider.getInstance());
            if (compilerOutput != null) {
                analyzer.analyzeAll(VirtualFileUtil.virtualToIoFile(compilerOutput));
            }

            compilerOutput = compilerModuleExtension.getCompilerOutput(TestContentFolderTypeProvider.getInstance());
            if (compilerOutput != null) {
                analyzer.analyzeAll(VirtualFileUtil.virtualToIoFile(compilerOutput));
            }
        }

        for (IClassCoverage classCoverage : coverageBuilder.getClasses()) {
            String className = classCoverage.getName();
            className = className.replace('\\', '.').replace('/', '.');
            ClassData classData = data.getOrCreateClassData(className);
            Collection<IMethodCoverage> methods = classCoverage.getMethods();
            LineData[] lines = new LineData[classCoverage.getLastLine()];
            for (IMethodCoverage method : methods) {
                final String desc = method.getName() + method.getDesc();
                int firstLine = method.getFirstLine();
                int lastLine = method.getLastLine();
                for (int i = firstLine; i < lastLine; i++) {
                    ILine methodLine = method.getLine(i);
                    final int methodLineStatus = methodLine.getStatus();
                    LineData lineData = new LineData(i, desc) {
                        @Override
                        public int getStatus() {
                            return switch (methodLineStatus) {
                                case ICounter.FULLY_COVERED -> LineCoverage.FULL;
                                case ICounter.PARTLY_COVERED -> LineCoverage.PARTIAL;
                                default -> LineCoverage.NONE;
                            };
                        }
                    };
                    lineData.setHits(methodLineStatus == ICounter.FULLY_COVERED || methodLineStatus == ICounter.PARTLY_COVERED ? 1 : 0);
                    lines[i] = lineData;
                }
            }
            classData.setLines(lines);
        }
    }

    @Override
    public void appendCoverageArgument(
        String sessionDataFilePath,
        String[] patterns,
        OwnJavaParameters javaParameters,
        boolean collectLineInfo,
        boolean isSampling
    ) {
        File agentFile = new File(PluginManager.getPluginPath(JaCoCoCoverageRunner.class), "coverage/jacoco/jacocoagent.jar");

        StringBuilder argument = new StringBuilder("-javaagent:");
        String parentPath = handleSpacesInPath(agentFile);
        argument.append(parentPath).append(File.separator).append(agentFile.getName());
        argument.append("=");
        argument.append("destfile=").append(sessionDataFilePath);
        argument.append(",append=false");
        javaParameters.getVMParametersList().add(argument.toString());
    }


    @Override
    public String getPresentableName() {
        return "JaCoCo";
    }

    @Override
    public String getId() {
        return "jacoco";
    }

    @Override
    public String getDataFileExtension() {
        return "exec";
    }

    @Override
    public boolean isCoverageByTestApplicable() {
        return false;
    }
}
