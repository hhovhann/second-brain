package com.hhovhann.brain;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

/**
 * The only place that touches {@link StructuredTaskScope}.
 *
 * <p>It is a preview API in Java 27 (JEP 533, seventh preview) and its signature moved
 * again in that release. Keeping every use behind this one method means the day it
 * changes — or becomes final in 28 — one file is edited, not the codebase.
 *
 * <p>Fork inherits scoped values, so a reader bound with {@link ScopedValue} before the
 * call is the same reader inside every branch. That is what lets retrieval run its
 * vector and keyword searches in parallel without the principal ever being an argument.
 */
public final class Parallel {

    private Parallel() {}

    /** Runs every task on its own virtual thread; fails fast and cancels the rest if one throws. */
    public static <T> List<T> all(List<Callable<T>> tasks) throws Exception {
        try (var scope = StructuredTaskScope.open()) {
            var subtasks = tasks.stream().map(scope::fork).toList();
            scope.join();
            return subtasks.stream().map(StructuredTaskScope.Subtask::get).toList();
        }
    }
}
