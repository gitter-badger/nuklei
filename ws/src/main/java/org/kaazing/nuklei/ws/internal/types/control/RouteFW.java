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
package org.kaazing.nuklei.ws.internal.types.control;

import static java.lang.String.format;
import static org.kaazing.nuklei.ws.internal.types.control.Types.TYPE_ID_ROUTE_COMMAND;

import java.nio.charset.StandardCharsets;

import org.kaazing.nuklei.ws.internal.types.Flyweight;
import org.kaazing.nuklei.ws.internal.types.StringFW;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public final class RouteFW extends Flyweight
{
    private static final int FIELD_OFFSET_CORRELATION_ID = 0;
    private static final int FIELD_SIZE_CORRELATION_ID = BitUtil.SIZE_OF_LONG;

    private static final int FIELD_OFFSET_DESTINATION = FIELD_OFFSET_CORRELATION_ID + FIELD_SIZE_CORRELATION_ID;

    private final StringFW destinationRO = new StringFW();

    @Override
    public RouteFW wrap(DirectBuffer buffer, int offset, int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);

        this.destinationRO.wrap(buffer, offset + FIELD_OFFSET_DESTINATION, maxLimit);

        checkLimit(limit(), maxLimit);

        return this;
    }

    @Override
    public int limit()
    {
        return destination().limit();
    }

    public int typeId()
    {
        return TYPE_ID_ROUTE_COMMAND;
    }

    public long correlationId()
    {
        return buffer().getLong(offset() + FIELD_OFFSET_CORRELATION_ID);
    }

    public StringFW destination()
    {
        return destinationRO;
    }

    @Override
    public String toString()
    {
        return format("UNROUTE [correlationId=%d, destination=%s]", correlationId(), destination());
    }

    public static final class Builder extends Flyweight.Builder<RouteFW>
    {
        private final StringFW.Builder destinationRW = new StringFW.Builder();

        public Builder()
        {
            super(new RouteFW());
        }

        @Override
        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);

            this.destinationRW.wrap(buffer, offset + FIELD_OFFSET_DESTINATION, maxLimit);

            return this;
        }

        public Builder correlationId(long correlationId)
        {
            buffer().putLong(offset() + FIELD_OFFSET_CORRELATION_ID, correlationId);
            return this;
        }

        public Builder destination(String destination)
        {
            destinationRW.set(destination, StandardCharsets.UTF_8);
            return this;
        }
    }
}
