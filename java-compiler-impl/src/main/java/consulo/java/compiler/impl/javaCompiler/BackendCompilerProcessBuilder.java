package consulo.java.compiler.impl.javaCompiler;

import consulo.ide.impl.idea.compiler.impl.ModuleChunk;
import com.intellij.java.compiler.impl.javaCompiler.javac.JpsJavaCompilerOptions;
import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.ProcessHandler;
import consulo.process.local.ProcessHandlerFactory;
import consulo.compiler.CompileContext;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.annotation.access.RequiredReadAction;

import javax.annotation.Nonnull;
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

	protected BackendCompilerProcessBuilder(consulo.ide.impl.idea.compiler.impl.ModuleChunk moduleChunk,
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
		FileUtil.asyncDelete(myTempFiles);
	}
}
