package com.hhovhann.brain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

class ParallelTest {

    @Test
    void scopedValueReachesEveryBranchOnAVirtualThread() throws Exception {
        List<String> seen = ScopedValue.where(Doctor.READER, "pm").call(() -> Parallel.all(List.of(
                () -> Doctor.READER.get() + ":" + Thread.currentThread().isVirtual(),
                () -> Doctor.READER.get() + ":" + Thread.currentThread().isVirtual())));

        assertThat(seen).containsExactly("pm:true", "pm:true");
    }

    @Test
    void readerIsUnboundOutsideTheScope() {
        // Fails closed: code that needs a reader and has none must not run as "nobody".
        assertThat(Doctor.READER.isBound()).isFalse();
        assertThatThrownBy(Doctor.READER::get).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void oneFailingBranchFailsTheWholeCall() {
        assertThatThrownBy(() -> Parallel.all(List.<Callable<String>>of(
                        () -> "fine",
                        () -> {
                            throw new IllegalStateException("boom");
                        })))
                .hasStackTraceContaining("boom");
    }

    @Test
    void resultsComeBackInTaskOrder() throws Exception {
        assertThat(Parallel.all(List.of(() -> "a", () -> "b", () -> "c"))).containsExactly("a", "b", "c");
    }
}
