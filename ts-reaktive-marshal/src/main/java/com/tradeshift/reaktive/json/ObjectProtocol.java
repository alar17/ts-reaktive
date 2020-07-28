package com.tradeshift.reaktive.json;

import java.util.List;
import java.util.function.Function;

import com.tradeshift.reaktive.marshal.Protocol;
import com.tradeshift.reaktive.marshal.Reader;
import com.tradeshift.reaktive.marshal.Writer;

import io.vavr.Function1;
import io.vavr.collection.Vector;

/**
 * Generic class to combine several nested FieldProtocols into reading/writing a Java object instance.
 */
public class ObjectProtocol<T> implements Protocol<JSONEvent, T> {
    private final ObjectReadProtocol<T> read;
    private final ObjectWriteProtocol<T> write;

    public ObjectProtocol(Protocol<JSONEvent, T> inner) {
        this.read = new ObjectReadProtocol<>(inner);
        this.write = new ObjectWriteProtocol<>(inner);
    }
    
    public ObjectProtocol(
        List<Protocol<JSONEvent, ?>> protocols,
        Function<List<?>, T> produce,
        List<Function1<T, ?>> getters
    ) {
        this.read = new ObjectReadProtocol<>(Vector.ofAll(protocols), produce, Vector.empty());
        this.write = new ObjectWriteProtocol<>(Vector.ofAll(protocols), getters, Vector.empty());
    }
    
    private ObjectProtocol(ObjectReadProtocol<T> read, ObjectWriteProtocol<T> write) {
        this.read = read;
        this.write = write;
    }

    @Override
    public Reader<JSONEvent, T> reader() {
        return read.reader();
    }

    @Override
    public Writer<JSONEvent, T> writer() {
        return write.writer();
    }
    
    @Override
    public String toString() {
        return read.toString();
    }
    
    @Override
    public Class<? extends JSONEvent> getEventType() {
        return write.getEventType();
    }
    
    /**
     * Returns a new protocol that, in addition, also requires the given nested protocol to be present with the given constant value,
     * writing out the value when serializing as well.
     */
    public <U> ObjectProtocol<T> having(Protocol<JSONEvent, U> nestedProtocol, U value) {
        return new ObjectProtocol<>(read.having(nestedProtocol, value), write.having(nestedProtocol, value));
    }
    
}