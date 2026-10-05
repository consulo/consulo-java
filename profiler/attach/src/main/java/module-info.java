/**
 * Attaching to running JVMs without the JDK's {@code jdk.attach} module: byte-buddy-agent's {@code VirtualMachine}
 * (Apache 2.0) over JNA, in its original package. See this module's NOTICE for the source.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
module consulo.java.profiler.attach {
    requires static consulo.annotation;

    requires com.sun.jna;
    requires com.sun.jna.platform;

    exports net.bytebuddy.agent;

    opens net.bytebuddy.agent to com.sun.jna;
}
