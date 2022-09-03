/*
 * User: anna
 * Date: 20-May-2008
 */
package com.intellij.java.coverage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.execution.coverage.BaseCoverageSuite;
import consulo.execution.coverage.CoverageSuite;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.ISessionInfoVisitor;
import org.jacoco.core.data.SessionInfo;
import consulo.ide.impl.idea.ide.plugins.PluginManager;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import consulo.compiler.ModuleCompilerPathsManager;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.roots.impl.ProductionContentFolderTypeProvider;
import consulo.roots.impl.TestContentFolderTypeProvider;

public class JaCoCoCoverageRunner extends JavaCoverageRunner
{
	@Override
	public ProjectData loadCoverageData(@Nonnull File sessionDataFile, @Nullable CoverageSuite baseCoverageSuite)
	{
		final ProjectData data = new ProjectData();
		try
		{
			final Project project = baseCoverageSuite instanceof BaseCoverageSuite ? ((BaseCoverageSuite) baseCoverageSuite).getProject() : null;
			if(project != null)
			{
				loadExecutionData(sessionDataFile, data, project);
			}
		}
		catch(Exception e)
		{
			return data;
		}
		return data;
	}

	private static void loadExecutionData(@Nonnull final File sessionDataFile, ProjectData data, @Nonnull Project project) throws IOException
	{
		final ExecutionDataStore executionDataStore = new ExecutionDataStore();
		FileInputStream fis = null;
		try
		{
			fis = new FileInputStream(sessionDataFile);
			final ExecutionDataReader executionDataReader = new ExecutionDataReader(fis);

			executionDataReader.setExecutionDataVisitor(executionDataStore);
			executionDataReader.setSessionInfoVisitor(new ISessionInfoVisitor()
			{
				@Override
				public void visitSessionInfo(SessionInfo info)
				{
					System.out.println(info.toString());
				}
			});

			while(executionDataReader.read())
			{
			}
		}
		finally
		{
			if(fis != null)
			{
				fis.close();
			}
		}

		final CoverageBuilder coverageBuilder = new CoverageBuilder();
		final Analyzer analyzer = new Analyzer(executionDataStore, coverageBuilder);

		final Module[] modules = ModuleManager.getInstance(project).getModules();
		for(Module module : modules)
		{
			final ModuleCompilerPathsManager compilerModuleExtension = ModuleCompilerPathsManager.getInstance(module);
			VirtualFile compilerOutput = compilerModuleExtension.getCompilerOutput(ProductionContentFolderTypeProvider.getInstance());
			if(compilerOutput != null)
			{
				analyzer.analyzeAll(consulo.ide.impl.idea.openapi.vfs.VfsUtil.virtualToIoFile(compilerOutput));
			}

			compilerOutput = compilerModuleExtension.getCompilerOutput(TestContentFolderTypeProvider.getInstance());
			if(compilerOutput != null)
			{
				analyzer.analyzeAll(consulo.ide.impl.idea.openapi.vfs.VfsUtil.virtualToIoFile(compilerOutput));
			}
		}

		for(IClassCoverage classCoverage : coverageBuilder.getClasses())
		{
			String className = classCoverage.getName();
			className = className.replace('\\', '.').replace('/', '.');
			final ClassData classData = data.getOrCreateClassData(className);
			final Collection<IMethodCoverage> methods = classCoverage.getMethods();
			LineData[] lines = new LineData[classCoverage.getLastLine()];
			for(IMethodCoverage method : methods)
			{
				final String desc = method.getName() + method.getDesc();
				final int firstLine = method.getFirstLine();
				final int lastLine = method.getLastLine();
				for(int i = firstLine; i < lastLine; i++)
				{
					final ILine methodLine = method.getLine(i);
					final int methodLineStatus = methodLine.getStatus();
					final LineData lineData = new LineData(i, desc)
					{
						@Override
						public int getStatus()
						{
							switch(methodLineStatus)
							{
								case ICounter.FULLY_COVERED:
									return LineCoverage.FULL;
								case ICounter.PARTLY_COVERED:
									return LineCoverage.PARTIAL;
								default:
									return LineCoverage.NONE;
							}
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
	public void appendCoverageArgument(final String sessionDataFilePath, final String[] patterns, final OwnJavaParameters javaParameters, final boolean collectLineInfo, final boolean isSampling)
	{
		final File agentFile = new File(PluginManager.getPluginPath(JaCoCoCoverageRunner.class), "coverage/jacoco/jacocoagent.jar");

		StringBuilder argument = new StringBuilder("-javaagent:");
		final String parentPath = handleSpacesInPath(agentFile);
		argument.append(parentPath).append(File.separator).append(agentFile.getName());
		argument.append("=");
		argument.append("destfile=").append(sessionDataFilePath);
		argument.append(",append=false");
		javaParameters.getVMParametersList().add(argument.toString());
	}


	@Override
	public String getPresentableName()
	{
		return "JaCoCo";
	}

	@Override
	public String getId()
	{
		return "jacoco";
	}

	@Override
	public String getDataFileExtension()
	{
		return "exec";
	}

	@Override
	public boolean isCoverageByTestApplicable()
	{
		return false;
	}
}
