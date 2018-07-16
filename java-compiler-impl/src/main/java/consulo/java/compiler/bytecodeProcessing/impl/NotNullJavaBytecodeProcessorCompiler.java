/*
 * Copyright 2013-2016 must-be.org
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

package consulo.java.compiler.bytecodeProcessing.impl;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.compiler.cache.Cache;
import com.intellij.compiler.cache.JavaDependencyCache;
import com.intellij.compiler.impl.javaCompiler.JavaCompilerConfiguration;
import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ArrayUtil;
import consulo.java.compiler.JavaCompilerUtil;
import consulo.java.compiler.bytecodeProcessing.JavaBytecodeProcessor;
import consulo.internal.org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;

/**
 * @author VISTALL
 * @since 28-Sep-16
 */
public class NotNullJavaBytecodeProcessorCompiler implements JavaBytecodeProcessor
{
	@javax.annotation.Nullable
	@Override
	public byte[] processClassFile(CompileContext compileContext,
			Module affectedModule,
			JavaDependencyCache dependencyCache,
			Cache newClassesCache,
			int classId,
			File file,
			ThrowableComputable<byte[], IOException> bytesCompitable,
			InstrumentationClassFinder classFinder) throws IOException, CacheCorruptedException
	{
		Sdk jdk = JavaCompilerUtil.getSdkForCompilation(affectedModule);

		boolean isJdk6 = jdk != null && JavaSdk.getInstance().isOfVersionOrHigher(jdk, JavaSdkVersion.JDK_1_6);

		boolean addNotNullAssertions = JavaCompilerConfiguration.getInstance(affectedModule.getProject()).isAddNotNullAssertions();

		if(!addNotNullAssertions)
		{
			return null;
		}

		byte[] bytes = bytesCompitable.compute();
		FailSafeClassReader reader = new FailSafeClassReader(bytes, 0, bytes.length);

		assert classFinder != null;

		ClassWriter writer = new InstrumenterClassWriter(reader, isJdk6 ? ClassWriter.COMPUTE_FRAMES : ClassWriter.COMPUTE_MAXS, classFinder);

		NullableNotNullManager manager = NullableNotNullManager.getInstance(affectedModule.getProject());

		if(NotNullVerifyingInstrumenter.processClassFile(reader, writer, ArrayUtil.toStringArray(manager.getNotNulls())))
		{
			return writer.toByteArray();
		}

		return null;
	}
}
