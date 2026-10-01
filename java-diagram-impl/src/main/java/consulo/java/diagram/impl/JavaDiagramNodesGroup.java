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
package consulo.java.diagram.impl;

import consulo.diagram.DiagramNodesGroup;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-10-01
 */
public class JavaDiagramNodesGroup extends DiagramNodesGroup {
    private final String myPackageName;
    private boolean myClosed;

    public JavaDiagramNodesGroup(String packageName) {
        myPackageName = packageName;
    }

    @Override
    public String getGroupName() {
        return myPackageName.isEmpty() ? "<default>" : myPackageName;
    }

    @Override
    public @Nullable DiagramNodesGroup getParent() {
        return null;
    }

    @Override
    public boolean isClosed() {
        return myClosed;
    }

    @Override
    public void setClosed(boolean closed) {
        myClosed = closed;
    }
}
