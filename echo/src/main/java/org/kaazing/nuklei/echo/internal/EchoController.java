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
package org.kaazing.nuklei.echo.internal;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.nativeOrder;
import static org.kaazing.nuklei.echo.internal.types.control.Types.TYPE_ID_BOUND_RESPONSE;
import static org.kaazing.nuklei.echo.internal.types.control.Types.TYPE_ID_CAPTURED_RESPONSE;
import static org.kaazing.nuklei.echo.internal.types.control.Types.TYPE_ID_ERROR_RESPONSE;
import static org.kaazing.nuklei.echo.internal.types.control.Types.TYPE_ID_ROUTED_RESPONSE;
import static org.kaazing.nuklei.echo.internal.types.control.Types.TYPE_ID_UNBOUND_RESPONSE;

import java.util.concurrent.CompletableFuture;

import org.kaazing.nuklei.Controller;
import org.kaazing.nuklei.echo.internal.types.control.BindFW;
import org.kaazing.nuklei.echo.internal.types.control.BoundFW;
import org.kaazing.nuklei.echo.internal.types.control.CaptureFW;
import org.kaazing.nuklei.echo.internal.types.control.CapturedFW;
import org.kaazing.nuklei.echo.internal.types.control.ErrorFW;
import org.kaazing.nuklei.echo.internal.types.control.RouteFW;
import org.kaazing.nuklei.echo.internal.types.control.RoutedFW;
import org.kaazing.nuklei.echo.internal.types.control.UnbindFW;
import org.kaazing.nuklei.echo.internal.types.control.UnboundFW;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.collections.Long2ObjectHashMap;
import uk.co.real_logic.agrona.concurrent.AtomicBuffer;
import uk.co.real_logic.agrona.concurrent.AtomicCounter;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.agrona.concurrent.broadcast.BroadcastReceiver;
import uk.co.real_logic.agrona.concurrent.broadcast.CopyBroadcastReceiver;
import uk.co.real_logic.agrona.concurrent.ringbuffer.RingBuffer;

public final class EchoController implements Controller
{
    private static final int MAX_SEND_LENGTH = 1024; // TODO: Configuration and Context

    // TODO: thread-safe flyweights or command queue from public methods
    private final CaptureFW.Builder captureRW = new CaptureFW.Builder();
    private final RouteFW.Builder routeRW = new RouteFW.Builder();
    private final BindFW.Builder bindRW = new BindFW.Builder();
    private final UnbindFW.Builder unbindRW = new UnbindFW.Builder();

    private final ErrorFW errorRO = new ErrorFW();
    private final CapturedFW capturedRO = new CapturedFW();
    private final RoutedFW routedRO = new RoutedFW();
    private final BoundFW boundRO = new BoundFW();
    private final UnboundFW unboundRO = new UnboundFW();

    private final Context context;
    private final AtomicCounter messagesReflected;
    private final AtomicCounter bytesReflected;
    private final RingBuffer conductorCommands;
    private final CopyBroadcastReceiver conductorResponses;
    private final AtomicBuffer atomicBuffer;
    private final Long2ObjectHashMap<CompletableFuture<?>> promisesByCorrelationId;

    public EchoController(Context context)
    {
        this.context = context;
        this.messagesReflected = context.counters().messagesWritten();
        this.bytesReflected = context.counters().bytesWritten();
        this.conductorCommands = context.conductorCommands();
        this.conductorResponses = new CopyBroadcastReceiver(new BroadcastReceiver(context.conductorResponseBuffer()));
        this.atomicBuffer = new UnsafeBuffer(allocateDirect(MAX_SEND_LENGTH).order(nativeOrder()));
        this.promisesByCorrelationId = new Long2ObjectHashMap<>();
    }

    @Override
    public int process()
    {
        int weight = 0;

        weight += conductorResponses.receive(this::handleResponse);

        return weight;
    }

    @Override
    public void close() throws Exception
    {
        context.close();
    }

    @Override
    public Class<EchoController> kind()
    {
        return EchoController.class;
    }

    @Override
    public String name()
    {
        return "echo";
    }

    public AtomicCounter messagesReflected()
    {
        return messagesReflected;
    }

    public AtomicCounter bytesReflected()
    {
        return bytesReflected;
    }

    public EchoStreams streams(String source)
    {
        return new EchoStreams(context, source);
    }

    public CompletableFuture<Void> capture(
        String source)
    {
        final CompletableFuture<Void> promise = new CompletableFuture<Void>();

        long correlationId = conductorCommands.nextCorrelationId();

        CaptureFW captureRO = captureRW.wrap(atomicBuffer, 0, atomicBuffer.capacity())
                                       .correlationId(correlationId)
                                       .source(source)
                                       .build();

        if (!conductorCommands.write(captureRO.typeId(), captureRO.buffer(), captureRO.offset(), captureRO.length()))
        {
            promise.completeExceptionally(new IllegalStateException("unable to offer command"));
        }
        else
        {
            promisesByCorrelationId.put(correlationId, promise);
        }

        return promise;
    }

    public CompletableFuture<Void> route(String destination)
    {
        final CompletableFuture<Void> promise = new CompletableFuture<Void>();

        long correlationId = conductorCommands.nextCorrelationId();

        RouteFW routeRO = routeRW.wrap(atomicBuffer, 0, atomicBuffer.capacity())
                                 .correlationId(correlationId)
                                 .destination(destination)
                                 .build();

        if (!conductorCommands.write(routeRO.typeId(), routeRO.buffer(), routeRO.offset(), routeRO.length()))
        {
            promise.completeExceptionally(new IllegalStateException("unable to offer command"));
        }
        else
        {
            promisesByCorrelationId.put(correlationId, promise);
        }

        return promise;
    }

    public CompletableFuture<Long> bind(
        String source)
    {
        final CompletableFuture<Long> promise = new CompletableFuture<Long>();

        long correlationId = conductorCommands.nextCorrelationId();

        BindFW bindRO = bindRW.wrap(atomicBuffer, 0, atomicBuffer.capacity())
                              .correlationId(correlationId)
                              .source(source)
                              .build();

        if (!conductorCommands.write(bindRO.typeId(), bindRO.buffer(), bindRO.offset(), bindRO.length()))
        {
            promise.completeExceptionally(new IllegalStateException("unable to offer command"));
        }
        else
        {
            promisesByCorrelationId.put(correlationId, promise);
        }

        return promise;
    }

    public CompletableFuture<Void> unbind(
        long referenceId)
    {
        final CompletableFuture<Void> promise = new CompletableFuture<>();

        long correlationId = conductorCommands.nextCorrelationId();

        UnbindFW unbindRO = unbindRW.wrap(atomicBuffer, 0, atomicBuffer.capacity())
                                    .correlationId(correlationId)
                                    .referenceId(referenceId)
                                    .build();

        if (!conductorCommands.write(unbindRO.typeId(), unbindRO.buffer(), unbindRO.offset(), unbindRO.length()))
        {
            promise.completeExceptionally(new IllegalStateException("unable to offer command"));
        }
        else
        {
            promisesByCorrelationId.put(correlationId, promise);
        }

        return promise;
    }

    private int handleResponse(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        switch (msgTypeId)
        {
        case TYPE_ID_ERROR_RESPONSE:
            handleErrorResponse(buffer, index, length);
            break;
        case TYPE_ID_CAPTURED_RESPONSE:
            handleCapturedResponse(buffer, index, length);
            break;
        case TYPE_ID_ROUTED_RESPONSE:
            handleRoutedResponse(buffer, index, length);
            break;
        case TYPE_ID_BOUND_RESPONSE:
            handleBoundResponse(buffer, index, length);
            break;
        case TYPE_ID_UNBOUND_RESPONSE:
            handleUnboundResponse(buffer, index, length);
            break;
        default:
            break;
        }

        return 1;
    }

    private void handleErrorResponse(
        DirectBuffer buffer,
        int index,
        int length)
    {
        errorRO.wrap(buffer, index, length);
        long correlationId = errorRO.correlationId();

        CompletableFuture<?> promise = promisesByCorrelationId.remove(correlationId);
        if (promise != null)
        {
            promise.completeExceptionally(new IllegalStateException().fillInStackTrace());
        }
    }

    private void handleCapturedResponse(
        DirectBuffer buffer,
        int index,
        int length)
    {
        capturedRO.wrap(buffer, index, length);
        long correlationId = capturedRO.correlationId();

        CompletableFuture<?> promise = promisesByCorrelationId.remove(correlationId);
        if (promise != null)
        {
            promise.complete(null);
        }
    }

    private void handleRoutedResponse(
        DirectBuffer buffer,
        int index,
        int length)
    {
        routedRO.wrap(buffer, index, length);
        long correlationId = routedRO.correlationId();

        CompletableFuture<?> promise = promisesByCorrelationId.remove(correlationId);
        if (promise != null)
        {
            promise.complete(null);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleBoundResponse(
        DirectBuffer buffer,
        int index,
        int length)
    {
        boundRO.wrap(buffer, index, length);
        long correlationId = boundRO.correlationId();

        CompletableFuture<Long> promise = (CompletableFuture<Long>)promisesByCorrelationId.remove(correlationId);
        if (promise != null)
        {
            promise.complete(boundRO.referenceId());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleUnboundResponse(
        DirectBuffer buffer,
        int index,
        int length)
    {
        unboundRO.wrap(buffer, index, length);
        long correlationId = unboundRO.correlationId();

        CompletableFuture<Void> promise = (CompletableFuture<Void>)promisesByCorrelationId.remove(correlationId);
        if (promise != null)
        {
            promise.complete(null);
        }
    }
}
