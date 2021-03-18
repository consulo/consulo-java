package consulo.java.rt.compiler;

import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import consulo.java.rt.common.compiler.JavaCompilerInterface;
import org.apache.thrift.TException;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * @author VISTALL
 * @since 18/03/2021
 */
public class OracleJavacLogHack
{
	private static class OracleJavacLog extends Log
	{
		private final JavaCompilerInterface.Client myClient;

		public OracleJavacLog(Context context, JavaCompilerInterface.Client client)
		{
			super(context);
			myClient = client;
		}

		@Override
		public void printVerbose(String key, Object... args)
		{
			//super.printVerbose(key, args);

			if("wrote.file".equals(key))
			{
				System.out.println("hacked " + args[0]);
				// may string or java file object
				Object arg = args[0];
				if(arg instanceof String)
				{
					try
					{
						myClient.fileWrote((String) arg);
					}
					catch(TException ignored)
					{
					}
				}
				else if(arg instanceof JavaFileObject)
				{
					try
					{
						myClient.fileWrote(((JavaFileObject) arg).getName());
					}
					catch(TException ignored)
					{
					}
				}
			}
		}
	}

	public static void tryToInject(JavaCompiler.CompilationTask compilationTask, JavaCompilerInterface.Client client)
	{
		try
		{
			Method getContextMethod = findMethod(compilationTask.getClass(), "getContext");

			if(getContextMethod == null)
			{
				throw new IllegalArgumentException("no getContext() method");
			}

			Context context = (Context) getContextMethod.invoke(compilationTask);

			Map map = contextMap(context);

			map.remove(Log.logKey);

			new OracleJavacLog(context, client);
		}
		catch(IllegalArgumentException e)
		{
			throw e;
		}
		catch(Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	private static Map contextMap(Context context) throws Exception
	{
		Field ht = context.getClass().getDeclaredField("ht");
		ht.setAccessible(true);
		return (Map) ht.get(context);
	}

	private static Method findMethod(Class<?> cls, String name)
	{
		try
		{
			Method declaredMethod = cls.getDeclaredMethod(name);
			declaredMethod.setAccessible(true);
			return declaredMethod;
		}
		catch(NoSuchMethodException ignored)
		{
		}

		Class<?> superclass = cls.getSuperclass();
		if(superclass != null)
		{
			return findMethod(superclass, name);
		}

		return null;
	}
}
