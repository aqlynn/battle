package com.linxk.battle;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import com.linxk.battle.BattleProtos.GlobalMessage;
import com.linxk.battle.util.CRC8;

public class BattlePacketEncoder extends MessageToByteEncoder<GlobalMessage> {
	@Override
	protected void encode(ChannelHandlerContext ctx, GlobalMessage msg,
			ByteBuf out) throws Exception {
		byte[] b = msg.toByteArray();

		out.writeInt(b.length); // length
        out.writeBytes(b); // content
        out.writeByte(CRC8.calcCrc8(b));
        
        if (System.getProperty("DEBUG", "false").equals("true")) System.out.println("Server: sent a packet: \n"+msg.toString());
	}
}
