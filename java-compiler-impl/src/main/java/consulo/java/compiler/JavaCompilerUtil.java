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

import java.util.Collections;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import consulo.java.module.extension.JavaModuleExtension;
import com.intellij.compiler.impl.ModuleChunk;
import com.intellij.compiler.impl.javaCompiler.JavaCompilerConfiguration;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import consulo.annotation.access.RequiredReadAction;

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


	//todo[nik] rewrite using JavaSdkVersion#getMaxLanguageLevel
	@Nonnull
	public static LanguageLevel getApplicableLanguageLevel(String versionString, @Nonnull LanguageLevel languageLevel)
	{
		final boolean is9OrNewer = isOfVersion(versionString, "1.9") || isOfVersion(versionString, "9.0");
		final boolean is8OrNewer = isOfVersion(versionString, "1.8") || isOfVersion(versionString, "8.0");
		final boolean is7OrNewer = is8OrNewer || isOfVersion(versionString, "1.7") || isOfVersion(versionString, "7.0");
		final boolean is6OrNewer = is7OrNewer || isOfVersion(versionString, "1.6") || isOfVersion(versionString, "6.0");
		final boolean is5OrNewer = is6OrNewer || isOfVersion(versionString, "1.5") || isOfVersion(versionString, "5.0");
		final boolean is4OrNewer = is5OrNewer || isOfVersion(versionString, "1.4");
		final boolean is3OrNewer = is4OrNewer || isOfVersion(versionString, "1.3");
		final boolean is2OrNewer = is3OrNewer || isOfVersion(versionString, "1.2");
		final boolean is1OrNewer = is2OrNewer || isOfVersion(versionString, "1.0") || isOfVersion(versionString, "1.1");

		if(!is1OrNewer)
		{
			// unknown jdk version, cannot say anything about the corresponding language level, so leave it unchanged
			return languageLevel;
		}
		// now correct the language level to be not higher than jdk used to compile
		if(LanguageLevel.JDK_1_9.equals(languageLevel) && !is9OrNewer)
		{
			languageLevel = LanguageLevel.JDK_1_8;
		}
		if(LanguageLevel.JDK_1_8.equals(languageLevel) && !is8OrNewer)
		{
			languageLevel = LanguageLevel.JDK_1_7;
		}
		if(LanguageLevel.JDK_1_7.equals(languageLevel) && !is7OrNewer)
		{
			languageLevel = LanguageLevel.JDK_1_6;
		}
		if(LanguageLevel.JDK_1_6.equals(languageLevel) && !is6OrNewer)
		{
			languageLevel = LanguageLevel.JDK_1_5;
		}
		if(LanguageLevel.JDK_1_5.equals(languageLevel) && !is5OrNewer)
		{
			languageLevel = LanguageLevel.JDK_1_4;
		}
		if(LanguageLevel.JDK_1_4.equals(languageLevel) && !is4OrNewer)
		{
			languageLevel = LanguageLevel.JDK_1_3;
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
