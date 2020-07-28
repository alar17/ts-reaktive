package com.tradeshift.reaktive.json;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tradeshift.reaktive.marshal.ConstantProtocol;
import com.tradeshift.reaktive.marshal.Protocol;
import com.tradeshift.reaktive.marshal.WriteProtocol;
import com.tradeshift.reaktive.marshal.Writer;

import io.vavr.Function1;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;

/**
 * Generic class to combine several nested FieldProtocols into reading/writing a Java object instance.
 */
@SuppressWarnings("unchecked")
public class ObjectWriteProtocol<T> implements WriteProtocol<JSONEvent, T> {
    private static final Logger log = LoggerFactory.getLogger(ObjectWriteProtocol.class);
    
    private final Seq<WriteProtocol<JSONEvent, ?>> protocols;
    private final Seq<WriteProtocol<JSONEvent, ConstantProtocol.Present>> conditions;
    private final List<Function1<T, ?>> getters;

    public ObjectWriteProtocol(
        List<WriteProtocol<JSONEvent, ?>> protocols,
        List<Function1<T, ?>> getters
    ) {
        this(Vector.ofAll(protocols), getters, Vector.empty());
    }
    
    public ObjectWriteProtocol(WriteProtocol<JSONEvent, T> inner) {
        this(Vector.of((WriteProtocol<JSONEvent, ?>)inner), Arrays.asList(Function1.identity()), Vector.empty());
    }
    
    ObjectWriteProtocol(
        Seq<WriteProtocol<JSONEvent, ?>> protocols,
        List<Function1<T, ?>> getters,
        Seq<WriteProtocol<JSONEvent, ConstantProtocol.Present>> conditions
    ) {
        this.protocols = protocols;
        this.getters = getters;
        this.conditions = conditions;
        if (protocols.size() != getters.size()) {
            throw new IllegalArgumentException("protocols must match getters");
        }
    }
    
    @Override
    public Class<? extends JSONEvent> getEventType() {
        return JSONEvent.class;
    }

    @Override
    public Writer<JSONEvent, T> writer() {
        Vector<Writer<JSONEvent, Object>> writers = Vector.range(0, protocols.size()).map(i ->
            (Writer<JSONEvent, Object>) protocols.get(i).writer());

        // If there's more than one sub-writer, we need to reset each sub-writer after each value
        // in order to properly close each JSON field. This also means that each incoming value will
        // always be only 1 JSON object.

        // We don't need to do this when there's only one sub-writer, since then it will be reset when
        // this ObjectWriteProtocol itself is being reset. In this special case, we can then have multiple
        // values contribute to the same JSON object.
        boolean resetAfterValue = writers.size() > 1;

        return new Writer<JSONEvent, T>() {
            boolean started = false;

            @Override
            public Seq<JSONEvent> apply(T value) {
                log.debug("{}: Writing {}", ObjectWriteProtocol.this, value);

                Seq<JSONEvent> events = startObject();
                for (int i = 0; i < protocols.size(); i++) {
                    if (resetAfterValue) {
                        events = events.appendAll(writers.get(i).applyAndReset(getters.get(i).apply(value)));
                    } else {
                        events = events.appendAll(writers.get(i).apply(getters.get(i).apply(value)));
                    }
                }

                started = true;
                return events;
            }

            @Override
            public Seq<JSONEvent> reset() {
                log.debug("{}: Resetting ", ObjectWriteProtocol.this);

                Seq<JSONEvent> events = startObject();
                if (!resetAfterValue) {
                    for (int i = 0; i < protocols.size(); i++) {
                        events = events.appendAll(writers.get(0).reset());
                    }
                }

                events = events.appendAll(conditions.map(c -> c.writer().applyAndReset(ConstantProtocol.PRESENT)).flatMap(Function.identity()));

                started = false;
                return events.append(JSONEvent.END_OBJECT);
            }

            private Seq<JSONEvent> startObject() {
                return (started) ? Vector.empty() : Vector.of(JSONEvent.START_OBJECT);
            }
        };
    }
    
    /**
     * Returns a new protocol that, in addition, writes out the given value when serializing.
     */
    public <U> ObjectWriteProtocol<T> having(WriteProtocol<JSONEvent, U> nestedProtocol, U value) {
        return new ObjectWriteProtocol<>(protocols, getters, conditions.append(ConstantProtocol.write(nestedProtocol, value)));
    }
    
    @Override
    public String toString() {
        String c = conditions.isEmpty() ? "" : ", " + conditions.mkString(", ");
        return "{ " + protocols.mkString(", ") + c + " }";
    }
}
