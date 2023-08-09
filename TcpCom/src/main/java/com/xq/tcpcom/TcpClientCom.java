package com.xq.tcpcom;

import android.util.Pair;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class TcpClientCom {

    private final Map<Integer,Record> recordMap = new HashMap<>();

    public int connect(final String address, final int port, final int timeout, final OnConnectListener onConnectListener){

        final int businessId = nextID();

        final Record record = new Record();

        synchronized (recordMap){
            recordMap.put(businessId,record);
        }

        record.future = Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {

                record.socket = new Socket();

                try {
                    record.socket.connect(new InetSocketAddress(address,port),timeout);

                    if (recordMap.containsKey(businessId)){

                        record.tcpChannel = new TcpChannel(record.socket, new TcpChannel.OnCloseListener() {
                            @Override
                            public void onClose() {
                                containWithRemove();
                            }
                        });

                        onConnectListener.onSuccess(record.socket.getLocalPort(),record.tcpChannel);

                    } else {
                        try {
                            record.socket.close();
                        } catch (IOException e){
                            e.printStackTrace();
                        }
                    }

                } catch (IOException e) {
                    if (containWithRemove()){
                        onConnectListener.onError(e.getMessage(),"");
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

    public boolean disconnect(int businessId){
        Pair<Boolean, Record> pair = containWithRemoveKey(recordMap, businessId);
        if (pair.first){
            Record record = pair.second;
            //
            record.future.cancel(false);
            //
            if (record.tcpChannel != null){
                record.tcpChannel.close();
            } else {
                if (record.socket != null){
                    try {
                        record.socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
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

    public void disconnectAll(){
        for (int businessId : new ArrayList<>(recordMap.keySet())){
            disconnect(businessId);
        }
    }

    private final AtomicInteger ID = new AtomicInteger(0);
    private int nextID() {
        return ID.incrementAndGet();
    }

    private class Record{
        Future<?> future;
        Socket socket;
        TcpChannel tcpChannel;
    }

    public interface OnConnectListener{
        void onSuccess(int selfPort,TcpChannel tcpChannel);
        void onError(String info,String code);
    }

}
