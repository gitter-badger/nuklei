/**
 * Copyright 2007-2015, Kaazing Corporation. All rights reserved.
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
package org.kaazing.nuklei.tcp.internal.acceptor;

import static java.lang.String.format;
import static java.nio.channels.SelectionKey.OP_ACCEPT;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

import org.kaazing.nuklei.Nukleus;
import org.kaazing.nuklei.tcp.internal.Context;
import org.kaazing.nuklei.tcp.internal.conductor.ConductorProxy;
import org.kaazing.nuklei.tcp.internal.layouts.StreamsLayout;
import org.kaazing.nuklei.tcp.internal.reader.ReaderProxy;
import org.kaazing.nuklei.tcp.internal.writer.WriterProxy;

import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.collections.Long2ObjectHashMap;
import uk.co.real_logic.agrona.concurrent.AtomicCounter;
import uk.co.real_logic.agrona.concurrent.OneToOneConcurrentArrayQueue;
import uk.co.real_logic.agrona.concurrent.ringbuffer.RingBuffer;
import uk.co.real_logic.agrona.nio.TransportPoller;

public final class Acceptor extends TransportPoller implements Nukleus, Consumer<AcceptorCommand>
{
    private final ConductorProxy conductorProxy;
    private final ReaderProxy readerProxy;
    private final WriterProxy writerProxy;
    private final OneToOneConcurrentArrayQueue<AcceptorCommand> commandQueue;
    private final Long2ObjectHashMap<AcceptorState> stateByRef;
    private final AtomicCounter acceptedCount;
    private final File streamsDirectory;
    private final int streamsCapacity;

    public Acceptor(Context context)
    {
        this.conductorProxy = new ConductorProxy(context);
        this.readerProxy = new ReaderProxy(context);
        this.writerProxy = new WriterProxy(context);
        this.commandQueue = context.acceptorCommandQueue();
        this.stateByRef = new Long2ObjectHashMap<>();
        this.acceptedCount = context.countersManager().newCounter("accepted");
        this.streamsDirectory = context.streamsDirectory();
        this.streamsCapacity = context.streamsCapacity();
    }

    @Override
    public int process() throws Exception
    {
        int weight = 0;

        selector.selectNow();
        weight += selectedKeySet.forEach(this::processAccept);
        weight += commandQueue.drain(this);

        return weight;
    }

    @Override
    public String name()
    {
        return "acceptor";
    }

    @Override
    public void close()
    {
        stateByRef.values().forEach((state) -> {
            try
            {
                state.channel().close();
                selectNowWithoutProcessing();
            }
            catch (final Exception ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        });

        super.close();
    }

    @Override
    public void accept(AcceptorCommand command)
    {
        command.execute(this);
    }

    public void doBind(
        long correlationId,
        String source,
        long sourceRef,
        String destination,
        InetSocketAddress localAddress)
    {
        final long reference = correlationId;

        AcceptorState oldState = stateByRef.get(reference);
        if (oldState != null)
        {
            conductorProxy.onErrorResponse(correlationId);
        }
        else
        {
            try
            {
                final ServerSocketChannel serverChannel = ServerSocketChannel.open();
                serverChannel.bind(localAddress);
                serverChannel.configureBlocking(false);

                // BIND goes first, so Acceptor owns bidirectional streams mapped file lifecycle
                // TODO: unmap mapped buffer (also cleanup in Context.close())

                StreamsLayout streamsRO = new StreamsLayout.Builder().streamsDirectory(streamsDirectory)
                                                                     .streamsFilename(format("%s.accepts", destination))
                                                                     .streamsCapacity(streamsCapacity)
                                                                     .build();

                RingBuffer inputBuffer = streamsRO.inputBuffer();
                RingBuffer outputBuffer = streamsRO.outputBuffer();

                AcceptorState newState = new AcceptorState(reference, source, sourceRef, destination,
                                                           localAddress, inputBuffer, outputBuffer);

                serverChannel.register(selector, OP_ACCEPT, newState);
                newState.attach(serverChannel);

                stateByRef.put(newState.reference(), newState);

                conductorProxy.onBoundResponse(correlationId, newState.reference());
            }
            catch (IOException e)
            {
                conductorProxy.onErrorResponse(correlationId);
                throw new RuntimeException(e);
            }
        }
    }

    public void doUnbind(
        long correlationId,
        long referenceId)
    {
        final AcceptorState state = stateByRef.remove(referenceId);

        if (state == null)
        {
            conductorProxy.onErrorResponse(correlationId);
        }
        else
        {
            try
            {
                ServerSocketChannel serverChannel = state.channel();
                serverChannel.close();
                selector.selectNow();

                String source = state.source();
                long sourceRef = state.sourceRef();
                String destination = state.destination();
                InetSocketAddress localAddress = state.localAddress();

                conductorProxy.onUnboundResponse(correlationId, source, sourceRef, destination, localAddress);
            }
            catch (IOException e)
            {
                conductorProxy.onErrorResponse(correlationId);
            }
        }
    }

    private int processAccept(SelectionKey selectionKey)
    {
        try
        {
            AcceptorState state = (AcceptorState) selectionKey.attachment();
            ServerSocketChannel serverChannel = state.channel();
            SocketChannel channel = serverChannel.accept();
            long connectionId = acceptedCount.increment();

            readerProxy.doRegister(connectionId, state.reference(), channel, state.inputBuffer());
            writerProxy.doRegister(connectionId, state.reference(), channel, state.outputBuffer());
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return 1;
    }
}
