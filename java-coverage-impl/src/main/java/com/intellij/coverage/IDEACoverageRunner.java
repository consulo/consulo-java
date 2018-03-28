package com.intellij.coverage;

import java.io.File;
import java.io.IOException;

import javax.annotation.Nonnull;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.util.ProjectDataLoader;
import consulo.java.execution.configurations.OwnJavaParameters;

/*
 * User: anna
 * Date: 20-May-2008
 */
public class IDEACoverageRunner extends JavaCoverageRunner
{
	private static final Logger LOG = Logger.getInstance(IDEACoverageRunner.class.getName());

	@Override
	public ProjectData loadCoverageData(@Nonnull final File sessionDataFile, @javax.annotation.Nullable final CoverageSuite coverageSuite)
	{
		return ProjectDataLoader.load(sessionDataFile);
	}

	@Override
	public void appendCoverageArgument(final String sessionDataFilePath,
			final String[] patterns,
			final OwnJavaParameters javaParameters,
			final boolean collectLineInfo,
			final boolean isSampling)
	{
		StringBuilder argument = new StringBuilder("-javaagent:");
		File agentFile = new File(PluginManager.getPluginPath(IDEACoverageRunner.class), "coverage/consulo/agent.jar");

		final String parentPath = handleSpacesInPath(agentFile);
		argument.append(parentPath).append(File.separator).append(agentFile.getName());
		argument.append("=");
		try
		{
			final File tempFile = createTempFile();
			tempFile.deleteOnExit();
			write2file(tempFile, sessionDataFilePath);
			write2file(tempFile, String.valueOf(collectLineInfo));
			write2file(tempFile, Boolean.FALSE.toString()); //append unloaded
			write2file(tempFile, Boolean.FALSE.toString());//merge with existing
			write2file(tempFile, String.valueOf(isSampling));
			if(patterns != null)
			{
				for(String coveragePattern : patterns)
				{
					coveragePattern = coveragePattern.replace("$", "\\$").replace(".", "\\.").replaceAll("\\*", ".*");
					if(!coveragePattern.endsWith(".*"))
					{ //include inner classes
						coveragePattern += "(\\$.*)*";
					}
					write2file(tempFile, coveragePattern);
				}
			}
			argument.append(tempFile.getCanonicalPath());
		}
		catch(IOException e)
		{
			LOG.info("Coverage was not enabled", e);
			return;
		}

		javaParameters.getVMParametersList().add(argument.toString());
	}


	@Override
	public String getPresentableName()
	{
		return "Consulo (Internal)";
	}

	@Override
	public String getId()
	{
		return "idea";
	}

	@Override
	public String getDataFileExtension()
	{
		return "ic";
	}

	@Override
	public boolean isCoverageByTestApplicable()
	{
		return true;
	}
}