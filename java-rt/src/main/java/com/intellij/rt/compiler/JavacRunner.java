/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.rt.compiler;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Vector;

/**
 * MUST BE COMPILED WITH JDK 1.1 IN ORDER TO SUPPORT JAVAC LAUNCHING FOR ALL JDKs
 */
public class JavacRunner
{

	/**
	 * @param args - params
	 *             0. jdk version string
	 *             1. javac main class
	 *             2. javac parameters
	 */
	public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException, IOException
	{
		if(!JavacResourcesReader.dumpPatterns())
		{
			throw new IOException("Unsupported compiler: " + System.getProperty("java.version"));
		}

		final String versionString = args[0];
		final Class aClass = Class.forName(args[1]);
		//noinspection HardCodedStringLiteral
		final Method mainMethod = aClass.getMethod("main", new Class[]{String[].class});
		String[] newArgs;
		if(versionString.indexOf("1.1") > -1)
		{
			// expand the file
			final Vector arguments = new Vector();
			boolean isClasspath = false;
			for(int idx = 3; idx < args.length; idx++)
			{
				final String arg = args[idx];
				if(arg.startsWith("@") && !isClasspath)
				{
					addFilesToCompile(arguments, arg);
				}
				else
				{
					isClasspath = "-classpath".equals(arg) || "-cp".equals(arg) || "-bootclasspath".equals(arg) || "--module-path".equals(arg);
					arguments.addElement(arg);
				}
			}
			newArgs = new String[arguments.size()];
			for(int idx = 0; idx < newArgs.length; idx++)
			{
				newArgs[idx] = (String) arguments.elementAt(idx);
			}
		}
		else
		{
			newArgs = new String[args.length - 2];
			System.arraycopy(args, 2, newArgs, 0, newArgs.length);
		}
		expandClasspath(newArgs);
		try
		{
			mainMethod.invoke(null, new Object[]{newArgs});
		}
		catch(Throwable e)
		{
			System.err.print(e.getMessage());
			e.printStackTrace(System.err);
		}
	}

	private static void addFilesToCompile(Vector arguments, String pathWithAt) throws IOException
	{
		String path = pathWithAt.substring(1);

		BufferedReader reader = null;
		try
		{
			reader = new BufferedReader(new FileReader(new File(path)));
			for(String filePath = reader.readLine(); filePath != null; filePath = reader.readLine())
			{
				arguments.addElement(filePath.replace('/', File.separatorChar));
			}
		}
		finally
		{
			if(reader != null)
			{
				reader.close();
			}
		}
	}

	private static void expandClasspath(String[] args) throws IOException
	{
		for(int idx = 0; idx < args.length; idx++)
		{
			final String arg = args[idx];
			//noinspection HardCodedStringLiteral
			if("-classpath".equals(arg) || "-cp".equals(arg) || "-bootclasspath".equals(arg) || "--module-path".equals(arg))
			{
				final String cpValue = args[idx + 1];
				if(cpValue.startsWith("@"))
				{
					args[idx + 1] = readClassPathWithAt(cpValue);
				}
			}
		}
	}

	public static String readClassPathWithAt(String filePath) throws IOException
	{
		Vector elements = new Vector();

		addFilesToCompile(elements, filePath);

		StringBuffer builder = new StringBuffer();

		for(int i = 0; i < elements.size(); i++)
		{
			if(i != 0)
			{
				builder.append(File.pathSeparatorChar);
			}

			builder.append(elements.get(i));
		}
		return builder.toString();
	}
}
