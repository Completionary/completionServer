package de.completionary.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TTransport;

/**
 * Servlet implementation class ThriftServer with a proper CORS implementation
 * 
 * <p>
 * This code is based on {@link gist.github.com/pieceable/430784}.
 * </p>
 * 
 * @author Jonas Kunze <kunze.jonas@gmail.com>
 */
public class TServlet extends HttpServlet {

    private static final long serialVersionUID = -9040416060293389629L;

    private final TProcessor processor;

    private final TProtocolFactory inProtocolFactory;

    private final TProtocolFactory outProtocolFactory;

    private final Collection<Map.Entry<String, String>> customHeaders;

    /**
     * @see HttpServlet#HttpServlet()
     */
    public TServlet(
            TProcessor processor,
            TProtocolFactory inProtocolFactory,
            TProtocolFactory outProtocolFactory) {
        super();
        this.processor = processor;
        this.inProtocolFactory = inProtocolFactory;
        this.outProtocolFactory = outProtocolFactory;
        this.customHeaders = new ArrayList<Map.Entry<String, String>>();
    }

    /**
     * @see HttpServlet#HttpServlet()
     */
    public TServlet(
            TProcessor processor) {
        this(processor, new TJSONProtocol.Factory(),
                new TJSONProtocol.Factory());
    }

    @Override
    protected void doOptions(
            HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {

        // Support cross-site scripting requests that get "pre-flighted"
        // Mozilla has a good explanation here: https://developer.mozilla.org/En/HTTP_access_control
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST");
        response.setHeader("Access-Control-Max-Age", "1728000");
        response.setHeader("Access-Control-Allow-Headers",
                "Origin, X-Requested-With, Content-Type, Accept");
        // If our client sent any special header, we'd need to enumerate them here.
        // response.setHeader("Access-Control-Allow-Headers", "header1, header2, ...");

        response.setHeader("Allow", "GET, OPTIONS");
    }

    /**
     * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse
     *      response)
     */
    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {

        TTransport inTransport = null;
        TTransport outTransport = null;

        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers",
                "Origin, X-Requested-With, Content-Type, Accept");

        try {
            response.setContentType("application/x-thrift");

            if (null != this.customHeaders) {
                for (Map.Entry<String, String> header : this.customHeaders) {
                    response.addHeader(header.getKey(), header.getValue());
                }
            }
            InputStream in = request.getInputStream();
            OutputStream out = response.getOutputStream();

            TTransport transport = new TIOStreamTransport(in, out);
            inTransport = transport;
            outTransport = transport;

            TProtocol inProtocol = inProtocolFactory.getProtocol(inTransport);
            TProtocol outProtocol =
                    outProtocolFactory.getProtocol(outTransport);

            processor.process(inProtocol, outProtocol);
            out.flush();
        } catch (TException te) {
            throw new ServletException(te);
        }
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
     *      response)
     */
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    public void addCustomHeader(final String key, final String value) {
        this.customHeaders.add(new Map.Entry<String, String>() {

            public String getKey() {
                return key;
            }

            public String getValue() {
                return value;
            }

            public String setValue(String value) {
                return null;
            }
        });
    }

    public void setCustomHeaders(Collection<Map.Entry<String, String>> headers) {
        this.customHeaders.clear();
        this.customHeaders.addAll(headers);
    }
}
