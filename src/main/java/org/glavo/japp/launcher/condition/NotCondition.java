package org.glavo.japp.launcher.condition;

public class NotCondition implements Condition {
    private final Condition original;

    public NotCondition(Condition original) {
        this.original = original;
    }

    @Override
    public boolean test(JAppRuntimeContext context) {
        return !original.test(context);
    }

    @Override
    public String toString() {
        return "not(" + original + ')';
    }
}
