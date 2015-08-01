package com.alibaba.middleware.race.rpc.api.impl;

import com.alibaba.middleware.race.rpc.aop.ConsumerHook;
import com.alibaba.middleware.race.rpc.api.RpcConsumer;
import com.alibaba.middleware.race.rpc.async.ResponseCallbackListener;
import com.alibaba.middleware.race.rpc.context.RpcContext;
import com.alibaba.middleware.race.rpc.model.RpcRequest;
import com.alibaba.middleware.race.rpc.protocol.DefaultDecoder;
import com.alibaba.middleware.race.rpc.protocol.DefaultEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.internal.chmv8.ConcurrentHashMapV8;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Created by marsares on 15/7/23.
 */
public class RpcConsumerImpl extends RpcConsumer {
    private DefaultClientHandler handler;
    private volatile Channel channel;
    private ConsumerHook hook;
    public RpcConsumerImpl() {
        init();
    }

    @Override
    public void init() {
        String host = "127.0.0.1";
        int port = 8888;
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try{
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            //DefaultClientInitializer clientInitializer=new DefaultClientInitializer();
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) {
                    handler = new DefaultClientHandler();
                    ch.pipeline().addLast(new DefaultDecoder(false));
                    ch.pipeline().addLast(new DefaultEncoder());
                    ch.pipeline().addLast(handler);
                }
            });
            channel=b.connect(host,port).awaitUninterruptibly().channel();
            handler.setChannel(channel);
            RpcContext.setHandler(handler);
            /*Object[]args={};
            RpcContext rpcCtx = createRequest(args, "com.alibaba.middleware.race.rpc.demo.service.RaceTestServiceImpl", "getString");
            //RpcFuture rpcFuture=doRpc(rpcCtx);
            System.out.println("waiting for future");
            channel.writeAndFlush(rpcCtx);*/
            //ChannelFuture f=b.connect(host, port);
            //f.awaitUninterruptibly();//.channel();
            //f.channel().closeFuture().sync();
        }catch(Exception e){
            e.printStackTrace();
        }
        /*finally{
            workerGroup.shutdownGracefully();
        }*/

    }

    @Override
    public RpcConsumer hook(ConsumerHook hook){
        this.hook=hook;
        return this;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object[]args2={};
        if(args==null){
            args=args2;
        }
        RpcContext rpcCtx = createRequest(args, getInterfaceClazz().getName() + "Impl", method.getName(), 0, 1);
        hook.before(rpcCtx.getRequest());
        RpcFuture rpcFuture=handler.doRpc(rpcCtx);
        hook.after(rpcCtx.getRequest());
        if(rpcFuture==null)return null;
        Object result=rpcFuture.get(3000,TimeUnit.MILLISECONDS);
        if(result instanceof Exception)throw (Exception)result;
        return result;
    }

    @Override
    public <T extends ResponseCallbackListener> void asynCall(String methodName, T callbackListener) {
        if(callbackListener==null){
            Object[]args={};
            RpcContext rpcCtx = createRequest(args, getInterfaceClazz().getName() + "Impl", methodName, 1,1);
            handler.doAsynRpc(rpcCtx);
        }else{
            handler.setListener(callbackListener);
            Object[]args={};
            RpcContext rpcCtx = createRequest(args, getInterfaceClazz().getName() + "Impl", methodName, 2,1);
            handler.doAsynRpcWithListener(rpcCtx);
        }
    }

    @Override
    public void cancelAsyn(String methodName) {
        handler.cancelAsyn(methodName);
    }

    public RpcContext createRequest(Object[] args, String ObjName, String FuncName,int callType,int FuncType) {
        RpcContext ctx = new RpcContext();
        RpcRequest request = new RpcRequest();
        request.setSeqNum(handler.getNextSequenceNumber());
        request.setArgs(args);
        request.setFuncName(FuncName);
        request.setObjName(ObjName);
        request.setCallType(callType);
        request.setFuncType(FuncType);
        ctx.setRequest(request);
        return ctx;
    }


}
