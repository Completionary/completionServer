package de.completionary.server;

import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.server.TNonblockingServer;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TNonblockingServerTransport;

import de.completionary.proxy.helper.ProxyOptions;
import de.completionary.proxy.thrift.services.suggestion.SuggestionService;
import de.completionary.server.thrift.SuggestionHandler;

public class SuggestionServer extends Thread {

    @Override
    public void run() {
        try {

            TNonblockingServerTransport trans =
                    new TNonblockingServerSocket(
                            ProxyOptions.SUGGESTION_SERVER_PORT);
            TNonblockingServer.Args args = new TNonblockingServer.Args(trans);
            args.transportFactory(new TFramedTransport.Factory());
            args.protocolFactory(new TCompactProtocol.Factory());
            args.processor(new SuggestionService.AsyncProcessor<SuggestionService.AsyncIface>(
                    new SuggestionHandler()));
            TServer server = new TNonblockingServer(args);
            server.serve();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
