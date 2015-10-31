/*
 * Copyright 2015, Kaazing Corporation. All rights reserved.
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

package org.kaazing.nuklei.tcp.internal.writer;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

import org.kaazing.nuklei.Nukleus;
import org.kaazing.nuklei.tcp.internal.Context;

import uk.co.real_logic.agrona.concurrent.OneToOneConcurrentArrayQueue;
import uk.co.real_logic.agrona.concurrent.ringbuffer.RingBuffer;
import uk.co.real_logic.agrona.nio.TransportPoller;

public final class Writer extends TransportPoller implements Nukleus, Consumer<WriterCommand>
{
    private final OneToOneConcurrentArrayQueue<WriterCommand> commandQueue;

    public Writer(Context context)
    {
        this.commandQueue = context.writerCommandQueue();
    }

    @Override
    public int process() throws Exception
    {
        int weight = 0;

        selector.selectNow();
        weight += selectedKeySet.forEach(this::processWrite);
        weight += commandQueue.drain(this);

        return weight;
    }

    @Override
    public String name()
    {
        return "writer";
    }

    @Override
    public void accept(WriterCommand command)
    {
        command.execute(this);
    }

    public void doRegister(
        long bindingRef,
        long connectionId,
        SocketChannel channel,
        RingBuffer readBuffer)
    {
        // TODO Auto-generated method stub
    }

    private int processWrite(SelectionKey selectionKey)
    {
        return 1;
    }
}
