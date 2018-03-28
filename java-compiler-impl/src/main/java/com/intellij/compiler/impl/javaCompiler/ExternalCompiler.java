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
package com.intellij.compiler.impl.javaCompiler;

import java.io.IOException;

import javax.annotation.Nonnull;
import com.intellij.compiler.impl.ModuleChunk;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.EnvironmentUtil;

public abstract class ExternalCompiler implements BackendCompiler
{
	private static final Logger LOG = Logger.getInstance(ExternalCompiler.class);

	@Nonnull
	public abstract GeneralCommandLine createStartupCommand(ModuleChunk chunk, CompileContext context, String outputPath) throws IOException, IllegalArgumentException;

	@Override
	@Nonnull
	public Process launchProcess(@Nonnull final ModuleChunk chunk, @Nonnull final String outputDir, @Nonnull final CompileContext compileContext) throws IOException
	{
		final GeneralCommandLine commandLine = createStartupCommand(chunk, compileContext, outputDir);

		StringBuilder buf = new StringBuilder();
		buf.append("\n===================================Environment:===========================\n");
		for(String pair : EnvironmentUtil.getEnvironment())
		{
			buf.append("\t").append(pair).append("\n");
		}
		buf.append("=============================================================================\n");
		buf.append("Running compiler: ").append(commandLine);

		if(LOG.isDebugEnabled())
		{
			LOG.debug(buf.toString());
		}

		try
		{
			return commandLine.createProcess();
		}
		catch(ExecutionException e)
		{
			throw new IOException(e);
		}
	}
}
