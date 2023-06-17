/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.java.execution;

import com.intellij.java.execution.ShortenCommandLine;
import consulo.content.bundle.Sdk;
import consulo.execution.CantRunException;
import consulo.execution.process.ProcessTerminatedListener;
import consulo.java.execution.projectRoots.OwnJdkUtil;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersList;
import consulo.process.cmd.SimpleProgramParameters;
import consulo.process.local.ProcessHandlerFactory;
import consulo.project.Project;
import consulo.util.io.CharsetToolkit;
import consulo.virtualFileSystem.util.PathsList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.Charset;

/**
 * @author Gregory.Shrago
 *
 * Replacement of {@link consulo.process.cmd.SimpleJavaParameters} without dependency to platform code
 */
public class OwnSimpleJavaParameters extends SimpleProgramParameters
{
	private static final Logger LOG = Logger.getInstance(OwnSimpleJavaParameters.class);

	private Sdk myJdk;
	private String myMainClass;
	private final PathsList myClassPath = new PathsList();
	private String myModuleName;
	private final PathsList myModulePath = new PathsList();
	private final ParametersList myVmParameters = new ParametersList();
	private Charset myCharset = CharsetToolkit.getDefaultSystemCharset();
	private boolean myUseDynamicClasspath;
	private boolean myUseDynamicVMOptions;
	private boolean myUseDynamicParameters;
	private boolean myUseClasspathJar;
	private boolean myArgFile;
	private boolean myClasspathFile = true;
	private String myJarPath;

	@Nullable
	public Sdk getJdk()
	{
		return myJdk;
	}

	public void setJdk(Sdk jdk)
	{
		myJdk = jdk;
	}

	public String getMainClass()
	{
		return myMainClass;
	}

	public void setMainClass(String mainClass)
	{
		myMainClass = mainClass;
	}

	public PathsList getClassPath()
	{
		return myClassPath;
	}

	public String getModuleName()
	{
		return myModuleName;
	}

	public void setModuleName(String moduleName)
	{
		myModuleName = moduleName;
	}

	public PathsList getModulePath()
	{
		return myModulePath;
	}

	public ParametersList getVMParametersList()
	{
		return myVmParameters;
	}

	@Nullable
	public Charset getCharset()
	{
		return myCharset;
	}

	public void setCharset(@Nullable Charset charset)
	{
		myCharset = charset;
	}

	public boolean isDynamicClasspath()
	{
		return myUseDynamicClasspath;
	}

	public void setUseDynamicClasspath(boolean useDynamicClasspath)
	{
		myUseDynamicClasspath = useDynamicClasspath && (myArgFile || myUseClasspathJar || myClasspathFile);
	}

	public void setUseDynamicClasspath(@Nullable Project project)
	{
		setUseDynamicClasspath(true);
	}

	public boolean isDynamicVMOptions()
	{
		return myUseDynamicVMOptions;
	}

	/**
	 * Allows to pass system properties via a temporary file in order to avoid "too long command line" problem.
	 */
	public void setUseDynamicVMOptions(boolean useDynamicVMOptions)
	{
		myUseDynamicVMOptions = useDynamicVMOptions;
	}

	public boolean isDynamicParameters()
	{
		return myUseDynamicParameters;
	}

	/**
	 * Allows to pass program parameters via a temporary file in order to avoid "too long command line" problem.
	 */
	public void setUseDynamicParameters(boolean useDynamicParameters)
	{
		myUseDynamicParameters = useDynamicParameters;
	}

	public boolean isUseClasspathJar()
	{
		return myUseClasspathJar;
	}

	public boolean isArgFile()
	{
		return myArgFile;
	}

	/**
	 * Option to use java 9 @argFile
	 */
	public void setArgFile(boolean argFile)
	{
		myArgFile = argFile;
	}

	public boolean isClasspathFile()
	{
		return myClasspathFile;
	}

	public void setClasspathFile(boolean classpathFile)
	{
		myClasspathFile = classpathFile;
	}

	/**
	 * Allows to use a specially crafted .jar file instead of a custom class loader to pass classpath/properties/parameters.
	 * Would have no effect if user explicitly disabled idea.dynamic.classpath.jar
	 */
	public void setUseClasspathJar(boolean useClasspathJar)
	{
		myUseClasspathJar = useClasspathJar;
	}

	public void setShortenCommandLine(@Nullable ShortenCommandLine mode, Project project)
	{
		if(mode == null)
		{
			Sdk jdk = getJdk();
			mode = ShortenCommandLine.getDefaultMethod(project, jdk != null ? jdk.getHomePath() : null);
		}
		myUseDynamicClasspath = mode != ShortenCommandLine.NONE;
		myUseClasspathJar = mode == ShortenCommandLine.MANIFEST;
		setClasspathFile(mode == ShortenCommandLine.CLASSPATH_FILE);
		setArgFile(mode == ShortenCommandLine.ARGS_FILE);
	}

	public String getJarPath()
	{
		return myJarPath;
	}

	public void setJarPath(String jarPath)
	{
		myJarPath = jarPath;
	}

	/**
	 * @throws CantRunException when incorrect Java SDK is specified
	 * @see OwnJdkUtil#setupJVMCommandLine(OwnSimpleJavaParameters)
	 */
	@Nonnull
	public GeneralCommandLine toCommandLine() throws CantRunException
	{
		return OwnJdkUtil.setupJVMCommandLine(this);
	}

	@Nonnull
	public ProcessHandler createOSProcessHandler() throws ExecutionException
	{
		ProcessHandler processHandler = ProcessHandlerFactory.getInstance().createProcessHandler(toCommandLine());
		ProcessTerminatedListener.attach(processHandler);
		return processHandler;
	}

	//<editor-fold desc="Deprecated stuff.">

	/**
	 * @deprecated use {@link #isDynamicParameters()} (to be removed in IDEA 2018)
	 */
	public boolean isPassProgramParametersViaClasspathJar()
	{
		return isDynamicParameters();
	}

	/**
	 * @deprecated use {@link #setUseDynamicParameters(boolean)} (to be removed in IDEA 2018)
	 */
	public void setPassProgramParametersViaClasspathJar(@SuppressWarnings("SameParameterValue") boolean passProgramParametersViaClasspathJar)
	{
		LOG.assertTrue(myUseClasspathJar);
		setUseDynamicParameters(passProgramParametersViaClasspathJar);
	}
	//</editor-fold>
}