package de.completionary.server.thrift;

import de.completionary.proxy.thrift.impl.SuggestionHandler;
import de.completionary.proxy.thrift.services.SuggestionService;
import de.completionary.server.TServlet;

public class SuggestionServer extends TServlet {

    private static final long serialVersionUID = 4250997284816006328L;

    public SuggestionServer() {
        super(
                new SuggestionService.Processor<SuggestionHandler.SuggestionHandlerSync>(
                        new SuggestionHandler.SuggestionHandlerSync()));
    }
}
