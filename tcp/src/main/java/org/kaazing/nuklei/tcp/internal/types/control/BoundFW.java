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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY ERROR_TYPE_ID, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kaazing.nuklei.tcp.internal.types.control;

import org.kaazing.nuklei.tcp.internal.types.Flyweight;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public final class BoundFW extends Flyweight
{
    public static final int BOUND_TYPE_ID = 0x40000001;

    private static final int FIELD_OFFSET_CORRELATION_ID = 0;
    private static final int FIELD_SIZE_CORRELATION_ID = BitUtil.SIZE_OF_LONG;

    private static final int FIELD_OFFSET_BINDING_REF = FIELD_OFFSET_CORRELATION_ID + FIELD_SIZE_CORRELATION_ID;
    private static final int FIELD_SIZE_BINDING_REF = BitUtil.SIZE_OF_LONG;

    public BoundFW wrap(DirectBuffer buffer, int offset, int actingLimit)
    {
        super.wrap(buffer, offset);

        checkLimit(limit(), actingLimit);

        return this;
    }

    public int typeId()
    {
        return BOUND_TYPE_ID;
    }

    public long correlationId()
    {
        return buffer().getLong(offset() + FIELD_OFFSET_CORRELATION_ID);
    }

    public long bindingRef()
    {
        return buffer().getLong(offset() + FIELD_OFFSET_BINDING_REF);
    }

    public int limit()
    {
        return offset() + FIELD_OFFSET_BINDING_REF + FIELD_SIZE_BINDING_REF;
    }

    @Override
    public String toString()
    {
        return String.format("[correlationId=%d, bindingRef=%d]", correlationId(), bindingRef());
    }

    public static final class Builder extends Flyweight.Builder<BoundFW>
    {
        public Builder()
        {
            super(new BoundFW());
        }

        @Override
        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);

            return this;
        }

        public Builder correlationId(long correlationId)
        {
            buffer().putLong(offset() + FIELD_OFFSET_CORRELATION_ID, correlationId);
            return this;
        }

        public Builder bindingRef(long bindingRef)
        {
            buffer().putLong(offset() + FIELD_OFFSET_BINDING_REF, bindingRef);
            return this;
        }
    }
}
