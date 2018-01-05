package com.linxk.battle;

import java.util.List;

import com.linxk.battle.BattleProtos.GlobalMessage;
import com.linxk.battle.util.CRC8;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

public class BattlePacketDecoder extends ByteToMessageDecoder {

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in,
			List<Object> out) throws Exception {
		if (in.readableBytes() < 4) {
			if (System.getProperty("DEBUG", "false").equals("true")) System.out.println("Server: packet header length not reached with "+in.readableBytes()+" , wait");
            return;
        }
        in.markReaderIndex();                  //我们标记一下当前的readIndex的位置
        int dataLength = in.readInt();  // 数据包长
        if (dataLength < 0) { // 我们读到的消息体长度小于0，这是不应该出现的情况，这里出现这情况，关闭连接。
            System.err.println("Protocol error, connection closed!");
        	ctx.channel().close();
        	return;
        }
        //加上校验位 
        if (in.readableBytes() < dataLength+1) { //读到的消息体长度如果小于我们传送过来的消息长度，则resetReaderIndex. 这个配合markReaderIndex使用的。把readIndex重置到mark的地方
        	if (System.getProperty("DEBUG", "false").equals("true")) System.out.println("Server: content length not reached with "+in.readableBytes()+", wanted "+(dataLength+1)+", wait");
        	in.resetReaderIndex();
            return;
        }
 
        byte[] content = new byte[dataLength];  //  嗯，这时候，我们读到的长度，满足我们的要求了，把传送过来的数据，取出来吧~~
        in.readBytes(content);
        byte crc = in.readByte();
        if (crc != CRC8.calcCrc8(content)) {
        	System.out.println("CRC error, packet ignored!");
        }
        GlobalMessage recvedmsg = GlobalMessage.parseFrom(content);  //将byte数据转化为我们需要的对象
        if (System.getProperty("DEBUG", "false").equals("true")) System.out.println("Server: got a packet: \n"+recvedmsg.toString());
        out.add(recvedmsg);
	}

}
