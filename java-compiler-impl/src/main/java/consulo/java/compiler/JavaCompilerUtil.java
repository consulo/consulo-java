/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.java.compiler;

import com.intellij.java.compiler.impl.javaCompiler.JavaCompilerConfiguration;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.module.EffectiveLanguageLevelUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.annotation.access.RequiredReadAction;
import consulo.compiler.CompileContext;
import consulo.compiler.CompilerBundle;
import consulo.compiler.ModuleChunk;
import consulo.content.bundle.Sdk;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.process.cmd.ParametersList;
import consulo.util.io.CharsetToolkit;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

/**
 * @author VISTALL
 * @since 0:37/26.05.13
 * <p>
 * This class is split part of {com.intellij.compiler.impl.CompilerUtil}
 */
public class JavaCompilerUtil
{
	@RequiredReadAction
	public static void addTargetCommandLineSwitch(final ModuleChunk chunk, final ParametersList parametersList)
	{
		String optionValue = null;

		JavaCompilerConfiguration compilerConfiguration = JavaCompilerConfiguration.getInstance(chunk.getProject());
		final Module[] modules = chunk.getModules();
		for(Module module : modules)
		{
			final String moduleTarget = compilerConfiguration.getBytecodeTargetLevel(module);
			if(moduleTarget == null)
			{
				continue;
			}
			if(optionValue == null)
			{
				optionValue = moduleTarget;
			}
			else
			{
				if(moduleTarget.compareTo(optionValue) < 0)
				{
					optionValue = moduleTarget; // use the lower possible target among modules that form the chunk
				}
			}
		}
		if(optionValue != null)
		{
			parametersList.add("-target");
			parametersList.add(optionValue);
		}
	}

	public static void addSourceCommandLineSwitch(final Sdk jdk, LanguageLevel chunkLanguageLevel, @NonNls final ParametersList parametersList)
	{
		final String versionString = jdk.getVersionString();
		if(StringUtil.isEmpty(versionString))
		{
			throw new IllegalArgumentException(CompilerBundle.message("javac.error.unknown.jdk.version", jdk.getName()));
		}

		final LanguageLevel applicableLanguageLevel = getApplicableLanguageLevel(versionString, chunkLanguageLevel);
		if(applicableLanguageLevel.equals(LanguageLevel.JDK_1_9))
		{
			parametersList.add("-source");
			parametersList.add("9");
		}
		else if(applicableLanguageLevel.equals(LanguageLevel.JDK_1_8))
		{
			parametersList.add("-source");
			parametersList.add("8");
		}
		else if(applicableLanguageLevel.equals(LanguageLevel.JDK_1_7))
		{
			parametersList.add("-source");
			parametersList.add("1.7");
		}
		else if(applicableLanguageLevel.equals(LanguageLevel.JDK_1_6))
		{
			parametersList.add("-source");
			parametersList.add("1.6");
		}
		else if(applicableLanguageLevel.equals(LanguageLevel.JDK_1_5))
		{
			parametersList.add("-source");
			parametersList.add("1.5");
		}
		else if(applicableLanguageLevel.equals(LanguageLevel.JDK_1_4))
		{
			parametersList.add("-source");
			parametersList.add("1.4");
		}
		else if(applicableLanguageLevel.equals(LanguageLevel.JDK_1_3))
		{
			if(!(isOfVersion(versionString, "1.3") || isOfVersion(versionString, "1.2") || isOfVersion(versionString, "1.1")))
			{
				//noinspection HardCodedStringLiteral
				parametersList.add("-source");
				parametersList.add("1.3");
			}
		}
	}

	public static void addLocaleOptions(final ParametersList parametersList, final boolean launcherUsed)
	{
		// need to specify default encoding so that javac outputs messages in 'correct' language
		//noinspection HardCodedStringLiteral
		parametersList.add((launcherUsed ? "-J" : "") + "-D" + CharsetToolkit.FILE_ENCODING_PROPERTY + "=" + CharsetToolkit.getDefaultSystemCharset().name());
		// javac's VM should use the same default locale that IDEA uses in order for javac to print messages in 'correct' language
		//noinspection HardCodedStringLiteral
		final String lang = System.getProperty("user.language");
		if(lang != null)
		{
			//noinspection HardCodedStringLiteral
			parametersList.add((launcherUsed ? "-J" : "") + "-Duser.language=" + lang);
		}
		//noinspection HardCodedStringLiteral
		final String country = System.getProperty("user.country");
		if(country != null)
		{
			//noinspection HardCodedStringLiteral
			parametersList.add((launcherUsed ? "-J" : "") + "-Duser.country=" + country);
		}
		//noinspection HardCodedStringLiteral
		final String region = System.getProperty("user.region");
		if(region != null)
		{
			//noinspection HardCodedStringLiteral
			parametersList.add((launcherUsed ? "-J" : "") + "-Duser.region=" + region);
		}
	}

	@Nonnull
	public static LanguageLevel getApplicableLanguageLevel(String versionString, @Nonnull LanguageLevel languageLevel)
	{
		JavaSdkVersion runtimeVersion = JavaSdkVersion.fromVersionString(versionString);
		if(runtimeVersion == null)
		{
			return languageLevel;
		}

		LanguageLevel runtimeMaxLevel = runtimeVersion.getMaxLanguageLevel();

		if(runtimeMaxLevel.getMajor() < languageLevel.getMajor())
		{
			return LanguageLevel.parse(String.valueOf(runtimeMaxLevel.getMajor() - 1));
		}

		return languageLevel;
	}

	public static boolean isOfVersion(String versionString, String checkedVersion)
	{
		return versionString.contains(checkedVersion);
	}


	@Nullable
	public static Sdk getSdkForCompilation(@Nonnull final Module module)
	{
		JavaModuleExtension extension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
		if(extension == null)
		{
			return null;
		}
		return extension.getSdkForCompilation();
	}

	@Nullable
	public static Sdk getSdkForCompilation(final ModuleChunk chunk)
	{
		return getSdkForCompilation(chunk.getModule());
	}

	@Nonnull
	public static Set<VirtualFile> getCompilationClasspath(@Nonnull CompileContext compileContext, final ModuleChunk moduleChunk)
	{
		JavaModuleExtension<?> extension = ModuleUtilCore.getExtension(moduleChunk.getModule(), JavaModuleExtension.class);
		if(extension == null)
		{
			return Collections.emptySet();
		}
		return extension.getCompilationClasspath(compileContext, moduleChunk);
	}

	@Nonnull
	public static Set<VirtualFile> getCompilationBootClasspath(@Nonnull CompileContext compileContext, final ModuleChunk moduleChunk)
	{
		JavaModuleExtension<?> extension = ModuleUtilCore.getExtension(moduleChunk.getModule(), JavaModuleExtension.class);
		if(extension == null)
		{
			return Collections.emptySet();
		}
		return extension.getCompilationBootClasspath(compileContext, moduleChunk);
	}

	@Nullable
	@RequiredReadAction
	public static LanguageLevel getLanguageLevelForCompilation(final ModuleChunk chunk)
	{
		return EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(chunk.getModule());
	}
}
