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
package org.kaazing.nuklei.tcp.internal.reader;

import java.io.File;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.kaazing.nuklei.Nukleus;
import org.kaazing.nuklei.Reaktive;
import org.kaazing.nuklei.tcp.internal.Context;
import org.kaazing.nuklei.tcp.internal.conductor.Conductor;
import org.kaazing.nuklei.tcp.internal.connector.Connector;
import org.kaazing.nuklei.tcp.internal.layouts.StreamsLayout;

import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.collections.ArrayUtil;
import uk.co.real_logic.agrona.nio.TransportPoller;

@Reaktive
public final class Reader extends TransportPoller implements Nukleus
{
    private final Function<String, File> streamsFile;
    private final int streamsCapacity;
    private final Map<String, StreamsLayout> layoutsByHandler;

    private Conductor conductor;
    private Connector connector;
    private ReaderState[] readerStates;

    public Reader(Context context)
    {
        this.readerStates = new ReaderState[0];
        this.streamsFile = context.captureStreamsFile();
        this.streamsCapacity = context.streamsBufferCapacity();
        this.layoutsByHandler = new HashMap<>();
    }

    public void setConductor(Conductor conductor)
    {
        this.conductor = conductor;
    }

    public void setConnector(Connector connector)
    {
        this.connector = connector;
    }

    @Override
    public int process()
    {
        int weight = 0;

        try
        {
            selector.selectNow();
            weight += selectedKeySet.forEach(this::processWrite);

            for (int i=0; i < readerStates.length; i++)
            {
                weight += readerStates[i].process();
            }
        }
        catch (Exception ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return weight;
    }

    @Override
    public String name()
    {
        return "writer";
    }

    @Override
    public void close()
    {
        for (int i=0; i < readerStates.length; i++)
        {
            try
            {
                readerStates[i].close();
            }
            catch (final Exception ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }

        layoutsByHandler.values().forEach((layout) ->
        {
            try
            {
                layout.close();
            }
            catch (final Exception ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        });

        super.close();
    }

    public void doCapture(
        long correlationId,
        String source)
    {
        StreamsLayout layout = layoutsByHandler.get(source);
        if (layout != null)
        {
            conductor.onErrorResponse(correlationId);
        }
        else
        {
            try
            {
                StreamsLayout newLayout = new StreamsLayout.Builder().streamsFile(streamsFile.apply(source))
                                                                     .streamsCapacity(streamsCapacity)
                                                                     .createFile(true)
                                                                     .build();

                layoutsByHandler.put(source, newLayout);

                ReaderState newCaptureState = new ReaderState(connector, source, newLayout.buffer());

                readerStates = ArrayUtil.add(readerStates, newCaptureState);

                conductor.onCapturedResponse(correlationId);
            }
            catch (Exception ex)
            {
                conductor.onErrorResponse(correlationId);
                LangUtil.rethrowUnchecked(ex);
            }
        }
    }

    public void doUncapture(
        long correlationId,
        String handler)
    {
        StreamsLayout oldLayout = layoutsByHandler.remove(handler);
        if (oldLayout == null)
        {
            conductor.onErrorResponse(correlationId);
        }
        else
        {
            try
            {
                ReaderState[] readerStates = this.readerStates;
                for (int i=0; i < readerStates.length; i++)
                {
                    if (handler.equals(readerStates[i].source()))
                    {
                        ReaderState oldCaptureState = readerStates[i];
                        oldCaptureState.close();

                        this.readerStates = ArrayUtil.remove(this.readerStates, oldCaptureState);
                        break;
                    }
                }

                oldLayout.close();
                conductor.onUncapturedResponse(correlationId);
            }
            catch (Exception ex)
            {
                conductor.onErrorResponse(correlationId);
                LangUtil.rethrowUnchecked(ex);
            }
        }
    }

    public void doRegister(
        String handler,
        long handlerRef,
        long clientStreamId,
        long serverStreamId,
        SocketChannel channel)
    {
        ReaderState[] readerStates = this.readerStates;
        for (int i=0; i < readerStates.length; i++)
        {
            if (handler.equals(readerStates[i].source()))
            {
                ReaderState readerState = readerStates[i];
                readerState.doRegister(handler, handlerRef, clientStreamId, serverStreamId, channel);
                break;
            }
        }
    }

    private int processWrite(SelectionKey selectionKey)
    {
        // fulfill partial writes (flow control?)
        return 1;
    }
}
