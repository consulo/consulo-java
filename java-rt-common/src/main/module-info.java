module consulo.java.rt.common
{
	requires org.apache.thrift;
	requires org.slf4j;

	exports com.intellij.rt.execution.junit.segments;
	exports consulo.java.rt;
	exports consulo.java.rt.common.compiler;
	exports consulo.java.rt.compiler;
	exports consulo.java.rt.execution.application;
	exports consulo.java.rt.execution.junit.segments;
}