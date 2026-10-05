/**
 * Java Flight Recorder without the JDK's {@code jdk.jfr} and {@code jdk.management.jfr} modules. Only the
 * {@code consulo.java.profiler.jfr} API is exported; it is implemented with async-profiler's JFR reader (Apache 2.0)
 * and Microsoft's jfr-streaming (MIT), kept in their original packages. See this module's NOTICE for the sources.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
module consulo.java.profiler.jfr {
    requires static consulo.annotation;

    requires java.management;

    exports consulo.java.profiler.jfr;
}
