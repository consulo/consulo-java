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
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.debugger.impl.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.java.debugger.impl.jdi.ThreadReferenceProxyImpl;
import consulo.execution.debug.frame.XExecutionStack;
import consulo.execution.debug.frame.XExecutionStackGroup;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A thread group of the debugged VM in the Threads view.
 *
 * @author VISTALL
 * @since 2026-11-17
 */
public class JavaThreadGroup extends XExecutionStackGroup {
    private final List<XExecutionStackGroup> myGroups;
    private final List<XExecutionStack> myStacks;

    private JavaThreadGroup(String name, List<XExecutionStackGroup> groups, List<XExecutionStack> stacks) {
        super(name);
        myGroups = groups;
        myStacks = stacks;
    }

    @Override
    public Image getIcon() {
        return ExecutionDebugIconGroup.threadThreadgroup();
    }

    @Override
    public List<XExecutionStack> getStacks() {
        return myStacks;
    }

    @Override
    public List<XExecutionStackGroup> getGroups() {
        return myGroups;
    }

    /**
     * All the groups are loaded at once - the VM has few of them.
     */
    public static CompletableFuture<JavaThreadGroup> build(ThreadGroupReferenceProxyImpl group,
                                                           DebugProcessImpl debugProcess,
                                                           @Nullable ThreadReferenceProxyImpl currentThread) {
        String name = group.name();
        List<CompletableFuture<JavaThreadGroup>> subGroups = new ArrayList<>();
        for (ThreadGroupReferenceProxyImpl subGroup : group.threadGroups()) {
            subGroups.add(build(subGroup, debugProcess, currentThread));
        }
        List<CompletableFuture<@Nullable JavaExecutionStack>> stacks = new ArrayList<>();
        for (ThreadReferenceProxyImpl thread : group.threads()) {
            stacks.add(JavaExecutionStack.create(thread, debugProcess, thread.equals(currentThread)));
        }

        List<CompletableFuture<?>> all = new ArrayList<>(subGroups);
        all.addAll(stacks);
        return CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).thenApply(unused -> {
            List<XExecutionStackGroup> groups = new ArrayList<>(subGroups.size());
            for (CompletableFuture<JavaThreadGroup> subGroup : subGroups) {
                groups.add(subGroup.join());
            }
            List<XExecutionStack> executionStacks = new ArrayList<>(stacks.size());
            for (CompletableFuture<@Nullable JavaExecutionStack> stack : stacks) {
                JavaExecutionStack executionStack = stack.join();
                if (executionStack != null) {
                    executionStacks.add(executionStack);
                }
            }
            return new JavaThreadGroup(name == null ? "" : name, groups, executionStacks);
        });
    }
}
