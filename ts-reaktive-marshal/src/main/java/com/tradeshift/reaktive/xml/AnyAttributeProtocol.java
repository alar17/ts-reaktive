package com.tradeshift.reaktive.xml;


import static com.tradeshift.reaktive.marshal.ReadProtocol.none;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

import com.tradeshift.reaktive.marshal.Protocol;
import com.tradeshift.reaktive.marshal.Reader;
import com.tradeshift.reaktive.marshal.Writer;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Vector;
import io.vavr.control.Try;

/**
 * Handles reading and writing a single attribute of a tag, matching any name
 */
public class AnyAttributeProtocol implements Protocol<XMLEvent,Tuple2<QName,String>> {
    public static final AnyAttributeProtocol INSTANCE = new AnyAttributeProtocol();
    
    private static final XMLEventFactory factory = XMLEventFactory.newFactory();
    
    private final Writer<XMLEvent,Tuple2<QName,String>> writer;

    private AnyAttributeProtocol() {
        this.writer = Writer.of(t -> Vector.of(factory.createAttribute(t._1(), t._2())));
    }
    
    @Override
    public Class<? extends XMLEvent> getEventType() {
        return Attribute.class;
    }
    
    @Override
    public Reader<XMLEvent,Tuple2<QName,String>> reader() {
        return new Reader<XMLEvent,Tuple2<QName,String>>() {
            private int level = 0;

            @Override
            public Try<Tuple2<QName,String>> reset() {
                level = 0;
                return none();
            }

            @Override
            public Try<Tuple2<QName,String>> apply(XMLEvent evt) {
                if (level == 0 && evt.isAttribute()) {
                    Attribute attr = Attribute.class.cast(evt);
                    return Try.success(Tuple.of(attr.getName(), attr.getValue()));
                } else if (evt.isStartElement()) {
                    level++;
                    return none();
                } else if (evt.isEndElement()) {
                    level--;
                    return none();
                } else {
                    return none();
                }
            }
        };
    }
    
    @Override
    public Writer<XMLEvent,Tuple2<QName,String>> writer() {
        return writer;
    }
}
