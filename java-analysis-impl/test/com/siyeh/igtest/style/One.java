package com.siyeh.igtest.style;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import consulo.localize.LocalizeValue;

public class One extends BaseInspection {

    public BaseInspectionVisitor buildVisitor() {
        return null;
    }

    public LocalizeValue getGroupDisplayName() {
        return LocalizeValue.empty();
    }

    protected String buildErrorString(Object... infos) {
        return null;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.empty();
    }

    private static class Inner {
        private static boolean test() {
            return false;
        }
    }
}
class TestVariableScope {
    public static void main(String[] args) {
        int foo;
        // blah
        if (true) {
            foo = 5;
        }

        doStuff(foo);
    }

    private static void doStuff(int foo) {
    }

}