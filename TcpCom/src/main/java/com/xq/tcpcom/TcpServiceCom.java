package com.xq.tcpcom;

import android.util.Pair;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class TcpServiceCom {

    private final Map<Integer,Record> recordMap = new HashMap<>();

    public int bind(final OnBindCallback onBindCallback, final OnClientConnectListener onClientConnectListener){

        final int businessId = nextID();

        final Record record = new Record();

        synchronized (recordMap){
            recordMap.put(businessId,record);
        }

        record.future = Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {

                try {

                    record.serverSocket = new ServerSocket();

                    record.serverSocket.bind(new InetSocketAddress((InetAddress) null,0));

                    onBindCallback.onSuccess(record.serverSocket.getLocalPort());

                    while (true){

                        final Socket socket = record.serverSocket.accept();

                        final String client = socket.getInetAddress().getHostAddress()+":"+socket.getPort();

                        if(recordMap.containsKey(businessId)){

                            TcpChannel tcpChannel = new TcpChannel(socket, new TcpChannel.OnCloseListener() {
                                @Override
                                public void onClose() {
                                    containWithRemove();
                                }
                            });

                            record.tcpChannelMap.put(client,tcpChannel);

                            onClientConnectListener.OnClientConnect(client, tcpChannel);

                        } else {
                            try {
                                socket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if (containWithRemove()){
                        onBindCallback.onError(e.getMessage(),"");
                    }
                }
            }

            private boolean containWithRemove(){
                if (recordMap.containsKey(businessId)){
                    synchronized (recordMap){
                        if (recordMap.containsKey(businessId)){
                            recordMap.remove(businessId);
                            return true;
                        }
                    }
                }
                return false;
            }

        });

        return businessId;
    }

    public boolean unbind(int businessId){
        Pair<Boolean, Record> pair = containWithRemoveKey(recordMap, businessId);
        if (pair.first){
            Record record = pair.second;
            //
            record.future.cancel(false);
            //
            if (!record.tcpChannelMap.isEmpty()){
                for (TcpChannel tcpChannel : record.tcpChannelMap.values()){
                    tcpChannel.close();
                }
            }
            //
            if (record.serverSocket != null){
                try {
                    record.serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return pair.first;
    }

    private <K,V> Pair<Boolean, V> containWithRemoveKey(Map<K,V> map, K k){
        if (map.containsKey(k)){
            synchronized (recordMap){
                if (map.containsKey(k)){
                    return new Pair<>(true,map.remove(k));
                }
            }
        }
        return new Pair<>(false,null);
    }

    public void unbindAll(){
        for (int businessId : new ArrayList<>(recordMap.keySet())){
            unbind(businessId);
        }
    }

    private final AtomicInteger ID = new AtomicInteger(0);
    private int nextID() {
        return ID.incrementAndGet();
    }

    private class Record{
        Future<?> future;
        ServerSocket serverSocket;
        Map<String,TcpChannel> tcpChannelMap = new HashMap<>();
    }

    public interface OnClientConnectListener{
        void OnClientConnect(String client, TcpChannel channel);
    }

    public interface OnBindCallback{
        void onSuccess(int port);
        void onError(String info,String code);
    }

}
