/*
 * Copyright 2013-2018 consulo.io
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

package consulo.java.rt;

/**
 * @author VISTALL
 * @since 2018-04-26
 */
public interface JavaRtClassNames
{
	String JAVAC_RUNNER = "com.intellij.rt.compiler.JavacRunner";

	String BATCH_EVALUATOR_SERVER = "com.intellij.rt.debugger.BatchEvaluatorServer";

	String IMAGE_SERIALIZER = "com.intellij.rt.debugger.ImageSerializer";

	String DEFAULT_METHOD_INVOKER = "com.intellij.rt.debugger.DefaultMethodInvoker";

	String APP_MAINV2 = "com.intellij.rt.execution.application.AppMainV2";
}
