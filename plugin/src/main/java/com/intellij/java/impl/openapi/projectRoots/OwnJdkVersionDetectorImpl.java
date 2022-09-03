// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.openapi.projectRoots;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.java.language.projectRoots.OwnJdkVersionDetector;
import jakarta.inject.Singleton;

import consulo.application.Application;
import consulo.logging.Logger;
import consulo.application.util.SystemInfo;
import consulo.util.lang.StringUtil;
import consulo.util.io.CharsetToolkit;
import com.intellij.java.language.util.Bitness;
import consulo.process.io.BaseOutputReader;
import consulo.application.util.JavaVersion;

/**
 * @author nik
 */
@Singleton
public class OwnJdkVersionDetectorImpl extends OwnJdkVersionDetector
{
	private static final Logger LOG = Logger.getInstance(OwnJdkVersionDetectorImpl.class);

	@Nullable
	@Override
	public JdkVersionInfo detectJdkVersionInfo(@Nonnull String homePath)
	{
		// Java 1.7+
		File releaseFile = new File(homePath, "release");
		if(releaseFile.isFile())
		{
			Properties p = new Properties();
			try (FileInputStream stream = new FileInputStream(releaseFile))
			{
				p.load(stream);
				String versionString = p.getProperty("JAVA_FULL_VERSION", p.getProperty("JAVA_VERSION"));
				if(versionString != null)
				{
					JavaVersion version = JavaVersion.parse(versionString);
					String arch = StringUtil.unquoteString(p.getProperty("OS_ARCH", ""));
					boolean x64 = "x86_64".equals(arch) || "amd64".equals(arch);
					return new JdkVersionInfo(version, x64 ? Bitness.x64 : Bitness.x32);
				}
			}
			catch(IOException | IllegalArgumentException e)
			{
				LOG.info(releaseFile.getPath(), e);
			}
		}

		// Java 1.2 - 1.8
		File rtFile = new File(homePath, "jre/lib/rt.jar");
		if(rtFile.isFile())
		{
			try (JarFile rtJar = new JarFile(rtFile, false))
			{
				Manifest manifest = rtJar.getManifest();
				if(manifest != null)
				{
					String versionString = manifest.getMainAttributes().getValue("Implementation-Version");
					if(versionString != null)
					{
						JavaVersion version = JavaVersion.parse(versionString);
						boolean x64 = SystemInfo.isMac || new File(rtFile.getParent(), "amd64").isDirectory();
						return new JdkVersionInfo(version, x64 ? Bitness.x64 : Bitness.x32);
					}
				}
			}
			catch(IOException | IllegalArgumentException e)
			{
				LOG.info(rtFile.getPath(), e);
			}
		}

		// last resort
		File javaExe = new File(homePath, "bin/" + (SystemInfo.isWindows ? "java.exe" : "java"));
		if(javaExe.canExecute())
		{
			try
			{
				Process process = new ProcessBuilder(javaExe.getPath(), "-version").redirectErrorStream(true).start();
				VersionOutputReader reader = new VersionOutputReader(process.getInputStream());
				try
				{
					reader.waitFor();
				}
				catch(InterruptedException e)
				{
					LOG.info(e);
					process.destroy();
				}

				if(!reader.myLines.isEmpty())
				{
					JavaVersion base = JavaVersion.parse(reader.myLines.get(0));
					JavaVersion rt = JavaVersion.tryParse(reader.myLines.size() > 2 ? reader.myLines.get(1) : null);
					JavaVersion version = rt != null && rt.feature == base.feature && rt.minor == base.minor ? rt : base;
					boolean x64 = reader.myLines.stream().anyMatch(s -> s.contains("64-Bit") || s.contains("x86_64") || s.contains("amd64"));
					return new JdkVersionInfo(version, x64 ? Bitness.x64 : Bitness.x32);
				}
			}
			catch(IOException | IllegalArgumentException e)
			{
				LOG.info(javaExe.getPath(), e);
			}
		}

		return null;
	}

	private static class VersionOutputReader extends BaseOutputReader
	{
		private static final BaseOutputReader.Options OPTIONS = new BaseOutputReader.Options()
		{
			@Override
			public SleepingPolicy policy()
			{
				return SleepingPolicy.BLOCKING;
			}

			@Override
			public boolean splitToLines()
			{
				return true;
			}

			@Override
			public boolean sendIncompleteLines()
			{
				return false;
			}

			@Override
			public boolean withSeparators()
			{
				return false;
			}
		};

		private final List<String> myLines;

		VersionOutputReader(@Nonnull InputStream stream)
		{
			super(stream, CharsetToolkit.getDefaultSystemCharset(), OPTIONS);
			myLines = new CopyOnWriteArrayList<>();
			start("java -version");
		}

		@Nonnull
		@Override
		protected Future<?> executeOnPooledThread(@Nonnull Runnable runnable)
		{
			return Application.get().executeOnPooledThread(runnable);
		}

		@Override
		protected void onTextAvailable(@Nonnull String text)
		{
			myLines.add(text);
			LOG.trace("text: " + text);
		}
	}
}