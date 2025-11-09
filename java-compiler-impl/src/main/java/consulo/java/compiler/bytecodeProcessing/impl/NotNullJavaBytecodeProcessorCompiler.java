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

import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.instrumentation.InstrumenterClassWriter;
import com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter;
import com.intellij.java.compiler.impl.cache.Cache;
import com.intellij.java.compiler.impl.cache.JavaDependencyCache;
import com.intellij.java.compiler.impl.javaCompiler.JavaCompilerConfiguration;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.ThrowableComputable;
import consulo.compiler.CacheCorruptedException;
import consulo.compiler.CompileContext;
import consulo.content.bundle.Sdk;
import consulo.internal.org.objectweb.asm.ClassWriter;
import consulo.java.compiler.JavaCompilerUtil;
import consulo.java.compiler.bytecodeProcessing.JavaBytecodeProcessor;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.module.Module;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author VISTALL
 * @since 28-Sep-16
 */
@ExtensionImpl
public class NotNullJavaBytecodeProcessorCompiler implements JavaBytecodeProcessor
{
	@Nullable
	@Override
	public byte[] processClassFile(CompileContext compileContext,
			Module affectedModule,
			JavaDependencyCache dependencyCache,
			Cache newClassesCache,
			int classId,
			File file,
			ThrowableComputable<byte[], IOException> bytesComputable,
			InstrumentationClassFinder classFinder) throws IOException, CacheCorruptedException
	{
		Sdk jdk = JavaCompilerUtil.getSdkForCompilation(affectedModule);

		boolean isJdk6 = jdk != null && JavaSdkTypeUtil.isOfVersionOrHigher(jdk, JavaSdkVersion.JDK_1_6);

		boolean addNotNullAssertions = JavaCompilerConfiguration.getInstance(affectedModule.getProject()).isAddNotNullAssertions();

		if(!addNotNullAssertions)
		{
			return null;
		}

		byte[] bytes = bytesComputable.compute();
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
