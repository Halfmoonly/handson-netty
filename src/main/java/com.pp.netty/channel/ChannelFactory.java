package com.pp.netty.channel;



public interface ChannelFactory<T extends Channel> {


    T newChannel();
}
