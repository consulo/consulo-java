/*
 * Copyright 2013-2014 must-be.org
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

package com.intellij.openapi.projectRoots.impl;

import java.io.File;
import java.io.FileFilter;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;

/**
 * @author VISTALL
 * @since 21.12.14
 */
public class JavaSdkTypeUtil
{
	public static boolean checkForJdk(final File homePath)
	{
		File binPath = new File(homePath.getAbsolutePath() + File.separator + "bin");
		if(!binPath.exists())
		{
			return false;
		}

		FileFilter fileFilter = new FileFilter()
		{
			@Override

			@SuppressWarnings({"HardCodedStringLiteral"})
			public boolean accept(File f)
			{
				if(f.isDirectory())
				{
					return false;
				}
				return Comparing.strEqual(FileUtil.getNameWithoutExtension(f), "javac") || Comparing.strEqual(FileUtil.getNameWithoutExtension(f),
						"javah");
			}
		};
		File[] children = binPath.listFiles(fileFilter);

		return children != null && children.length >= 2 &&
				checkForRuntime(homePath.getAbsolutePath());
	}

	public static boolean checkForRuntime(final String homePath)
	{
		return new File(new File(new File(homePath, "jre"), "lib"), "rt.jar").exists() ||
				new File(new File(homePath, "lib"), "rt.jar").exists() ||
				new File(homePath, "lib/modules/bootmodules.jimage").exists() || // java 1.9
				new File(homePath, "jmods/java.base.jmod").exists() || // java 1.9 Project Jigsaw
				new File(new File(new File(homePath, ".."), "Classes"), "classes.jar").exists() ||  // Apple JDK
				new File(new File(new File(homePath, "jre"), "lib"), "vm.jar").exists() ||  // IBM JDK
				new File(homePath, "classes").isDirectory();  // custom build
	}
}
