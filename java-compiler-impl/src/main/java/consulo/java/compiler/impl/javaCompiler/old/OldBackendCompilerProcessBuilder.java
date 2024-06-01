package consulo.java.compiler.impl.javaCompiler.old;

import com.intellij.java.compiler.impl.javaCompiler.javac.JavacCompiler;
import com.intellij.java.compiler.impl.javaCompiler.javac.JpsJavaCompilerOptions;
import com.intellij.java.language.impl.projectRoots.ex.JavaSdkUtil;
import com.intellij.java.language.projectRoots.JavaSdkType;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.annotation.access.RequiredReadAction;
import consulo.compiler.CompileContext;
import consulo.compiler.ModuleChunk;
import consulo.compiler.util.CompilerUtil;
import consulo.content.bundle.Sdk;
import consulo.java.compiler.JavaCompilerBundle;
import consulo.java.compiler.JavaCompilerUtil;
import consulo.java.compiler.impl.javaCompiler.BackendCompilerProcessBuilder;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.java.rt.JavaRtClassNames;
import consulo.logging.Logger;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersList;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.io.*;
import java.util.List;

/**
 * @author VISTALL
 * @since 14/03/2021
 */
public class OldBackendCompilerProcessBuilder extends BackendCompilerProcessBuilder
{
	private static final Logger LOG = Logger.getInstance(OldBackendCompilerProcessBuilder.class);

	public static final String JAVAC_MAIN_CLASS = "com.sun.tools.javac.Main";

	public OldBackendCompilerProcessBuilder(ModuleChunk moduleChunk,
											String outputPath,
											CompileContext compileContext,
											JpsJavaCompilerOptions jpsJavaCompilerOptions,
											boolean annotationProcessorsEnabled)
	{
		super(moduleChunk, outputPath, compileContext, jpsJavaCompilerOptions, annotationProcessorsEnabled);
	}

	@RequiredReadAction
	@Nonnull
	@Override
	public GeneralCommandLine buildCommandLine() throws IOException
	{
		return createStartupCommand(myModuleChunk, myOutputPath, myCompileContext, myJavaCompilerOptions, myAnnotationProcessorsEnabled);
	}

	@Nonnull
	@RequiredReadAction
	private GeneralCommandLine createStartupCommand(ModuleChunk chunk,
                                                  String outputPath,
                                                  CompileContext compileContext,
                                                  JpsJavaCompilerOptions javacOptions,
                                                  boolean annotationProcessorsEnabled) throws IOException
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

		GeneralCommandLine commandLine = new GeneralCommandLine();
		sdkType.setupCommandLine(commandLine, jdk);

		ParametersList parametersList = commandLine.getParametersList();
		parametersList.add("-Xmx" + javacOptions.MAXIMUM_HEAP_SIZE + "m");

		final List<String> additionalOptions = JavacCompiler.addAdditionalSettings(parametersList, javacOptions, myAnnotationProcessorsEnabled, version, chunk, annotationProcessorsEnabled);

		JavaCompilerUtil.addLocaleOptions(parametersList, false);

		parametersList.add("-classpath");

		if(version == JavaSdkVersion.JDK_1_0)
		{
			parametersList.add(sdkType.getToolsPath(jdk)); //  do not use JavacRunner for jdk 1.0
		}
		else if(version.isAtLeast(JavaSdkVersion.JDK_1_9))
		{
			parametersList.add(JavaSdkUtil.getJavaRtJarPath());
			parametersList.add(JavaRtClassNames.JAVAC_RUNNER);
			parametersList.add("\"" + versionString + "\"");
		}
		else
		{
			parametersList.add(sdkType.getToolsPath(jdk) + File.pathSeparator + JavaSdkUtil.getJavaRtJarPath());
			parametersList.add(JavaRtClassNames.JAVAC_RUNNER);
			parametersList.add("\"" + versionString + "\"");
		}

		parametersList.add(JAVAC_MAIN_CLASS);

		JavacCompiler.addCommandLineOptions(compileContext, chunk, parametersList, outputPath, jdk, version, myTempFiles, true, myAnnotationProcessorsEnabled, false);

		parametersList.addAll(additionalOptions);

		final List<VirtualFile> files = chunk.getFilesToCompile();

		if(version == JavaSdkVersion.JDK_1_0)
		{
			for(VirtualFile file : files)
			{
				String path = file.getPath();
				if(LOG.isDebugEnabled())
				{
					LOG.debug("Adding path for compilation " + path);
				}
				parametersList.add(CompilerUtil.quotePath(path));
			}
		}
		else
		{
			File sourcesFile = File.createTempFile("javac", ".tmp");
			sourcesFile.deleteOnExit();
			myTempFiles.add(sourcesFile);
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
			parametersList.add("@" + sourcesFile.getAbsolutePath());
		}
		return commandLine;
	}
}
