package consulo.java.compiler.impl.javaCompiler;

import com.intellij.java.compiler.impl.javaCompiler.javac.JpsJavaCompilerOptions;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.util.AsyncFileService;
import consulo.compiler.CompileContext;
import consulo.compiler.ModuleChunk;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.local.ProcessHandlerFactory;

import jakarta.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 14/03/2021
 */
public abstract class BackendCompilerProcessBuilder
{
	protected final List<File> myTempFiles = new ArrayList<>();

	protected final ModuleChunk myModuleChunk;
	protected final String myOutputPath;
	protected final CompileContext myCompileContext;
	protected final JpsJavaCompilerOptions myJavaCompilerOptions;
	protected final boolean myAnnotationProcessorsEnabled;

	protected BackendCompilerProcessBuilder(ModuleChunk moduleChunk,
                                          String outputPath,
                                          CompileContext compileContext,
                                          JpsJavaCompilerOptions javaCompilerOptions,
                                          boolean annotationProcessorsEnabled)
	{
		myModuleChunk = moduleChunk;
		myOutputPath = outputPath;
		myCompileContext = compileContext;
		myJavaCompilerOptions = javaCompilerOptions;
		myAnnotationProcessorsEnabled = annotationProcessorsEnabled;
	}

	public CompileContext getCompileContext()
	{
		return myCompileContext;
	}

	@Nonnull
	@RequiredReadAction
	public abstract GeneralCommandLine buildCommandLine() throws IOException;

	@Nonnull
	public ProcessHandler createProcess(GeneralCommandLine commandLine) throws ExecutionException
	{
		return ProcessHandlerFactory.getInstance().createProcessHandler(commandLine);
	}

	public void clearTempFiles()
	{
		Application.get().getInstance(AsyncFileService.class).asyncDelete(myTempFiles);
	}
}
