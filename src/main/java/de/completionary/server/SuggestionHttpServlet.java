package de.completionary.server;

import de.completionary.proxy.thrift.services.suggestion.SuggestionService;
import de.completionary.server.thrift.SuggestionHandler;
import de.completionary.server.thrift.TServlet;

public class SuggestionHttpServlet extends TServlet {

    private static final long serialVersionUID = 4250997284816006328L;

    public SuggestionHttpServlet() {
        super(
                new SuggestionService.Processor<SuggestionHandler.SuggestionHandlerSync>(
                        new SuggestionHandler.SuggestionHandlerSync()));
    }
}
