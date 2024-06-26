/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.ui.impl.watch;

import java.util.Collection;
import java.util.function.Function;

import jakarta.annotation.Nonnull;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.java.compiler.ClassObject;
import consulo.project.Project;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.LanguageLevel;
import consulo.language.psi.PsiCodeFragment;
import consulo.language.psi.PsiElement;
import consulo.util.lang.SystemProperties;
import jakarta.annotation.Nullable;

// TODO [VISTALL] disabled
// todo: consider batching compilations in order not to start a separate process for every class that needs to be compiled
public class CompilingEvaluatorImpl extends CompilingEvaluator
{
	private static final boolean DEBUGGER_COMPILING_EVALUATOR = SystemProperties.getBooleanProperty("debugger.compiling.evaluator", false);

	private Collection<ClassObject> myCompiledClasses;

	/*public CompilingEvaluatorImpl(@NotNull Project project, @NotNull PsiElement context, @NotNull ExtractLightMethodObjectHandler.ExtractedData data)
	{
		super(project, context, data);
	}  */

	@Override
	@Nonnull
	protected Collection<ClassObject> compile(@Nullable JavaSdkVersion debuggeeVersion) throws EvaluateException
	{
		if(myCompiledClasses == null)
		{
			/*Module module = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(myPsiContext));
			List<String> options = new ArrayList<>();
			options.add("-encoding");
			options.add("UTF-8");
			List<File> platformClasspath = new ArrayList<>();
			List<File> classpath = new ArrayList<>();
			AnnotationProcessingConfiguration profile = null;
			if(module != null)
			{
				assert myProject.equals(module.getProject()) : module + " is from another project";
				profile = CompilerConfiguration.getInstance(myProject).getAnnotationProcessingConfiguration(module);
				ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
				for(String s : rootManager.orderEntries().compileOnly().recursively().exportedOnly().withoutSdk().getPathsList().getPathList())
				{
					classpath.add(new File(s));
				}
				for(String s : rootManager.orderEntries().compileOnly().sdkOnly().getPathsList().getPathList())
				{
					platformClasspath.add(new File(s));
				}
			}
			JavaBuilder.addAnnotationProcessingOptions(options, profile);

			Pair<Sdk, JavaSdkVersion> runtime = BuildManager.getJavacRuntimeSdk(myProject);
			JavaSdkVersion buildRuntimeVersion = runtime.getSecond();
			// if compiler or debuggee version or both are unknown, let source and target be the compiler's defaults
			if(buildRuntimeVersion != null && debuggeeVersion != null)
			{
				JavaSdkVersion minVersion = buildRuntimeVersion.ordinal() > debuggeeVersion.ordinal() ? debuggeeVersion : buildRuntimeVersion;
				String sourceOption = getSourceOption(minVersion.getMaxLanguageLevel());
				options.add("-source");
				options.add(sourceOption);
				options.add("-target");
				options.add(sourceOption);
			}

			CompilerManager compilerManager = CompilerManager.getInstance(myProject);

			File sourceFile = null;
			try
			{
				sourceFile = generateTempSourceFile(compilerManager.getJavacCompilerWorkingDir());
				File srcDir = sourceFile.getParentFile();
				List<File> sourcePath = Collections.emptyList();
				Set<File> sources = Collections.singleton(sourceFile);

				myCompiledClasses = compilerManager.compileJavaCode(options, platformClasspath, classpath, Collections.emptyList(), sourcePath, sources, srcDir);
			}
			catch(CompilationException e)
			{
				StringBuilder res = new StringBuilder("Compilation failed:\n");
				for(CompilationException.Message m : e.getMessages())
				{
					if(m.getCategory() == CompilerMessageCategory.ERROR)
					{
						res.append(m.getText()).append("\n");
					}
				}
				throw new EvaluateException(res.toString());
			}
			catch(Exception e)
			{
				throw new EvaluateException(e.getMessage());
			}
			finally
			{
				if(sourceFile != null)
				{
					FileUtil.delete(sourceFile);
				}
			} */
		}
		return myCompiledClasses;
	}

	@Nonnull
	private static String getSourceOption(@Nonnull LanguageLevel languageLevel)
	{
		return "1." + Integer.valueOf(3 + languageLevel.ordinal());
	}

	/*private File generateTempSourceFile(File workingDir) throws IOException
	{
		Pair<String, String> fileData = ReadAction.compute(() ->
		{
			PsiFile file = myData.getGeneratedInnerClass().getContainingFile();
			return Pair.create(file.getName(), file.getText());
		});
		if(fileData.first == null)
		{
			throw new IOException("Class file name not specified");
		}
		if(fileData.second == null)
		{
			throw new IOException("Class source code not specified");
		}
		File file = new File(workingDir, "debugger/src/" + fileData.first);
		FileUtil.writeToFile(file, fileData.second);
		return file;
	} */

	@Nullable
	public static ExpressionEvaluator create(@Nonnull Project project, @Nullable PsiElement psiContext, @Nonnull Function<PsiElement, PsiCodeFragment> fragmentFactory) throws EvaluateException
	{
		/*if(DEBUGGER_COMPILING_EVALUATOR && psiContext != null)
		{
			return ApplicationManager.getApplication().runReadAction((ThrowableComputable<ExpressionEvaluator, EvaluateException>) () ->
			{
				try
				{
					ExtractLightMethodObjectHandler.ExtractedData data = ExtractLightMethodObjectHandler.extractLightMethodObject(project, findPhysicalContext(psiContext), fragmentFactory.apply
							(psiContext), getGeneratedClassName());
					if(data != null)
					{
						return new CompilingEvaluatorImpl(project, psiContext, data);
					}
				}
				catch(PrepareFailedException e)
				{
					NodeDescriptorImpl.LOG.info(e);
				}
				return null;
			});
		}   */
		return null;
	}

	@Nonnull
	private static PsiElement findPhysicalContext(@Nonnull PsiElement element)
	{
		while(!element.isPhysical())
		{
			PsiElement context = element.getContext();
			if(context == null)
			{
				break;
			}
			element = context;
		}
		return element;
	}
}
