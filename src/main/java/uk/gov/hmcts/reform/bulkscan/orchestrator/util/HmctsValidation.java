package uk.gov.hmcts.reform.bulkscan.orchestrator.util;

import io.vavr.collection.Seq;
import io.vavr.control.Validation;

import java.util.Objects;

public interface HmctsValidation<E, T> extends Validation<E, T> {

    static <E, T1, T2, T3, T4, T5, T6, T7, T8, T9> Builder9<E, T1, T2, T3, T4, T5, T6, T7, T8, T9> combine(Validation<E, T1> validation1, Validation<E, T2> validation2, Validation<E, T3> validation3, Validation<E, T4> validation4, Validation<E, T5> validation5, Validation<E, T6> validation6, Validation<E, T7> validation7, Validation<E, T8> validation8, Validation<E, T9> validation9) {
        Objects.requireNonNull(validation1, "validation1 is null");
        Objects.requireNonNull(validation2, "validation2 is null");
        Objects.requireNonNull(validation3, "validation3 is null");
        Objects.requireNonNull(validation4, "validation4 is null");
        Objects.requireNonNull(validation5, "validation5 is null");
        Objects.requireNonNull(validation6, "validation6 is null");
        Objects.requireNonNull(validation7, "validation7 is null");
        Objects.requireNonNull(validation8, "validation8 is null");
        Objects.requireNonNull(validation9, "validation9 is null");
        return new Builder9<>(validation1, validation2, validation3, validation4, validation5, validation6, validation7, validation8, validation9);
    }

    final class Builder9<E, T1, T2, T3, T4, T5, T6, T7, T8, T9> {

        private Validation<E, T1> v1;
        private Validation<E, T2> v2;
        private Validation<E, T3> v3;
        private Validation<E, T4> v4;
        private Validation<E, T5> v5;
        private Validation<E, T6> v6;
        private Validation<E, T7> v7;
        private Validation<E, T8> v8;
        private Validation<E, T9> v9;

        private Builder9(Validation<E, T1> v1, Validation<E, T2> v2, Validation<E, T3> v3, Validation<E, T4> v4, Validation<E, T5> v5, Validation<E, T6> v6, Validation<E, T7> v7, Validation<E, T8> v8, Validation<E, T9> v9) {
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
            this.v4 = v4;
            this.v5 = v5;
            this.v6 = v6;
            this.v7 = v7;
            this.v8 = v8;
            this.v9 = v9;
        }

        public <R> Validation<Seq<E>, R> ap(Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, R> f) {
            return v9.ap(v8.ap(v7.ap(v6.ap(v5.ap(v4.ap(v3.ap(v2.ap(v1.ap(Validation.valid(f.curried()))))))))));
        }
    }
}
