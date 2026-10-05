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
package consulo.java.profiler.impl.internal.jvm;

import consulo.execution.profiler.AttachableTargetProcess;

/**
 * @author VISTALL
 * @since 2026-10-05
 */
public final class JvmTargetProcess implements AttachableTargetProcess {
    private final String myFullName;
    private final int myPid;

    public JvmTargetProcess(String fullName, int pid) {
        myFullName = fullName;
        myPid = pid;
    }

    @Override
    public String getFullName() {
        return myFullName;
    }

    @Override
    public int getPid() {
        return myPid;
    }

    @Override
    public String toString() {
        return myFullName;
    }
}
