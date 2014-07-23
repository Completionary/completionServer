package de.completionary.server.thrift;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.transport.TTransportFactory;

/**
 * <p>
 * A servlet for exposing Thrift services over HTTP. To use, create a subclass
 * that supplies a {@link TProcessor}. For example,
 * </p>
 * 
 * <pre>
 * 
 * 
 * public class CalculatorTServlet extends TServlet {
 * 
 *     public CalculatorTServlet() {
 *         super(new Calculator.Processor(new CalculatorHandler()));
 *     }
 * }
 * </pre>
 * 
 * <p>
 * TServlet expects to be called in one of three ways:
 * 
 * 1) Via HTTP POST with the request body being the plain binary protocol data.
 * 
 * 2) Via HTTP POST with the request body being url-safe base 64 (the client
 * should signal this with ?base64=true)
 * 
 * 3) In the JSONP style via HTTP GET with a "body" parameter containing
 * url-safe base 64, and a "callback" parameter with the function name to be
 * called. That is,
 * "some/url?body=<URL SAFE BASE64>&callback=<SOME JS CALLBACK>". (See
 * http://en.wikipedia.org/wiki/JSON#JSONP for more info on JSONP)
 * </p>
 * 
 * <p>
 * This code is based heavily on
 * {@link com.facebook.thrift.server.TSimpleServer}.
 * </p>
 * 
 * @author Tom White
 * @author Fred Potter
 */
public class TServlet extends HttpServlet {

    private static final long serialVersionUID = 9110289251730217130L;

    protected TProcessor processor_ = null;

    protected TTransportFactory inputTransportFactory_ =
            new TTransportFactory();

    protected TTransportFactory outputTransportFactory_ =
            new TTransportFactory();

    protected TProtocolFactory inputProtocolFactory_ =
            new TJSONProtocol.Factory();

    protected TProtocolFactory outputProtocolFactory_ =
            new TJSONProtocol.Factory();

    private Base64 base64;

    public TServlet(
            TProcessor processor) {
        processor_ = processor;

        // We want to use URL-Safe mode, with no chunking.
        base64 = new Base64(0, null, true);
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

        // If our client sent any special header, we'd need to enumerate them here.
        // response.setHeader("Access-Control-Allow-Headers", "header1, header2, ...");

        response.setHeader("Allow", "GET, POST, OPTIONS");
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        // If it's a GET, we'll assume the caller is trying to use the JSONP method.

        InputStream in;
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Our input comes base64 encoded from the 'body' get parameter
        in =
                new ByteArrayInputStream(base64.decode(request
                        .getParameter("body")));

        processRequest(in, out);

        String jsCallback = request.getParameter("callback");
        String responseAsBase64 = base64.encodeToString(out.toByteArray());

        response.setContentType("application/javascript");

        PrintStream ps = new PrintStream(response.getOutputStream());
        ps.print(jsCallback + "(\"" + responseAsBase64 + "\");");
        ps.flush();
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {

        response.setHeader("Access-Control-Allow-Origin", "*");

        boolean useBase64 =
                request.getParameter("base64") != null
                        && request.getParameter("base64").equalsIgnoreCase(
                                "true");

        InputStream in;
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        if (useBase64) {
            byte[] postBody = readContentsOfStream(request.getInputStream());

            postBody = base64.decode(postBody);
            in = new ByteArrayInputStream(postBody);
        } else {
            in = request.getInputStream();
        }

        processRequest(in, out);

        response.setContentType("application/x-thrift");

        byte[] responseBytes;

        if (useBase64) {
            String responseAsBase64 = base64.encodeToString(out.toByteArray());

            response.setContentType("text/plain");
            responseBytes = responseAsBase64.getBytes();
        } else {
            response.setContentType("application/octet-stream");
            responseBytes = out.toByteArray();
        }

        response.setContentLength(responseBytes.length);

        OutputStream os = response.getOutputStream();
        os.write(responseBytes);
        os.close();
    }

    private byte[] readContentsOfStream(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        byte[] buffer = new byte[4096];
        while (true) {
            int bytesRead = in.read(buffer, 0, buffer.length);

            if (bytesRead == -1) {
                // end of input
                break;
            } else {
                baos.write(buffer, 0, bytesRead);
            }
        }

        return baos.toByteArray();
    }

    private void processRequest(InputStream in, OutputStream out) {
        TTransport client = new TIOStreamTransport(in, out);
        TProcessor processor = null;
        TTransport inputTransport = null;
        TTransport outputTransport = null;
        TProtocol inputProtocol = null;
        TProtocol outputProtocol = null;

        try {
            processor = processor_;
            inputTransport = inputTransportFactory_.getTransport(client);
            outputTransport = outputTransportFactory_.getTransport(client);
            inputProtocol = inputProtocolFactory_.getProtocol(inputTransport);
            outputProtocol =
                    outputProtocolFactory_.getProtocol(outputTransport);

            while (processor.process(inputProtocol, outputProtocol)) {
            }
        } catch (TTransportException ttx) {
            // Client died, just move on
        } catch (TException tx) {
            tx.printStackTrace();
        } catch (Exception x) {
            x.printStackTrace();
        }

        if (inputTransport != null) {
            inputTransport.close();
        }

        if (outputTransport != null) {
            outputTransport.close();
        }
    }
}
