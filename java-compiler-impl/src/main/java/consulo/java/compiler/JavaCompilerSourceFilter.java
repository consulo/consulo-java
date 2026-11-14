package consulo.java.compiler;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.module.Module;

import java.nio.file.Path;

/**
 * Lets another compiler take over Java files of a module (e.g. a compiler of a JVM language which compiles mixed
 * Java sources itself), so the Java compiler does not compile them again.
 *
 * @author VISTALL
 * @since 2026-11-14
 */
@ExtensionAPI(ComponentScope.PROJECT)
public interface JavaCompilerSourceFilter {
    /**
     * @return false if the file is compiled by another compiler
     */
    boolean isCompilableByJavaCompiler(Module module, Path file);
}
