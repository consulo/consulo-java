package consulo.java.rt.compiler;

import consulo.java.rt.common.compiler.JavaCompilerInterface;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;

import javax.tools.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @author VISTALL
 * @since 13/03/2021
 * <p>
 * JDK 1.6+
 */
public class NewJavaRunner
{
	public static void main(String[] args) throws Exception
	{
		String outputDir = null;

		Set<File> bootclasspath = Collections.emptySet();
		Set<File> classpath = Collections.emptySet();
		Set<File> sourcepath = Collections.emptySet();
		Set<File> modulePaths = Collections.emptySet();

		List<String> options = new ArrayList<String>();

		// do not eat last paths
		for(int i = 0; i < (args.length - 1); i++)
		{
			String arg = args[i];

			if("-d".equals(arg))
			{
				outputDir = args[++i];
			}
			else if("-bootclasspath".equals(arg))
			{
				bootclasspath = readAllFiles(args[++i]);
			}
			else if("-classpath".equals(arg))
			{
				classpath = readAllFiles(args[++i]);
			}
			else if("-sourcepath".equals(arg))
			{
				sourcepath = readAllFiles(args[++i]);
			}
			else if("--module-path".equals(arg))
			{
				modulePaths = readAllFiles(args[++i]);
			}
			else
			{
				options.add(arg);
			}
		}

		int port = Integer.parseInt(System.getProperty("consulo.port"));

		TSocket socket = new TSocket("localhost", port);

		final JavaCompilerInterface.Client client = new JavaCompilerInterface.Client(new TBinaryProtocol(socket));

		socket.open();

		if(outputDir == null)
		{
			throw new IllegalArgumentException("output dir not set");
		}

		String filePaths = args[args.length - 1];

		JavaCompiler systemJavaCompiler = ToolProvider.getSystemJavaCompiler();

		DiagnosticListener<JavaFileObject> diagnosticListener = new DiagnosticListener<JavaFileObject>()
		{
			public void report(Diagnostic<? extends JavaFileObject> diagnostic)
			{
				String message = diagnostic.getMessage(Locale.getDefault());
				long lineNumber = diagnostic.getLineNumber();
				long columnNumber = diagnostic.getColumnNumber();
				String fileUrl = null;
				JavaFileObject source = diagnostic.getSource();
				if(source != null)
				{
					fileUrl = source.toUri().toString();
				}

				switch(diagnostic.getKind())
				{
					case ERROR:
						try
						{
							client.logError(message, fileUrl, lineNumber, columnNumber);
						}
						catch(TException ignored)
						{
						}
						break;
					case NOTE:
						try
						{
							client.logInfo(message, fileUrl, lineNumber, columnNumber);
						}
						catch(TException ignored)
						{
						}
						break;
					case WARNING:
					case MANDATORY_WARNING:
						try
						{
							client.logWarning(message, fileUrl, lineNumber, columnNumber);
						}
						catch(TException ignored)
						{
						}
						break;
				}
			}
		};

		final StandardJavaFileManager standardFileManager = systemJavaCompiler.getStandardFileManager(diagnosticListener, Locale.getDefault(), Charset.forName("UTF-8"));
		standardFileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(new File(outputDir)));

		if(!bootclasspath.isEmpty())
		{
			standardFileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, bootclasspath);
		}

		if(!classpath.isEmpty())
		{
			standardFileManager.setLocation(StandardLocation.CLASS_PATH, classpath);
		}

		if(!sourcepath.isEmpty())
		{
			standardFileManager.setLocation(StandardLocation.SOURCE_PATH, sourcepath);
		}

		if(!modulePaths.isEmpty())
		{
			standardFileManager.setLocation(StandardLocationWrapper.MODULE_PATH(), modulePaths);
		}

		List<String> classes = new ArrayList<String>();

		Set<File> files = readAllFiles(filePaths);

		Iterable<? extends JavaFileObject> javaFileObjects = standardFileManager.getJavaFileObjects(files.toArray(new File[files.size()]));

		JavaCompiler.CompilationTask task = systemJavaCompiler.getTask(new PrintWriter(System.out), standardFileManager, diagnosticListener, options, classes, javaFileObjects);

		OracleJavacLogHack.tryToInject(task, client);
		
		if(!task.call())
		{
			socket.close();
			throw new IllegalArgumentException("compilation failed");
		}

		socket.close();
	}

	private static Set<File> readAllFiles(String pathWithAt) throws IOException
	{
		if(pathWithAt.length() == 0 || pathWithAt.equals("\"\""))
		{
			return Collections.emptySet();
		}

		Set<File> files = new LinkedHashSet<File>();

		if(!pathWithAt.startsWith("@"))
		{
			return Collections.singleton(new File(pathWithAt));
		}

		String path = pathWithAt.substring(1);

		BufferedReader reader = null;
		try
		{
			reader = new BufferedReader(new FileReader(new File(path)));
			for(String filePath = reader.readLine(); filePath != null; filePath = reader.readLine())
			{
				files.add(new File(filePath));
			}
		}
		finally
		{
			if(reader != null)
			{
				reader.close();
			}
		}

		return files;
	}
}
