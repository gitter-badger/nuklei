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
package org.kaazing.nuklei.echo.internal.readable.stream;

import java.util.function.Consumer;

import org.kaazing.nuklei.echo.internal.types.stream.DataFW;
import org.kaazing.nuklei.echo.internal.types.stream.EndFW;
import org.kaazing.nuklei.echo.internal.types.stream.Types;

import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.AtomicBuffer;
import uk.co.real_logic.agrona.concurrent.MessageHandler;
import uk.co.real_logic.agrona.concurrent.ringbuffer.RingBuffer;

public final class ReplyStreamPool
{
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();

    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();

    private final AtomicBuffer atomicBuffer;

    public ReplyStreamPool(
        int capacity,
        AtomicBuffer atomicBuffer)
    {
        this.atomicBuffer = atomicBuffer;
    }

    public MessageHandler acquire(
        long sourceInitialStreamId,
        RingBuffer sourceRoute,
        Consumer<MessageHandler> released)
    {
        return new ReplyReflectingStream(released, sourceInitialStreamId, sourceRoute);
    }

    public final class ReplyReflectingStream implements MessageHandler
    {
        private final Consumer<MessageHandler> cleanup;
        private final long initialStreamId;
        private final RingBuffer routeBuffer;

        public ReplyReflectingStream(
            Consumer<MessageHandler> cleanup,
            long initialStreamId,
            RingBuffer routeBuffer)
        {
            this.cleanup = cleanup;
            this.initialStreamId = initialStreamId;
            this.routeBuffer = routeBuffer;
        }

        @Override
        public void onMessage(
            int msgTypeId,
            MutableDirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case Types.TYPE_ID_DATA:
                dataRO.wrap(buffer, index, index + length);

                // reflect data with updated stream id
                atomicBuffer.putBytes(0, buffer, index, length);
                DataFW data = dataRW.wrap(atomicBuffer, 0, atomicBuffer.capacity())
                        .streamId(initialStreamId)
                        .payloadLength(dataRO.payloadLength())
                        .build();

                if (!routeBuffer.write(data.typeId(), data.buffer(), data.offset(), data.length()))
                {
                    throw new IllegalStateException("could not write to ring buffer");
                }
                break;

            case Types.TYPE_ID_END:
                endRO.wrap(buffer, index, index + length);

                // reflect end with updated stream id
                atomicBuffer.putBytes(0, buffer, index, length);
                EndFW end = endRW.wrap(atomicBuffer, 0, atomicBuffer.capacity())
                                 .streamId(initialStreamId)
                                 .build();

                if (!routeBuffer.write(end.typeId(), end.buffer(), end.offset(), end.length()))
                {
                    throw new IllegalStateException("could not write to ring buffer");
                }

                // release
                cleanup.accept(this);

                break;
            }
        }
    }

}
