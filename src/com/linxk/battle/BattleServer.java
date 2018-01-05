package com.linxk.battle;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public final class BattleServer {

    static final int PORT = Integer.parseInt(System.getProperty("port", "6868"));

    public static void main(String[] args) throws Exception {  	
        EventLoopGroup bossGroup1 = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup1 = new NioEventLoopGroup();
        try {
            ServerBootstrap b1 = new ServerBootstrap();
            b1.group(bossGroup1, workerGroup1)
             .channel(NioServerSocketChannel.class)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast("decoder", new BattlePacketDecoder());
                    ch.pipeline().addLast("encoder", new BattlePacketEncoder());
                    ch.pipeline().addLast("handler", new BattleServerHandler());
                }
            });
            
            ChannelFuture f1 = b1.bind(PORT).sync();
            f1.channel().closeFuture().sync();
        } finally {
            bossGroup1.shutdownGracefully();
            workerGroup1.shutdownGracefully();
        }
    }
}
