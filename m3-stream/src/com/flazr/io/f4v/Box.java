/*
 * Flazr <http://flazr.com> Copyright (C) 2009  Peter Thomas.
 *
 * This file is part of Flazr.
 *
 * Flazr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Flazr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Flazr.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.flazr.io.f4v;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jboss.netty.buffer.ChannelBuffers;

import android.util.Log;

public class Box {
    
    private final BoxType type;
    private final long fileOffset;
    private List<Box> children;
    private Payload payload;

    public Box(final FileChannel in, final long endPos) throws Exception {        
        final ByteBuffer bb = ByteBuffer.allocate(8);
        in.read(bb);
        bb.flip();
        final int boxSize = bb.getInt();
        final byte[] typeBytes = new byte[4];
        bb.get(typeBytes);
        type = BoxType.parse(new String(typeBytes));        
        final long payloadSize;
        if (boxSize == 1) { // extended
            bb.clear();
            in.read(bb);
            bb.flip();
            final byte[] extBytes = new byte[8];
            bb.get(extBytes);
            final BigInteger bigLen = new BigInteger(1, extBytes);
            payloadSize = bigLen.intValue() - 16;
        } else if (boxSize == 0) { // decided by parent bound
            payloadSize = endPos - in.position();
        } else {
            payloadSize = boxSize - 8;
        }
        fileOffset = in.position();
        final long childEndPos = fileOffset + payloadSize;
        Log.d(this.getClass().getName(), ">> type: {"+type+"}, payloadSize: {"+payloadSize+"}");
        final BoxType[] childBoxes = type.getChildren();
        if(childBoxes == null) {            
            if(type == BoxType.MDAT) {
                Log.d(this.getClass().getName(), "skipping MDAT");
                in.position(childEndPos);
                return;
            }
            final ByteBuffer buf = ByteBuffer.allocate((int) payloadSize);
            in.read(buf);
            buf.flip();
            payload = type.read(ChannelBuffers.wrappedBuffer(buf));
            Log.d(this.getClass().getName(), ">> type: {"+type+"}, payloadSize: {"+payloadSize+"}");
            return;
        }        
        while(in.position() < childEndPos) {
            if(children == null) {
                children = new ArrayList<Box>();
            }
            children.add(new Box(in, childEndPos));
        }
        Log.d(this.getClass().getName(), "<< {"+type+"} children: {"+children+"}");
    }

    public BoxType getType() {
        return type;
    }

    public long getFileOffset() {
        return fileOffset;
    }

    public List<Box> getChildren() {
        return children;
    }

    public Payload getPayload() {
        return payload;
    }

    public static void recurse(final Box box, final List<Box> collect, final int level) {
        final char[] chars = new char[level * 2];
        Arrays.fill(chars, ' ');
        Log.d(Box.class.getName(), String.valueOf(chars) + "recursing " + box.type + ", payload" + box.payload);
        if(collect != null && box.getPayload() != null) {
            collect.add(box);
        }
        if(box.getChildren() != null) {
            for(final Box child : box.getChildren()) {
                recurse(child, collect, level + 1);
            }
        }        
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(type);
        sb.append(" fileOffset: ").append(fileOffset);
        if(children != null) {
            sb.append(" children: [");
            for(Box box : children) {
                sb.append(box.getType()).append(' ');
            }
            sb.append(']');
        }
        sb.append(" payload: ").append(payload);
        sb.append(']');
        return sb.toString();
    }

}
