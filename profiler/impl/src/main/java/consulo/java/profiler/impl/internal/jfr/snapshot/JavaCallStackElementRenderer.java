/*
 * Copyright 2013-2026 consulo.io
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
package consulo.java.profiler.impl.internal.jfr.snapshot;

import consulo.execution.profiler.BaseCallStackElement;
import consulo.execution.profiler.ui.BaseCallStackElementRenderer;
import consulo.execution.profiler.ui.NativeCallStackElementRenderer;
import consulo.ui.TextAttribute;
import consulo.ui.TextItemPresentation;

/**
 * Renders a Java frame as {@code Class.method} followed by its greyed package.
 *
 * @author VISTALL
 * @since 2026-10-05
 */
public final class JavaCallStackElementRenderer implements BaseCallStackElementRenderer {
    public static final JavaCallStackElementRenderer INSTANCE = new JavaCallStackElementRenderer();

    private JavaCallStackElementRenderer() {
    }

    @Override
    public void render(BaseCallStackElement element, TextItemPresentation presentation) {
        if (!(element instanceof JavaCallStackElement javaElement)) {
            NativeCallStackElementRenderer.INSTANCE.render(element, presentation);
            return;
        }

        presentation.append(javaElement.getShortClassName() + "." + javaElement.getMethodName(), TextAttribute.REGULAR);
        String packageName = javaElement.getPackageName();
        if (!packageName.isEmpty()) {
            presentation.append(" " + packageName, TextAttribute.GRAYED);
        }
    }

    @Override
    public String getText(BaseCallStackElement element) {
        if (element instanceof JavaCallStackElement javaElement) {
            return javaElement.getShortClassName() + "." + javaElement.getMethodName();
        }
        return NativeCallStackElementRenderer.INSTANCE.getText(element);
    }
}
