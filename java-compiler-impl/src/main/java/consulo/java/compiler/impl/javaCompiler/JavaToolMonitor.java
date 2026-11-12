package consulo.java.compiler.impl.javaCompiler;

import com.intellij.java.compiler.impl.javaCompiler.BackendCompilerWrapper;
import com.intellij.java.compiler.impl.javaCompiler.FileObject;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.compiler.CacheCorruptedException;
import consulo.compiler.CompileContext;
import consulo.compiler.CompilerMessageCategory;
import consulo.compiler.localize.CompilerLocalize;
import consulo.java.rt.common.compiler.JavaCompilerInterface;
import consulo.localize.LocalizeValue;
import consulo.navigation.Navigatable;
import consulo.platform.Platform;
import consulo.process.ProcessHandler;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import org.apache.thrift.TException;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Future;

/**
 * @author VISTALL
 * @since 2021-03-13
 */
public class JavaToolMonitor implements BackendCompilerMonitor, JavaCompilerInterface.Iface {
    private final TSimpleServer myServer;

    private final CompileContext myCompileContext;

    private final BackendCompilerWrapper.ClassParsingHandler myClassParsingHandler;
    private final Future<?> myClassParsingFuture;

    private final Platform myPlatform;
    private ProcessHandler myProcess;

    public JavaToolMonitor(NewBackendCompilerProcessBuilder processBuilder) {
        myCompileContext = processBuilder.getCompileContext();

        myClassParsingHandler = myCompileContext.getUserData(BackendCompilerWrapper.CLASS_PARSING_HANDLER_KEY);

        assert myClassParsingHandler != null;

        try {
            // the compiler process runs on the machine of the JDK - it connects back to a port bound there
            processBuilder.prepare();
        }
        catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        myPlatform = processBuilder.getPlatform();

        JavaCompilerInterface.Processor<JavaCompilerInterface.Iface> processor = new JavaCompilerInterface.Processor<>(this);

        myServer = new TSimpleServer(new TServer.Args(new PlatformServerTransport(processBuilder.getServerSocket())).processor(processor));

        myClassParsingFuture = AppExecutorUtil.getAppExecutorService().submit(myClassParsingHandler);

        AppExecutorUtil.getAppExecutorService().execute(myServer::serve);
    }

    @Override
    public void handleProcessStart(ProcessHandler process) {
        myProcess = process;
        process.addProcessListener(new ProcessAdapter() {
            @Override
            public void onTextAvailable(ProcessEvent event, Key outputType) {
                String text = event.getText().trim();
                if (text.startsWith("SLF4J")) {
                    return;
                }

                if (text.isEmpty()) {
                    return;
                }

                if (text.startsWith("java.lang.OutOfMemoryError")) {
                    myCompileContext.newError(CompilerLocalize.errorJavacOutOfMemory()).add();
                    return;
                }

                if (text.startsWith("Error:")) {
                    myCompileContext.newError(LocalizeValue.of(text)).add();
                    return;
                }

                myCompileContext.newInfo(LocalizeValue.of(text)).add();
            }
        });
    }

    @Override
    public void dispose() {
        myClassParsingHandler.stopParsing();

        if (myClassParsingFuture != null) {
            myClassParsingFuture.cancel(false);
        }

        if (myServer != null) {
            myServer.stop();
        }
    }

    @Override
    public void logInfo(String message, String fileUri, long lineNumber, long columnNumber) throws TException {
        logMessage(CompilerMessageCategory.INFORMATION, message, fileUri, lineNumber, columnNumber);
    }

    @Override
    public void logError(String message, String fileUri, long lineNumber, long columnNumber) throws TException {
        logMessage(CompilerMessageCategory.ERROR, message, fileUri, lineNumber, columnNumber);
    }

    @Override
    public void logWarning(String message, String fileUri, long lineNumber, long columnNumber) throws TException {
        logMessage(CompilerMessageCategory.WARNING, message, fileUri, lineNumber, columnNumber);
    }

    @Override
    public void fileWrote(String filePath) throws TException {
        try {
            // a path of the machine of the JDK
            myClassParsingHandler.addPath(new FileObject(myPlatform.fs().getPath(filePath)));
        }
        catch (CacheCorruptedException e) {
            if (myProcess != null) {
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
    //			if (relativize == null)
    //			{
    //				filePath = path.toString();
    //			}
    //			else
    //			{
    //				filePath = relativize.toString();
    //			}
    //		}
    //		catch (Exception ignored)
    //		{
    //		}
    //
    //		if (filePath != null)
    //		{
    //			myCompileContext.getProgressIndicator().setText(CompilerBundle.message("progress.parsing.file", filePath));
    //		}
    //	}

    private void logMessage(CompilerMessageCategory category, String message, String fileUri, long lineNumber, long columnNumber) {
        String fileUrl = null;
        try {
            // a file: uri of the machine of the JDK
            URI uri = new URI(fileUri);
            String path = uri.getPath();
            if (path != null) {
                if (myPlatform.os().isWindows() && path.length() > 2 && path.charAt(0) == '/' && path.charAt(2) == ':') {
                    // /C:/a - a drive
                    path = path.substring(1);
                }
                VirtualFile file = VirtualFileManager.getInstance().findFileByNioPath(myPlatform.fs().getPath(path));
                if (file != null) {
                    fileUrl = file.getUrl();
                }
            }
        }
        catch (Exception ignored) {
        }

        myCompileContext.newMessage(category, LocalizeValue.of(message))
            .optionalUrl(fileUrl)
            .position((int) lineNumber, (int) columnNumber)
            .add();
    }
}
