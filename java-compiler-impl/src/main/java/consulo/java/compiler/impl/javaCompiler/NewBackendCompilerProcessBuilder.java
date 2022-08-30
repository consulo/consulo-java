package consulo.java.compiler.impl.javaCompiler;

import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.ModuleChunk;
import com.intellij.java.compiler.impl.javaCompiler.javac.JavacCompiler;
import com.intellij.java.compiler.impl.javaCompiler.javac.JavacSettingsBuilder;
import com.intellij.java.compiler.impl.javaCompiler.javac.JpsJavaCompilerOptions;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.io.BaseOutputReader;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.compiler.JavaCompilerBundle;
import consulo.java.compiler.JavaCompilerUtil;
import consulo.java.execution.OwnSimpleJavaParameters;
import consulo.java.rt.JavaRtClassNames;
import consulo.java.rt.common.compiler.JavaCompilerInterface;
import consulo.logging.Logger;
import consulo.net.util.NetUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.thrift.TServiceClient;

import javax.annotation.Nonnull;
import java.io.*;
import java.util.List;

/**
 * @author VISTALL
 * @since 14/03/2021
 */
public class NewBackendCompilerProcessBuilder extends BackendCompilerProcessBuilder
{
	private static final Logger LOG = Logger.getInstance(NewBackendCompilerProcessBuilder.class);

	private int myPort;

	public NewBackendCompilerProcessBuilder(ModuleChunk moduleChunk,
											String outputPath,
											CompileContext compileContext,
											JpsJavaCompilerOptions javaCompilerOptions,
											boolean annotationProcessorsEnabled)
	{
		super(moduleChunk, outputPath, compileContext, javaCompilerOptions, annotationProcessorsEnabled);

		myPort = NetUtil.tryToFindAvailableSocketPort();
	}

	@RequiredReadAction
	@Nonnull
	@Override
	public GeneralCommandLine buildCommandLine() throws IOException
	{
		return createNewStartupCommand(myModuleChunk, myOutputPath, myCompileContext, myJavaCompilerOptions);
	}

	@Nonnull
	@Override
	public ProcessHandler createProcess(GeneralCommandLine commandLine) throws ExecutionException
	{
		return new OSProcessHandler(commandLine)
		{
			@Nonnull
			@Override
			protected BaseOutputReader.Options readerOptions()
			{
				return BaseOutputReader.Options.forMostlySilentProcess();
			}
		};
	}

	public int getPort()
	{
		return myPort;
	}

	@Nonnull
	@RequiredReadAction
	private GeneralCommandLine createNewStartupCommand(ModuleChunk chunk,
													   String outputPath,
													   CompileContext compileContext,
													   JpsJavaCompilerOptions javacOptions) throws IOException

	{
		final Sdk jdk = JavacCompiler.getJdkForStartupCommand(chunk);
		final String versionString = jdk.getVersionString();
		JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
		if(versionString == null || version == null || !(jdk.getSdkType() instanceof JavaSdk))
		{
			throw new IllegalArgumentException(JavaCompilerBundle.message("javac.error.unknown.jdk.version", jdk.getName()));
		}

		JavaSdk sdkType = (JavaSdk) jdk.getSdkType();

		if(!version.isAtLeast(JavaSdkVersion.JDK_1_9))
		{
			final String toolsJarPath = sdkType.getToolsPath(jdk);
			if(toolsJarPath == null)
			{
				throw new IllegalArgumentException(JavaCompilerBundle.message("javac.error.tools.jar.missing", jdk.getName()));
			}
		}

		OwnSimpleJavaParameters javaParameters = new OwnSimpleJavaParameters();

		javaParameters.setJdk(jdk);

		javaParameters.getVMParametersList().add("-Dconsulo.port=" + myPort);

		javaParameters.getVMParametersList().add("-Xmx" + javacOptions.MAXIMUM_HEAP_SIZE + "m");

		// rt jar
		javaParameters.getClassPath().add(JavaSdkUtil.getJavaRtJarNotShadedPath());
		// thrift
		javaParameters.getClassPath().add(PathUtil.getJarPathForClass(TServiceClient.class));
		// rt common jar
		javaParameters.getClassPath().add(PathUtil.getJarPathForClass(JavaCompilerInterface.class));
		// commons lang 3
		javaParameters.getClassPath().add(PathUtil.getJarPathForClass(StringUtils.class));
		// slf4j
		javaParameters.getClassPath().add(PathUtil.getJarPathForClass(org.slf4j.Logger.class));

		if(!version.isAtLeast(JavaSdkVersion.JDK_1_9))
		{
			javaParameters.getClassPath().add(sdkType.getToolsPath(jdk));
		}
		else
		{
			javaParameters.getVMParametersList().add("--add-opens");
			javaParameters.getVMParametersList().add("jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");

			javaParameters.getVMParametersList().add("--add-opens");
			javaParameters.getVMParametersList().add("jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED");
		}

		javaParameters.setMainClass(JavaRtClassNames.NEW_COMPILER_RUNNER);

		JavaCompilerUtil.addLocaleOptions(javaParameters.getVMParametersList(), false);

		ParametersList params = javaParameters.getProgramParametersList();

		JavacSettingsBuilder javacSettingsBuilder = new JavacSettingsBuilder(myJavaCompilerOptions);

		params.addAll(javacSettingsBuilder.getOptions(chunk));

		JavacCompiler.addCommandLineOptions(compileContext, chunk, params, outputPath, jdk, version, myTempFiles, true, myAnnotationProcessorsEnabled, true);

		File sourcesFile = File.createTempFile("javac", ".tmp");
		sourcesFile.deleteOnExit();
		myTempFiles.add(sourcesFile);

		List<VirtualFile> files = chunk.getFilesToCompile();

		try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(sourcesFile))))
		{
			for(final VirtualFile file : files)
			{
				// Important: should use "/" slashes!
				// but not for JDK 1.5 - see SCR 36673
				final String path = version.isAtLeast(JavaSdkVersion.JDK_1_5) ? file.getPath().replace('/', File.separatorChar) : file.getPath();
				if(LOG.isDebugEnabled())
				{
					LOG.debug("Adding path for compilation " + path);
				}
				writer.println(version == JavaSdkVersion.JDK_1_1 ? path : CompilerUtil.quotePath(path));
			}
		}

		params.add("@" + sourcesFile.getAbsolutePath());

		try
		{
			return javaParameters.toCommandLine();
		}
		catch(CantRunException e)
		{
			throw new IOException(e);
		}
	}
}
