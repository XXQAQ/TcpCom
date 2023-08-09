package com.xq.tcpcom;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TcpChannel {

    private final ScheduledExecutorService sendExecutorService = new ScheduledThreadPoolExecutor(1);

    private final ExecutorService receiveExecutorService = new ThreadPoolExecutor(1, 1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());

    private final Socket socket;

    private final OnCloseListener onCloseListener;

    public TcpChannel(Socket socket, OnCloseListener onCloseListener) {
        this.socket = socket;
        this.onCloseListener = onCloseListener;
    }

    private OnDisconnectedListener onDisconnectedListener;

    public void setOnDisconnectedListener(OnDisconnectedListener onDisconnectedListener) {
        this.onDisconnectedListener = onDisconnectedListener;
        if (socket.isClosed()){
            this.onDisconnectedListener.onDisconnected();
        }
    }

    public void close(){
        if (!socket.isClosed()){
            //写线程
            sendExecutorService.shutdown();
            //读线程
            receiveExecutorService.shutdownNow();
            //
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            //
            onCloseListener.onClose();
        }
    }

    public void startReceive(final int readBufferSize, final int readTimeout, final OnReceiveListener onReceiveListener){
        receiveExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    socket.setSoTimeout(readTimeout);
                    InputStream inputStream = socket.getInputStream();
                    byte[] buffer = new byte[readBufferSize];
                    while (true){
                        int length = inputStream.read(buffer);
                        if (length > 0){
                            onReceiveListener.onReceive(buffer,0,length);
                        }
                        else {
                            throw new SocketException("readLength:" + length);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if (!socket.isClosed()){
                        //写线程
                        sendExecutorService.shutdown();
                        //读线程
                        receiveExecutorService.shutdownNow();
                        //
                        try {
                            socket.close();
                        } catch (IOException ex) {
                            e.printStackTrace();
                        }
                        //
                        onCloseListener.onClose();
                        //
                        if (onDisconnectedListener != null){
                            onDisconnectedListener.onDisconnected();
                        }
                    }
                }
            }
        });
    }

    public void triggerHeart(int between, final byte[] bytes){
        sendExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    socket.getOutputStream().write(bytes);
                    socket.getOutputStream().flush();
                } catch (Exception e){
//                    e.printStackTrace();
                }
            }
        },0,between,TimeUnit.MILLISECONDS);
    }

    public void send(final byte[] bytes, final OnActionCallback callback){
        try {
            sendExecutorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        socket.getOutputStream().write(bytes);
                        socket.getOutputStream().flush();
                        callback.onSuccess();
                    } catch (Exception e){
                        callback.onError(e.getMessage(),"");
                    }
                }
            });
        } catch (RejectedExecutionException e){
            callback.onError("socket is disconnected","");
        }
    }

    interface OnCloseListener{
        void onClose();
    }

    public interface OnDisconnectedListener{
        void onDisconnected();
    }

    public interface OnReceiveListener{
        void onReceive(byte[] bytes, int offset, int length);
    }

    public interface OnActionCallback {
        void onSuccess();
        void onError(String info,String code);
    }

}
