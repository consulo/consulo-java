import org.jspecify.annotations.NullMarked;

/**
 * @author VISTALL
 * @since 2026-10-05
 */
@NullMarked
module consulo.java.profiler.impl {
    requires consulo.execution.profiler.api;
    requires consulo.java.language.api;
    requires consulo.java.language.impl;
    requires consulo.java.execution.api;
    requires consulo.java.execution.impl;
    requires consulo.java.profiler.attach;
    requires consulo.java.profiler.jfr;

    requires java.management;

    exports consulo.java.profiler.localize;

    opens consulo.java.profiler.impl.internal.jfr to consulo.util.xml.serializer;
}
