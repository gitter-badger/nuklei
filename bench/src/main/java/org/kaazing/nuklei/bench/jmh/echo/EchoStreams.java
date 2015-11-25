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
package org.kaazing.nuklei.bench.jmh.echo;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.nativeOrder;

import org.kaazing.nuklei.echo.internal.Context;
import org.kaazing.nuklei.echo.internal.layouts.StreamsLayout;
import org.kaazing.nuklei.echo.internal.types.stream.BeginFW;
import org.kaazing.nuklei.echo.internal.types.stream.DataFW;
import org.kaazing.nuklei.echo.internal.types.stream.EndFW;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.AtomicBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.agrona.concurrent.ringbuffer.RingBuffer;

public final class EchoStreams
{
    private static final int MAX_SEND_LENGTH = 1024; // TODO: Configuration and Context

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();

    private final StreamsLayout streams;
    private final RingBuffer streamsInput;
    private final RingBuffer streamsOutput;
    private final AtomicBuffer atomicBuffer;

    EchoStreams(
        Context context,
        String streamFilename)
    {
        this.streams = new StreamsLayout.Builder().streamsCapacity(context.streamsCapacity())
                                                  .streamsDirectory(context.streamsDirectory())
                                                  .streamsFilename(streamFilename)
                                                  .readonly(true)
                                                  .build();
        this.streamsInput = this.streams.inputBuffer();
        this.streamsOutput = this.streams.outputBuffer();
        this.atomicBuffer = new UnsafeBuffer(allocateDirect(MAX_SEND_LENGTH).order(nativeOrder()));
    }

    public void begin(
        long streamId,
        long referenceId)
    {
        BeginFW beginRO = beginRW.wrap(atomicBuffer, 0, atomicBuffer.capacity())
                                 .streamId(streamId)
                                 .referenceId(referenceId)
                                 .build();

        if (!streamsInput.write(beginRO.typeId(), beginRO.buffer(), beginRO.offset(), beginRO.remaining()))
        {
            throw new IllegalStateException("unable to write to streams");
        }
    }

    public void data(
        long streamId,
        DirectBuffer buffer,
        int offset,
        int length)
    {
        DataFW dataRO = dataRW.wrap(atomicBuffer, 0, atomicBuffer.capacity())
                              .streamId(streamId)
                              .payload(buffer, offset, length)
                              .build();

        if (!streamsInput.write(dataRO.typeId(), dataRO.buffer(), dataRO.offset(), dataRO.remaining()))
        {
            throw new IllegalStateException("unable to write to streams");
        }
    }

    public void end(
        long streamId)
    {
        EndFW endRO = endRW.wrap(atomicBuffer, 0, atomicBuffer.capacity())
                           .streamId(streamId)
                           .build();

        if (!streamsInput.write(endRO.typeId(), endRO.buffer(), endRO.offset(), endRO.remaining()))
        {
            throw new IllegalStateException("unable to write to streams");
        }
    }

    public void drain()
    {
        while (streamsOutput.read((msgTypeId, buffer, offset, length) -> {}) != 0)
        {
            // intentional
        }
    }
}
