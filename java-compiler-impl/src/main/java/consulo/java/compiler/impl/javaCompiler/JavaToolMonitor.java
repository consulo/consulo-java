package consulo.java.compiler.impl.javaCompiler;

import com.intellij.compiler.impl.javaCompiler.BackendCompilerWrapper;
import com.intellij.compiler.impl.javaCompiler.FileObject;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import consulo.java.rt.common.compiler.JavaCompilerInterface;
import org.apache.thrift.TException;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TTransportException;

import javax.annotation.Nonnull;
import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Future;

/**
 * @author VISTALL
 * @since 13/03/2021
 */
public class JavaToolMonitor implements BackendCompilerMonitor, JavaCompilerInterface.Iface
{
	private final TSimpleServer myServer;

	@Nonnull
	private final CompileContext myCompileContext;

	private final BackendCompilerWrapper.ClassParsingHandler myClassParsingHandler;
	private final Future<?> myClassParsingFuture;

	private Path myProjectFilePath;
	private ProcessHandler myProcess;

	public JavaToolMonitor(NewBackendCompilerProcessBuilder processBuilder)
	{
		myCompileContext = processBuilder.getCompileContext();

		myProjectFilePath = Paths.get(myCompileContext.getProject().getBasePath());

		myClassParsingHandler = myCompileContext.getUserData(BackendCompilerWrapper.CLASS_PARSING_HANDLER_KEY);

		assert myClassParsingHandler != null;

		TServerSocket localhost = null;
		try
		{
			localhost = new TServerSocket(new InetSocketAddress("localhost", processBuilder.getPort()));
		}
		catch(TTransportException e)
		{
			throw new IllegalArgumentException(e);
		}

		JavaCompilerInterface.Processor<JavaCompilerInterface.Iface> processor = new JavaCompilerInterface.Processor<>(this);

		myServer = new TSimpleServer(new TServer.Args(localhost).processor(processor));

		myClassParsingFuture = AppExecutorUtil.getAppExecutorService().submit(myClassParsingHandler);

		AppExecutorUtil.getAppExecutorService().execute(myServer::serve);
	}

	@Override
	public void handleProcessStart(ProcessHandler process)
	{
		myProcess = process;
	}

	@Override
	public void dispose()
	{
		myClassParsingHandler.stopParsing();

		if(myClassParsingFuture != null)
		{
			myClassParsingFuture.cancel(false);
		}

		if(myServer != null)
		{
			myServer.stop();
		}
	}

	@Override
	public void logInfo(String message, String fileUri, long lineNumber, long columnNumber) throws TException
	{
		log(CompilerMessageCategory.INFORMATION, message, fileUri, lineNumber, columnNumber);
	}

	@Override
	public void logError(String message, String fileUri, long lineNumber, long columnNumber) throws TException
	{
		log(CompilerMessageCategory.ERROR, message, fileUri, lineNumber, columnNumber);
	}

	@Override
	public void logWarning(String message, String fileUri, long lineNumber, long columnNumber) throws TException
	{
		log(CompilerMessageCategory.WARNING, message, fileUri, lineNumber, columnNumber);
	}

	@Override
	public void fileWrote(String filePath) throws TException
	{
		try
		{
			myClassParsingHandler.addPath(new FileObject(new File(filePath)));
		}
		catch(CacheCorruptedException e)
		{
			if(myProcess != null)
			{
				myProcess.destroyProcess();
			}
		}
	}

	//	public void parsingFileStarted(String fileUri) throws TException
	//	{
	//		String filePath = null;
	//		try
	//		{
	//			URI uri = new URI(fileUri);
	//			Path path = Paths.get(uri).toAbsolutePath();
	//
	//			Path relativize = myProjectFilePath.relativize(path);
	//			if(relativize == null)
	//			{
	//				filePath = path.toString();
	//			}
	//			else
	//			{
	//				filePath = relativize.toString();
	//			}
	//		}
	//		catch(Exception ignored)
	//		{
	//		}
	//
	//		if(filePath != null)
	//		{
	//			myCompileContext.getProgressIndicator().setText(CompilerBundle.message("progress.parsing.file", filePath));
	//		}
	//	}

	private void log(CompilerMessageCategory category, String message, String fileUri, long lineNumber, long columnNumber)
	{
		String fileUrl = null;
		try
		{
			URI uri = new URI(fileUri);
			VirtualFile fileByURL = VfsUtil.findFileByURL(uri.toURL());
			if(fileByURL != null)
			{
				fileUrl = fileByURL.getUrl();
			}
		}
		catch(Exception ignored)
		{
		}

		myCompileContext.addMessage(category, message, fileUrl, (int) lineNumber, (int) columnNumber);
	}
}
