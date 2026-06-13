package com.urbansidequest.backend.handler.route.constraint;

public record ConstraintResult(boolean passed, String reason) {

    public static ConstraintResult success() {
        return new ConstraintResult(true, null);
    }

    public static ConstraintResult failed(String reason) {
        return new ConstraintResult(false, reason);
    }
}
