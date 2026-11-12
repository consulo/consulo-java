package consulo.java.compiler.impl.javaCompiler;

import com.intellij.java.compiler.impl.javaCompiler.javac.JavacCompiler;
import com.intellij.java.compiler.impl.javaCompiler.javac.JavacSettingsBuilder;
import com.intellij.java.compiler.impl.javaCompiler.javac.JpsJavaCompilerOptions;
import com.intellij.java.language.impl.projectRoots.ex.JavaSdkUtil;
import com.intellij.java.language.projectRoots.JavaSdkType;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.ReadAction;
import consulo.application.platform.PlatformTransfer;
import consulo.compiler.CompileContext;
import consulo.compiler.ModuleChunk;
import consulo.compiler.util.CompilerUtil;
import consulo.content.bundle.Sdk;
import consulo.execution.CantRunException;
import consulo.java.compiler.JavaCompilerBundle;
import consulo.java.compiler.JavaCompilerUtil;
import consulo.java.execution.OwnSimpleJavaParameters;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.java.rt.JavaRtClassNames;
import consulo.java.rt.common.compiler.JavaCompilerInterface;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.platform.PlatformServerSocket;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersList;
import consulo.util.io.ClassPathUtil;
import consulo.util.io.FileUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.thrift.TServiceClient;
import org.jspecify.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author VISTALL
 * @since 14/03/2021
 */
public class NewBackendCompilerProcessBuilder extends BackendCompilerProcessBuilder
{
	private static final Logger LOG = Logger.getInstance(NewBackendCompilerProcessBuilder.class);

	private @Nullable Platform myPlatform;
	private @Nullable PlatformServerSocket myServerSocket;
	private List<String> myClassPath = List.of();

	public NewBackendCompilerProcessBuilder(ModuleChunk moduleChunk,
                                          String outputPath,
                                          CompileContext compileContext,
                                          JpsJavaCompilerOptions javaCompilerOptions,
                                          boolean annotationProcessorsEnabled)
	{
		super(moduleChunk, outputPath, compileContext, javaCompilerOptions, annotationProcessorsEnabled);
	}

	/**
	 * Readies the machine of the JDK for the compiler process - not under a read action, it talks to that machine: the port the
	 * process connects back to is bound there, the jars of the process are copied there.
	 */
	public void prepare() throws IOException
	{
		Sdk jdk = ReadAction.compute(() -> JavacCompiler.getJdkForStartupCommand(myModuleChunk));
		Platform platform = jdk.getPlatform();
		myPlatform = platform;
		myServerSocket = platform.net().bind("127.0.0.1", 0);

		PlatformTransfer transfer = PlatformTransfer.getInstance();
		List<String> classPath = new ArrayList<>();
		// rt jar, thrift, rt common jar, commons lang 3, slf4j
		for(String localJar : List.of(JavaSdkUtil.getJavaRtJarNotShadedPath(),
				ClassPathUtil.getJarPathForClass(TServiceClient.class),
				ClassPathUtil.getJarPathForClass(JavaCompilerInterface.class),
				ClassPathUtil.getJarPathForClass(StringUtils.class),
				ClassPathUtil.getJarPathForClass(org.slf4j.Logger.class)))
		{
			classPath.add(transfer.transfer(Path.of(localJar), platform).toString());
		}
		myClassPath = classPath;
	}

	/**
	 * The machine of the JDK - the compiler process runs there.
	 */
	public Platform getPlatform()
	{
		return Objects.requireNonNull(myPlatform, "not prepared");
	}

	/**
	 * The port of the machine of the JDK the compiler process connects back to.
	 */
	public PlatformServerSocket getServerSocket()
	{
		return Objects.requireNonNull(myServerSocket, "not prepared");
	}

	@RequiredReadAction
	@Override
	public GeneralCommandLine buildCommandLine() throws IOException
	{
		return createNewStartupCommand(myModuleChunk, myOutputPath, myCompileContext, myJavaCompilerOptions);
	}

	@Override
	public ProcessHandler createProcess(GeneralCommandLine commandLine) throws ExecutionException
	{
		return ProcessHandlerBuilder.create(commandLine).silentReader().build();
	}


	@RequiredReadAction
	private GeneralCommandLine createNewStartupCommand(ModuleChunk chunk,
													   String outputPath,
													   CompileContext compileContext,
													   JpsJavaCompilerOptions javacOptions) throws IOException

	{
		final Sdk jdk = JavacCompiler.getJdkForStartupCommand(chunk);
		final String versionString = jdk.getVersionString();
		JavaSdkVersion version = JavaSdkTypeUtil.getVersion(jdk);
		if(versionString == null || version == null || !(jdk.getSdkType() instanceof JavaSdkType))
		{
			throw new IllegalArgumentException(JavaCompilerBundle.message("javac.error.unknown.jdk.version", jdk.getName()));
		}

		JavaSdkType sdkType = (JavaSdkType) jdk.getSdkType();

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

		javaParameters.getVMParametersList().add("-Dconsulo.port=" + getServerSocket().getPort());

		javaParameters.getVMParametersList().add("-Xmx" + javacOptions.MAXIMUM_HEAP_SIZE + "m");

		// the jars of the process, on the machine of the JDK
		for(String jar : myClassPath)
		{
			javaParameters.getClassPath().add(jar);
		}

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

		List<Path> files = chunk.getFilesToCompile();

		List<String> sourceLines = new ArrayList<>(files.size());
		for(final Path file : files)
		{
			// Important: should use "/" slashes!
			// but not for JDK 1.5 - see SCR 36673
			final String path = version.isAtLeast(JavaSdkVersion.JDK_1_5) ? file.toString() : FileUtil.toSystemIndependentName(file.toString());
			if(LOG.isDebugEnabled())
			{
				LOG.debug("Adding path for compilation " + path);
			}
			sourceLines.add(version == JavaSdkVersion.JDK_1_1 ? path : CompilerUtil.quotePath(path));
		}

		// a file of the machine of the JDK - javac reads it
		Path sourcesFile = createTempFile(jdk, "javac", sourceLines, myTempFiles);
		params.add("@" + sourcesFile);

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
