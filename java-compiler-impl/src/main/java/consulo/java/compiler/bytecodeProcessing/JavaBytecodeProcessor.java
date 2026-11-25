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

import com.intellij.compiler.instrumentation.InstrumentationClassFinder;
import com.intellij.java.compiler.impl.cache.Cache;
import com.intellij.java.compiler.impl.cache.JavaDependencyCache;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.compiler.CacheCorruptedException;
import consulo.compiler.CompileContext;
import consulo.module.Module;
import consulo.util.lang.function.ThrowableSupplier;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author VISTALL
 * @since 2016-09-28
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JavaBytecodeProcessor {
    byte @Nullable [] processClassFile(
        CompileContext compileContext,
        Module affectedModule,
        JavaDependencyCache dependencyCache,
        Cache newClassesCache,
        int classId,
        File file,
        ThrowableSupplier<byte[], IOException> bytesSupplier,
        InstrumentationClassFinder classFinder
    ) throws IOException, CacheCorruptedException;
}
