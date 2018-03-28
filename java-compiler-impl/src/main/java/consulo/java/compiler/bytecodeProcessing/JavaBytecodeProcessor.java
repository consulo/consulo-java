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

package consulo.java.compiler.bytecodeProcessing;

import java.io.File;
import java.io.IOException;

import com.intellij.compiler.cache.Cache;
import com.intellij.compiler.cache.JavaDependencyCache;
import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.ThrowableComputable;
import consulo.extensions.CompositeExtensionPointName;

/**
 * @author VISTALL
 * @since 28-Sep-16
 */
public interface JavaBytecodeProcessor
{
	CompositeExtensionPointName<JavaBytecodeProcessor> EP_NAME = CompositeExtensionPointName.applicationPoint("consulo.java.bytecodeCompilerProcessor", JavaBytecodeProcessor.class);

	@javax.annotation.Nullable
	byte[] processClassFile(CompileContext compileContext,
			Module affectedModule,
			JavaDependencyCache dependencyCache,
			Cache newClassesCache,
			int classId,
			File file,
			ThrowableComputable<byte[], IOException> bytesCompitable,
			InstrumentationClassFinder classFinder) throws IOException, CacheCorruptedException;
}
